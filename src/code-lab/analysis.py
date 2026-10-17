"""Bounded symbolic loop counting, not execution, curve fitting or source eval.

Counts are polynomials derived by finite sums. Unsupported control flow fails
closed; a completed run must agree with every reported symbolic counter.
"""
from fractions import Fraction
from math import gcd
import re


class Unknown(Exception):
    pass


class Poly:
    def __init__(self, terms=None):
        self.terms = {k: Fraction(v) for k, v in (terms or {}).items() if v}
        if len(self.terms) > 80 or any(len(k) > 4 or v.numerator.bit_length() > 100 for k, v in self.terms.items()):
            raise Unknown('POLYNOMIAL_LIMIT')

    @staticmethod
    def number(n): return Poly({(): n})
    @staticmethod
    def var(name): return Poly({(name,): 1})
    def __add__(self, other):
        if not isinstance(other, Poly): other = Poly.number(other)
        terms = dict(self.terms)
        for k, v in other.terms.items(): terms[k] = terms.get(k, 0) + v
        return Poly(terms)
    def __neg__(self): return Poly({k: -v for k, v in self.terms.items()})
    def __sub__(self, other): return self + -other
    def __mul__(self, other):
        if not isinstance(other, Poly): other = Poly.number(other)
        terms = {}
        for a, x in self.terms.items():
            for b, y in other.terms.items():
                key = tuple(sorted(a + b)); terms[key] = terms.get(key, 0) + x * y
        return Poly(terms)
    def power(self, n):
        result = Poly.number(1)
        for _ in range(n): result = result * self
        return result
    def sub(self, name, value):
        result = Poly()
        for key, coefficient in self.terms.items():
            result = result + Poly({tuple(k for k in key if k != name): coefficient}) * value.power(key.count(name))
        return result
    def at(self, n):
        value = self.sub('n', Poly.number(n))
        if any(key for key in value.terms): raise Unknown('MULTIPLE_SIZES')
        return value.terms.get((), Fraction(0))
    def degree(self): return max((len(k) for k in self.terms), default=0)
    def display(self):
        if not self.terms: return '0'
        denominator = 1
        for c in self.terms.values(): denominator = denominator * c.denominator // gcd(denominator, c.denominator)
        # Familiar factorizations make triangular loops easier to read.
        if self.terms == {('n','n'): Fraction(1,2), ('n',): Fraction(-1,2)}: return 'n(n − 1)/2'
        if self.terms == {('n','n'): Fraction(1,2), ('n',): Fraction(1,2)}: return 'n(n + 1)/2'
        parts = []
        for key, c in sorted(self.terms.items(), key=lambda item: (-len(item[0]), -item[0].count('n'), item[0])):
            c *= denominator
            variables = '·'.join(v + ({1:'',2:'²',3:'³',4:'⁴'}[key.count(v)]) for v in sorted(set(key)))
            term = (str(abs(c.numerator)) if not variables or abs(c) != 1 else '') + variables
            parts.append((' − ' if c < 0 else ' + ') + term)
        value = ''.join(parts)
        value = '−' + value[3:] if value.startswith(' − ') else value[3:]
        return '(' + value + ')/' + str(denominator) if denominator != 1 else value


def prefix_sum(power, x):
    # Sum k**power for k=0,...,x-1; polynomial identity also supports shifts.
    if power == 0: return x
    if power == 1: return x * (x - 1) * Fraction(1, 2)
    if power == 2: return x * (x - 1) * (x * 2 - 1) * Fraction(1, 6)
    if power == 3: return (x * (x - 1)).power(2) * Fraction(1, 4)
    raise Unknown('POLYNOMIAL_LIMIT')


def summation(p, name, lower, upper):
    result = Poly()
    for key, c in p.terms.items():
        degree = key.count(name)
        rest = Poly({tuple(k for k in key if k != name): c})
        result = result + rest * (prefix_sum(degree, upper) - prefix_sum(degree, lower))
    return result


def text(n): return n.text.decode('utf-8') if n else ''
def field(n, *names):
    for name in names:
        value = n.child_by_field_name(name)
        if value is not None: return value
    return None
def children(n): return [c for c in n.named_children if c.type not in ('comment','line_comment','block_comment')]
def walk(n):
    yield n
    for c in children(n): yield from walk(c)
def operator(n): return text(field(n, 'operator')) or next((text(c) for c in n.children if not c.is_named), '')


class Analyzer:
    def __init__(self, engine, status):
        self.engine, self.status = engine, status
        self.root = field(engine.functions[engine.entry], 'body') if engine.entry in engine.functions else engine.root
        self.env, self.lengths, self.candidates, self.records, self.notes = {}, {}, [], [], []
        self.size, self.minimum, self.work = None, 0, 0
        self.loops = [n for n in walk(self.root) if n.type == 'for_statement']
        if len(self.loops) > 16: raise Unknown('ANALYSIS_LIMIT')
        if any(n.type == 'identifier' and len(text(n)) > 64 for n in walk(self.root)): raise Unknown('ANALYSIS_LIMIT')
        # Only one independent size is modeled. Other input bounds are rejected,
        # rather than silently claiming an n*m algorithm is linear in n.
        if engine.entry in engine.functions:
            fn = engine.functions[engine.entry]; decl = field(fn, 'declarator')
            parameters = field(decl, 'parameters') if decl else field(fn, 'parameters')
            params = [p for p in children(parameters) if not (p.type == 'parameter_declaration' and text(field(p,'type')) == 'void' and not field(p,'declarator'))]
            for p, value in zip(params, engine.arguments):
                key = field(p, 'name','declarator') or p
                if key.type == 'array_declarator': key = field(key, 'declarator')
                name = text(key)
                if type(value) is int:
                    self.env[name] = Poly.number(value)
                    self.candidates.append(dict(name=name, value=value, origin='parameter', node=None))
                elif isinstance(value, list):
                    self.lengths[name] = Poly.number(len(value))
                    self.candidates.append(dict(name=name, value=len(value), origin='length', node=None))
        # Read straight-line declarations before the first control structure.
        for statement in children(self.root):
            if statement.type in ('for_statement','while_statement','if_statement'): break
            for name, value, node in self.bindings(statement):
                try:
                    p = self.expr(value, self.env)
                    if p.degree() == 0:
                        raw = p.at(0)
                        if raw.denominator == 1:
                            self.env[name] = p
                            self.candidates.append(dict(name=name, value=int(raw), origin='literal' if value.type in ('integer','number','number_literal','decimal_integer_literal') else 'derived', node=node.start_byte))
                except Unknown: pass
        headers = ' '.join(text(field(n,'right','condition'))+' '+text(field(n,'initializer','init')) for n in self.loops)
        used = [c for c in self.candidates if re.search(r'\b'+re.escape(c['name'])+r'\b', headers)]
        used.sort(key=lambda c: (c['name'] != 'n', c['origin'] == 'length'))
        if used:
            self.size = dict(used[0]); self.size['symbol'] = 'n'
            if self.size['value'] < 0: raise Unknown('NEGATIVE_SIZE')
            if self.size['value'] > 2**53-1: raise Unknown('ANALYSIS_LIMIT')
            # Independent extra bounds cannot be treated as hidden constants.
            aliases = {self.array_length(value) for statement in children(self.root) for name,value,node in self.bindings(statement) if self.size['node'] == node.start_byte}
            if any(c['name'] != self.size['name'] and c['origin'] in ('parameter','length') and c['name'] not in aliases for c in used):
                raise Unknown('MULTIPLE_SIZES')
            target = self.lengths if self.size['origin'] == 'length' else self.env
            target[self.size['name']] = Poly.var('n')
        # n=length(a) is a size alias, so array-length occurrences share n.
        if self.size and self.size['origin'] == 'derived':
            for statement in children(self.root):
                for name, value, _ in self.bindings(statement):
                    if name == self.size['name']:
                        array = self.array_length(value)
                        if array: self.lengths[array] = Poly.var('n')
        self.initial = dict(self.env)

    def budget(self):
        self.work += 1
        if self.work > 4000: raise Unknown('ANALYSIS_LIMIT')

    def array_length(self, n):
        if n is None: return ''
        if n.type in ('field_access','member_expression','attribute') and text(field(n,'field','property','attribute')) == 'length':
            return text(field(n,'object','value'))
        if n.type in ('call','call_expression') and text(field(n,'function')) == 'len':
            args = children(field(n,'arguments')); return text(args[0]) if len(args) == 1 else ''
        return ''

    def expr(self, n, env):
        self.budget()
        if n is None: raise Unknown('BOUND')
        t = n.type
        if t in ('parenthesized_expression','condition'): return self.expr(children(n)[0], env)
        if t == 'identifier':
            if text(n) not in env or env[text(n)] is None: raise Unknown('BOUND')
            return env[text(n)]
        if re.fullmatch(r'\d+', text(n)):
            if len(text(n)) > 12: raise Unknown('BOUND')
            return Poly.number(int(text(n)))
        array = self.array_length(n)
        if array: return self.lengths.get(array) or self.fail('BOUND')
        if t in ('unary_expression','unary_operator'):
            if operator(n) == '-': return -self.expr(children(n)[0], env)
        if t in ('binary_expression','binary_operator'):
            a, b = self.expr(field(n,'left'), env), self.expr(field(n,'right'), env)
            op = operator(n)
            if op == '+': return a + b
            if op == '-': return a - b
            if op == '*' and (a.degree() == 0 or b.degree() == 0): return a * b
        raise Unknown('BOUND')

    def fail(self, reason): raise Unknown(reason)

    def bindings(self, n):
        if n.type == 'expression_statement':
            return [b for child in children(n) for b in self.bindings(child)]
        if n.type in ('assignment','assignment_expression') and operator(n) in ('=',''):
            left = field(n,'left')
            return [(text(left),field(n,'right'),n)] if left and left.type == 'identifier' else []
        result = []
        if n.type in ('declaration','local_variable_declaration','lexical_declaration','variable_declaration'):
            for d in children(n):
                name = field(d,'name','declarator'); value = field(d,'value')
                if name and name.type == 'identifier' and value: result.append((text(name),value,d))
        return result

    def extreme(self, p, domains, maximum):
        if p.degree() > 1: raise Unknown('BOUND')
        for name, lower, upper in reversed(domains):
            coefficient = p.terms.get((name,), 0)
            p = p.sub(name, upper - 1 if (coefficient >= 0) == maximum else lower)
        return p

    def nonnegative(self, p):
        if p.degree() > 1 or any(k not in ((), ('n',)) for k in p.terms): return False
        slope = p.terms.get(('n',), 0); constant = p.terms.get((), 0)
        return slope >= 0 and slope * self.minimum + constant >= 0

    def truth(self, n, env, domains):
        if n.type in ('parenthesized_expression','condition'): return self.truth(children(n)[0], env, domains)
        if text(n) in ('true','True'): return True
        if text(n) in ('false','False'): return False
        try:
            cs = children(n)
            left, right = field(n,'left'), field(n,'right')
            if n.type == 'comparison_operator' and len(cs) == 2: left,right = cs
            delta = self.expr(left, env) - self.expr(right, env)
            lo, hi = self.extreme(delta, domains, False), self.extreme(delta, domains, True)
            op = operator(n)
            if op in ('<','<='): lo,hi,op = -hi,-lo,('>' if op == '<' else '>=')
            if op == '>':
                if self.nonnegative(lo - 1): return True
                if self.nonnegative(-hi): return False
            if op == '>=':
                if self.nonnegative(lo): return True
                if self.nonnegative(-hi - 1): return False
            if op in ('==','===') and not lo.terms and not hi.terms: return True
        except Unknown: pass
        return None

    def loop(self, n, env, domains):
        if len(domains) >= 4: raise Unknown('POLYNOMIAL_LIMIT')
        update = None
        if self.engine.language == 'python':
            name = text(field(n,'left')); call = field(n,'right')
            if call.type != 'call' or text(field(call,'function')) != 'range': raise Unknown('LOOP_FORM')
            args = children(field(call,'arguments'))
            if not 1 <= len(args) <= 3: raise Unknown('LOOP_FORM')
            start = Poly.number(0) if len(args) == 1 else self.expr(args[0],env)
            end = self.expr(args[0] if len(args) == 1 else args[1], env)
            step = self.expr(args[2], env).at(0) if len(args) == 3 else 1
            if step not in (-1,1): raise Unknown('LOOP_FORM')
            lower, upper = (start,end) if step == 1 else (end + 1,start + 1)
        else:
            init = field(n,'initializer','init'); bindings = self.bindings(init) if init else []
            if len(bindings) != 1: raise Unknown('LOOP_FORM')
            name,value,_ = bindings[0]; start = self.expr(value,env)
            condition = field(n,'condition')
            if condition and condition.type in ('parenthesized_expression','condition'): condition = children(condition)[0]
            if not condition or text(field(condition,'left')) != name: raise Unknown('LOOP_FORM')
            end = self.expr(field(condition,'right'),env); op = operator(condition)
            update = field(n,'update','increment'); update_text = re.sub(r'\s','',text(update))
            if update_text in (name+'++','++'+name,name+'+=1') and op in ('<','<='):
                lower,upper = start,end + (1 if op == '<=' else 0)
            elif update_text in (name+'--','--'+name,name+'-=1') and op in ('>','>='):
                lower,upper = end + (1 if op == '>' else 0),start + 1
            else: raise Unknown('LOOP_FORM')
        if not re.fullmatch(r'[A-Za-z_]\w*',name) or name == 'n' or name in [d[0] for d in domains]: raise Unknown('COUNTER_SHADOW')
        if lower.degree() > 1 or upper.degree() > 1: raise Unknown('BOUND')
        count = upper - lower
        minimum_count = self.extreme(count,domains,False)
        # Restrict the displayed domain when a positive offset needs n >= k.
        slope = minimum_count.terms.get(('n',),0)
        if slope > 0 and minimum_count.degree() <= 1:
            from math import ceil
            self.minimum = max(self.minimum,ceil(-minimum_count.terms.get((),0)/slope))
        if not self.nonnegative(minimum_count): raise Unknown('EMPTY_DOMAIN')
        if self.minimum > 64: raise Unknown('EMPTY_DOMAIN')
        self.record(n,'loop',count,domains,'loop')
        if update:
            self.record(n,'condition',count + 1,domains,'condition')
            self.record(update,'update',count,domains,'update',line=self.engine.line(n))
        local = dict(env); local[name] = Poly.var(name)
        self.visit(field(n,'body'),local,domains + [(name,lower,upper)])
        # Changed ordinary variables are not assumed invariant after a loop.
        for key in set(env) & set(local):
            if local[key] is None: env[key] = None

    def record(self, n, kind, p, domains, event, line=None):
        if len(self.records) >= 80: raise Unknown('ANALYSIS_LIMIT')
        expression = p.display(); example = []
        for name,lower,upper in reversed(domains):
            if name == domains[0][0]:
                current = self.size['value'] if self.size else 0
                lo,hi = lower.at(current),upper.at(current)
                if lo.denominator == hi.denominator == 1 and 0 < hi-lo <= 12:
                    example = [int(p.sub(name,Poly.number(i)).at(current)) for i in range(int(lo),int(hi))]
            expression = f'Σ({name}={lower.display()}…{(upper-1).display()}) [{expression}]'
            p = summation(p,name,lower,upper)
        if any(set(k) - {'n'} for k in p.terms): raise Unknown('MULTIPLE_SIZES')
        measured = self.engine.loop_visits[n.start_byte] if kind == 'loop' else self.engine.event_counts[(n.start_byte,event)]
        self.records.append(dict(id=f'{n.start_byte}:{kind}',line=line or self.engine.line(n),kind=kind,
                                 summation=expression,formula=p.display(),coefficients=[float(p.terms.get(('n',)*i,0)) for i in range(p.degree()+1)],
                                 measured=measured,degree=p.degree(),exampleTerms=example,_poly=p))

    def check_effects(self, n, env, domains, conditional=False):
        protected = ({d[0] for d in domains} | {k for k,v in (env | self.lengths).items() if v is not None and v.degree() > 0}) if domains else set()
        for node in walk(n):
            if node.type == 'array_creation_expression' or operator(node) == '**': raise Unknown('BULK_OPERATION')
            if node.type in ('call','call_expression','method_invocation'):
                name = text(field(node,'function','name'))
                if name in self.engine.functions: raise Unknown('CALL_OR_RECURSION')
                # Only constant-cost scalar output/length and index-independent
                # helpers are admitted. List mutation and bulk operations vary.
                if name not in ('len','print','printf','println','System.out.println','System.out.print','console.log','abs','min','max'):
                    raise Unknown('CALL_COST')
            if node.type in ('assignment','assignment_expression','augmented_assignment','augmented_assignment_expression','update_expression'):
                target = field(node,'left','argument') or (children(node)[0] if children(node) else None)
                def targets(target):
                    if target is None: return set()
                    if target.type == 'identifier': return {text(target)}
                    if target.type in ('pattern_list','tuple_pattern','list_pattern'): return set().union(*(targets(c) for c in children(target)))
                    return set()
                names = targets(target)
                scalar = target and target.type in ('identifier','pattern_list','tuple_pattern','list_pattern')
                if scalar and names & protected: raise Unknown('CHANGING_BOUND')
                if scalar:
                    for name in names:
                        env[name] = None
                        self.lengths.pop(name,None)
            if node.type in ('break_statement','continue_statement','return_statement') and (domains or conditional): raise Unknown('EARLY_EXIT')
            if node.type in ('while_statement','for_statement'): raise Unknown('CONDITIONAL_LOOP')

    def visit(self, n, env, domains):
        self.budget()
        if n is None: return
        t = n.type
        if t in ('function_definition','function_declaration','method_declaration','comment','line_comment','block_comment','preproc_include'): return
        if t in ('module','program','translation_unit','block','compound_statement','statement_block','else_clause'):
            for child in children(n):
                if self.visit(child,env,domains): return True
            return
        if t == 'for_statement': self.loop(n,env,domains); return
        if t == 'while_statement': raise Unknown('WHILE')
        if t in ('if_statement','elif_clause'):
            condition = field(n,'condition')
            self.check_effects(condition,dict(env),domains)
            self.record(n,'branch',Poly.number(1),domains,'branch')
            known = self.truth(condition,env,domains)
            if known is not None:
                if not known and len(self.notes) < 12: self.notes.append(dict(line=self.engine.line(n),kind='always-false'))
                return self.visit(field(n,'consequence') if known else field(n,'alternative'),env,domains)
            else:
                # The condition count is exact. Branch body counts are not;
                # ensure unknown branches cannot change a modeled loop bound.
                for branch in ('consequence','alternative'):
                    body = field(n,branch)
                    if body: self.check_effects(body,env,domains,conditional=True)
                if len(self.notes) < 12: self.notes.append(dict(line=self.engine.line(n),kind='data-dependent'))
            return
        bindings = self.bindings(n)
        values = {}
        for name,value,node in bindings:
            try:
                p = self.expr(value,env)
                if self.size and self.size['node'] == node.start_byte: p = Poly.var('n')
                values[name] = p
            except Unknown: values[name] = None
        self.check_effects(n,env,domains)
        if bindings:
            if domains:
                protected = {d[0] for d in domains} | {k for k,v in self.initial.items() if v is not None and v.degree() > 0}
                if set(values) & protected: raise Unknown('CHANGING_BOUND')
            env.update(values)
        return t == 'return_statement'

    def analyze(self):
        self.visit(self.root,dict(self.initial),[])
        current = self.size['value'] if self.size else 0
        if current < self.minimum: raise Unknown('OUTSIDE_DOMAIN')
        for record in self.records:
            p = record.pop('_poly'); value = p.at(current)
            if value.denominator != 1 or value < 0: raise Unknown('VERIFICATION')
            if max(abs(value),abs(p.at(512))) > 2**53-1: raise Unknown('ANALYSIS_LIMIT')
            record['predicted'] = int(value)
            record['verification'] = 'matched' if self.status == 'completed' else 'partial'
            if self.status == 'completed' and record['measured'] != value: raise Unknown('VERIFICATION')
        degree = max((r['degree'] for r in self.records),default=0)
        growth = '1' if not degree else 'n' if degree == 1 else f'n^{degree}'
        size = {k:v for k,v in self.size.items() if k != 'node'} if self.size else None
        return dict(version=1,status='derived',size=size,minimum=self.minimum,maximum=512,records=self.records,notes=self.notes,
                    complexity=f'Θ({growth})',upperBound=f'O({growth})',degree=degree,
                    costModel='scalar-unit-cost',partial=self.status != 'completed')


def analyze(engine, status):
    if status == 'error': return dict(version=1,status='unavailable',reason='RUN_ERROR',records=[])
    try: return Analyzer(engine,status).analyze()
    except Unknown as e: return dict(version=1,status='unavailable',reason=str(e),records=[])
    except (ValueError,TypeError,KeyError,IndexError,AttributeError,RecursionError,OverflowError):
        return dict(version=1,status='unavailable',reason='UNSUPPORTED',records=[])
