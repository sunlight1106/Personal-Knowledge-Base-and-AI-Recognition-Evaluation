"""Bounded educational interpreter. User source is NEVER passed to eval/exec/compiler.

Tree-sitter parses syntax; this module explicitly implements a small algorithmic
subset. It has no user-accessible filesystem, network, imports or reflection.
Unsupported semantics fail closed, including unused unsupported code.
"""
import ast
import collections
import json
import math
import re
import time
from tree_sitter import Language, Parser
import tree_sitter_c
import tree_sitter_java
import tree_sitter_python
import tree_sitter_javascript

GRAMMARS = dict(c=tree_sitter_c, java=tree_sitter_java,
                python=tree_sitter_python, javascript=tree_sitter_javascript)
LIMITS = dict(sourceBytes=16000, lines=400, operations=20000, steps=1000,
              stack=24, collection=128, string=2000, output=8000, traceBytes=1500000)
COMMENTS = {'comment', 'line_comment', 'block_comment'}
BLOCKS = {'block', 'compound_statement', 'statement_block', 'module', 'program', 'translation_unit'}
FUNCS = {'function_definition', 'function_declaration', 'method_declaration'}
ASSIGNS = {'assignment', 'assignment_expression', 'augmented_assignment', 'augmented_assignment_expression'}
NUMBERS = {'number', 'integer', 'float', 'number_literal', 'decimal_integer_literal',
           'hex_integer_literal', 'binary_integer_literal', 'octal_integer_literal'}
ALLOWED = BLOCKS | FUNCS | COMMENTS | ASSIGNS | NUMBERS | {
 'class_declaration','class_body','modifiers','type_identifier','primitive_type','integral_type','void_type',
 'array_type','dimensions','formal_parameters','formal_parameter','parameter_list','parameter_declaration',
 'parameters','function_declarator','array_declarator','init_declarator','identifier','property_identifier',
 'expression_statement','declaration','local_variable_declaration','lexical_declaration','variable_declaration',
 'variable_declarator','return_statement','if_statement','elif_clause','else_clause','while_statement',
 'for_statement','break_statement','continue_statement','pass_statement','empty_statement',
 'binary_expression','binary_operator','boolean_operator','comparison_operator','unary_expression','unary_operator',
 'not_operator','parenthesized_expression','condition','update_expression','ternary_expression','conditional_expression',
 'call','call_expression','method_invocation','argument_list','arguments','attribute','member_expression','field_access',
 'subscript','subscript_expression','array_access','list','tuple','array','array_initializer','initializer_list',
 'array_creation_expression','dimensions_expr','true','false','null','none','string','string_literal',
 'string_content','string_fragment','string_start','string_end','escape_sequence','character_literal',
 'char_literal','preproc_include','system_lib_string','string_literal','expression_list','pattern_list',
 'list_pattern','tuple_pattern','boolean_type', 'decimal_floating_point_literal'
}


class Fault(Exception):
    def __init__(self, message, node=None, code='UNSUPPORTED'):
        self.message, self.node, self.code = message, node, code


class Return(Exception):
    def __init__(self, value): self.value = value


class Break(Exception): pass
class Continue(Exception): pass
UNSET = object()


def text(n): return n.text.decode('utf-8') if n else ''
def field(n, *names):
    for name in names:
        value = n.child_by_field_name(name)
        if value is not None: return value
    return None
def children(n): return [c for c in n.named_children if c.type not in COMMENTS]
def walk(n):
    yield n
    for c in n.named_children: yield from walk(c)


def parse(code, language):
    parser = Parser(Language(GRAMMARS[language].language()))
    root = parser.parse(code.encode()).root_node
    offset = 0
    # Java method snippets are valid educational inputs without a class wrapper.
    if language == 'java' and not re.search(r'\bclass\s+\w+', code):
        root = parser.parse(('class CodeLab {\n' + code + '\n}').encode()).root_node
        offset = 1
    return root, offset


def detect(code):
    if re.search(r'^\s*(def |print\(|for .+ in |elif )', code, re.M): return 'python'
    if re.search(r'\b(class|public|private|static)\b|System\.out|\bint\s*\[\]', code): return 'java'
    if re.search(r'\b(function|let|const|var)\b|console\.log', code): return 'javascript'
    if re.search(r'#include|\b(?:int|void|double|char)\s+\w+\s*\(', code): return 'c'
    candidates = [lang for lang in GRAMMARS if not parse(code, lang)[0].has_error]
    if len(candidates) == 1: return candidates[0]
    raise Fault('语言不明确，请手动选择 C、Python、Java 或 JavaScript。', code='LANGUAGE_REQUIRED')


class Engine:
    def __init__(self, request):
        self.source = request.get('source', '')
        if not isinstance(self.source, str) or not self.source.strip(): raise Fault('请先输入代码。', code='INPUT')
        if len(self.source.encode()) > LIMITS['sourceBytes'] or len(self.source.splitlines()) > LIMITS['lines']:
            raise Fault('代码最多 400 行、16 KB。', code='LIMIT')
        self.language = request.get('language', 'auto')
        if self.language == 'auto': self.language = detect(self.source)
        if self.language not in GRAMMARS: raise Fault('当前支持 C、Python、Java、JavaScript。', code='INPUT')
        self.root, self.offset = parse(self.source, self.language)
        self.steps, self.frames, self.functions = [], [], {}
        self.counts, self.loops, self.calls = collections.Counter(), collections.Counter(), collections.Counter()
        self.loop_visits, self.event_counts = collections.Counter(), collections.Counter()
        self.ops = self.trace_bytes = 0
        self.output, self.deadline = '', time.monotonic() + 3
        self.last = self.root
        self.globals = {'name':'<module>', 'scopes':[{}], 'const':[set()], 'types':[{}]}
        self.frames.append(self.globals)
        self.entry = request.get('entry', '')
        self.arguments = request.get('arguments', [])
        if not isinstance(self.entry, str) or (self.entry and not re.fullmatch(r'[A-Za-z_]\w{0,59}', self.entry)):
            raise Fault('入口应为函数名称。', code='INPUT')
        if not isinstance(self.arguments, list) or len(self.arguments) > 16: raise Fault('参数应为 JSON 数组，最多 16 项。', code='INPUT')
        self.checked(self.arguments)

    def line(self, n): return max(1, n.start_point.row + 1 - self.offset)

    def tick(self, n):
        self.ops += 1
        self.last = n
        if self.ops > LIMITS['operations'] or time.monotonic() > self.deadline:
            raise Fault('达到运算或时间上限；已保留截止前的回放。', n, 'LIMIT')

    def checked(self, value, depth=0):
        if depth > 8: raise Fault('数据嵌套最多 8 层。', self.last, 'LIMIT')
        if value is UNSET or value is None or type(value) is bool: return value
        if type(value) is int:
            if value.bit_length() > (31 if self.language in ('c','java') else 53 if self.language=='javascript' else 256):
                if self.language in ('c','java') and value == -2147483648: return value
                raise Fault('整数超出本解释器范围；C / Java 使用有符号 32 位整数，溢出停止。', self.last, 'LIMIT')
        elif type(value) is float:
            if self.language in ('c','java'): raise Fault('本版 C / Java 回放仅支持整数算法。', self.last)
            if not math.isfinite(value): raise Fault('数值不是有限数。', self.last, 'RUNTIME')
        elif isinstance(value, str):
            if len(value) > LIMITS['string']: raise Fault('字符串长度超过上限。', self.last, 'LIMIT')
        elif isinstance(value, (list,tuple)):
            if len(value) > LIMITS['collection']: raise Fault('数组或列表最多 128 项。', self.last, 'LIMIT')
            for item in value: self.checked(item, depth+1)
        else: raise Fault('只支持数字、布尔、字符串和数组参数。', self.last)
        return value

    def snapshot(self, value, depth=0):
        if value is UNSET: return '<uninitialized>'
        if depth > 8: raise Fault('不支持循环引用。', self.last)
        if isinstance(value, (list,tuple)): return [self.snapshot(v,depth+1) for v in value]
        return value

    def emit(self, n, event='statement', detail=None):
        if len(self.steps) >= LIMITS['steps']: raise Fault('已达到 1000 步回放上限。', n, 'LIMIT')
        frames=[]
        for f in self.frames:
            values={}
            for scope in f['scopes']: values.update(scope)
            if len(values)>64: raise Fault('每个调用帧最多 64 个变量。',n,'LIMIT')
            frames.append({'name':f['name'], 'variables':{k:self.snapshot(v) for k,v in values.items()}})
        step={'line':self.line(n),'event':event,'frames':frames,'outputLength':len(self.output)}
        if detail is not None: step['detail']=self.snapshot(detail)
        size=len(json.dumps(step, ensure_ascii=False).encode())
        if self.trace_bytes + size > LIMITS['traceBytes']: raise Fault('回放数据达到大小上限。',n,'LIMIT')
        self.trace_bytes+=size
        self.steps.append(step)
        self.counts[str(self.line(n))] += 1
        self.event_counts[(n.start_byte,event)] += 1

    def validate(self):
        nodes=list(walk(self.root))
        if len(nodes)>6000: raise Fault('语法树过大。',self.root,'LIMIT')
        for n in nodes:
            if n.type=='ERROR' or n.is_missing: raise Fault('语法错误，请检查这一行。',n,'SYNTAX')
            if n.type not in ALLOWED: raise Fault('暂不支持语法：'+n.type,n)
            if self.language in ('c','java') and n.type=='modifiers' and any(x not in ('public','private','protected','static') for x in text(n).split()):
                raise Fault('暂不支持此修饰符。',n)
            if self.language=='java' and n.type=='method_declaration' and 'static' not in text(next((c for c in n.named_children if c.type=='modifiers'),None)):
                raise Fault('本版仅支持 static 方法；类实例尚未支持。',n)
            if n.type in ASSIGNS:
                left=field(n,'left')
                if any(c.type in ASSIGNS|{'update_expression','call','call_expression','method_invocation'} for c in walk(left)):
                    raise Fault('请将赋值下标中的调用或自增拆成独立语句。',n)
            if self.language=='javascript' and n.type=='variable_declaration':
                raise Fault('本版请使用 let / const，暂不支持 var 的提升语义。',n)
            if n.type=='preproc_include' and not re.fullmatch(r'#\s*include\s*<(stdio|stdbool)\.h>\s*',text(n)):
                raise Fault('仅识别 stdio.h / stdbool.h；不会载入其他头文件。',n)
            if self.language in ('c','java') and n.type in ('primitive_type','integral_type','type_identifier'):
                if text(n) not in ('int','void','boolean','bool','String','CodeLab','Main'):
                    raise Fault('本版仅支持 int、boolean 和一维数组算法；此类型尚未支持。',n)
            if n.type in FUNCS:
                name=field(n,'name')
                decl=field(n,'declarator')
                if decl: name=field(decl,'declarator')
                key=text(name)
                if not key or key in self.functions: raise Fault('不支持重载、嵌套或重复函数。',n)
                if any(p.type in FUNCS for p in self.parents(n)): raise Fault('暂不支持嵌套函数。',n)
                self.functions[key]=n
        if self.root.has_error: raise Fault('语法不完整。',self.root,'SYNTAX')

    def parents(self,n):
        while n.parent:
            n=n.parent
            yield n

    def lookup(self,name,n):
        for scope in reversed(self.frames[-1]['scopes']):
            if name in scope:
                if scope[name] is UNSET: raise Fault('变量尚未初始化：'+name,n,'RUNTIME')
                return scope[name]
        if self.frames[-1] is not self.globals and name in self.globals['scopes'][0]:
            value=self.globals['scopes'][0][name]
            if value is UNSET: raise Fault('变量尚未初始化：'+name,n,'RUNTIME')
            return value
        raise Fault('未定义变量：'+name,n,'RUNTIME')

    def typed(self,value,type_node,declarator,n):
        if self.language not in ('c','java') or value is UNSET:return
        kind=text(type_node)
        array='[]' in kind or declarator is not None and declarator.type=='array_declarator'
        base=kind.replace('[]','').strip()
        values=value if array and isinstance(value,list) else [value]
        if array and not isinstance(value,list):raise Fault('数组参数或初值必须为数组。',n,'RUNTIME')
        if not array and isinstance(value,(list,tuple)):raise Fault('标量类型不能接收数组。',n,'RUNTIME')
        for item in values:
            if item is UNSET:continue
            if base=='int' and type(item)is not int or base in ('boolean','bool') and type(item)is not bool or base=='String' and not isinstance(item,str):
                raise Fault('参数、初值或返回值与声明类型不一致。',n,'RUNTIME')

    def index(self,target,index,n):
        if not isinstance(target,(list,tuple,str)) or type(index) is not int: raise Fault('下标必须是整数，目标必须是数组。',n,'RUNTIME')
        if index<0 and self.language!='python': raise Fault('数组下标不能为负数。',n,'RUNTIME')
        if not -len(target)<=index<len(target): raise Fault('数组下标越界。',n,'RUNTIME')
        return index

    def target(self,n):
        if n.type=='identifier': return None,text(n)
        if n.type in ('subscript','subscript_expression','array_access'):
            target=self.expr(field(n,'value','argument','object','array'))
            index=self.expr(field(n,'subscript','index'))
            return target,self.index(target,index,n)
        raise Fault('不支持这个赋值目标。',n)

    def declared_type(self,name):
        for types in reversed(self.frames[-1]['types']):
            if name in types:return types[name]
        return None

    def assign(self,n,value,declare=False,const=False):
        self.checked(value)
        if n.type in ('pattern_list','tuple_pattern','list_pattern'):
            names=children(n)
            if not isinstance(value,(list,tuple)) or len(names)!=len(value): raise Fault('解包数量不一致。',n,'RUNTIME')
            for key,val in zip(names,list(value)): self.assign(key,val,declare)
            return value
        target,key=self.target(n)
        if target is not None:
            if not isinstance(target,list): raise Fault('此值不可修改。',n,'RUNTIME')
            if value is target: raise Fault('不支持循环引用。',n)
            obj=field(n,'value','argument','object','array')
            declared=self.declared_type(text(obj))
            if declared:
                tn,_=declared
                self.typed(value,field(tn,'element') or tn,None,n)
            old=target[key];target[key]=value
            try:self.checked(target)
            except Fault:target[key]=old;raise
            return value
        frame=self.frames[-1]
        scopes=frame['scopes']
        scope=next((s for s in reversed(scopes) if key in s),None) if not declare else None
        if declare and key in scopes[-1]: raise Fault('变量重复声明：'+key,n,'RUNTIME')
        if scope is None and not declare and self.language not in ('python',): raise Fault('请先声明变量：'+key,n,'RUNTIME')
        if scope is None:scope=scopes[-1]
        scope_index=next(i for i,s in enumerate(scopes) if s is scope)
        if key in frame['const'][scope_index] and not declare: raise Fault('const 变量不能重新赋值。',n,'RUNTIME')
        declared=frame['types'][scope_index].get(key)
        if declared:
            tn,dn=declared
            if self.language=='c' and isinstance(value,list) and not declare:raise Fault('C 数组不能整体赋值，请逐项复制。',n)
            self.typed(value,tn,dn,n)
        scope[key]=value
        if const:frame['const'][scope_index].add(key)
        return value

    def operator(self,n):
        op=field(n,'operator')
        if op:return text(op)
        return next((text(c) for c in n.children if not c.is_named and text(c) not in ('(',')',';',',')), '')

    def binary(self,op,a,b,n):
        numeric=type(a) in (int,float) and type(b) in (int,float)
        if op in ('==','===','!=','!==','<','<=','>','>='):
            if type(a)!=type(b) and not numeric:raise Fault('比较需要相同类型。',n,'RUNTIME')
            if isinstance(a,(list,tuple)) or isinstance(b,(list,tuple)):raise Fault('数组比较语义尚未支持。',n)
            return {'==':lambda:a==b,'===':lambda:a==b,'!=':lambda:a!=b,'!==':lambda:a!=b,
                    '<':lambda:a<b,'<=':lambda:a<=b,'>':lambda:a>b,'>=':lambda:a>=b}[op]()
        if op=='+' and isinstance(a,str) and isinstance(b,str):return self.checked(a+b)
        if not numeric:raise Fault('此运算需要数字，不进行隐式类型转换。',n,'RUNTIME')
        if op=='**' and (abs(b)>256 or (a!=0 and abs(b)*max(1,abs(a).bit_length() if type(a)is int else 64)>1024)):
            raise Fault('指数运算超过上限。',n,'LIMIT')
        if op in ('<<','>>') and (type(b)is not int or not 0<=b<=31):raise Fault('移位量必须在 0 到 31 之间。',n,'LIMIT')
        trunc=lambda: (abs(a)//abs(b)) * (-1 if (a<0)!=(b<0) else 1)
        operations={'+':lambda:a+b,'-':lambda:a-b,'*':lambda:a*b,
                    '/':lambda:trunc() if self.language in ('c','java') else a/b,
                    '//':lambda:a//b,'%':lambda:a-trunc()*b if self.language in ('c','java','javascript') and type(a)is int and type(b)is int else a%b,
                    '**':lambda:a**b,'&':lambda:a&b,'|':lambda:a|b,'^':lambda:a^b,'<<':lambda:a<<b,'>>':lambda:a>>b}
        if op not in operations:raise Fault('暂不支持运算符：'+op,n)
        return self.checked(operations[op]())

    def expr(self,n):
        if n is None:return None
        self.tick(n);t=n.type;cs=children(n);raw=text(n)
        if t in NUMBERS:
            raw=raw.replace('_','')
            if re.search(r'[.eE]',raw) and not raw.lower().startswith(('0x','0b')):return self.checked(float(raw))
            if re.search(r'[uUlLfFdD]$',raw) and not raw.lower().startswith('0x'):raise Fault('暂不支持数值类型后缀。',n)
            return self.checked(int(raw,0) if raw.lower().startswith(('0x','0b','0o')) else int(raw,8) if self.language in ('c','java') and len(raw)>1 and raw.startswith('0') else int(raw))
        if t in ('true','false'):return t=='true'
        if t in ('null','none'):return None
        if t in ('string','string_literal','character_literal','char_literal'):
            try:value=ast.literal_eval(raw)
            except (ValueError,SyntaxError):raise Fault('不支持此字符串格式或转义。',n)
            if not isinstance(value,str):raise Fault('只支持普通字符串。',n)
            return self.checked(value)
        if t=='identifier':
            if self.language=='c' and raw in ('true','false'):return raw=='true'
            return self.lookup(raw,n)
        if t in ('parenthesized_expression','condition'):return self.expr(cs[0])
        if t in ('expression_list','pattern_list'):return [self.expr(c) for c in cs]
        if t=='tuple':return tuple(self.checked([self.expr(c) for c in cs]))
        if t in ('list','array','array_initializer','initializer_list'):return self.checked([self.expr(c) for c in cs])
        if t=='array_creation_expression':
            init=field(n,'value')
            if init:return self.expr(init)
            dims=[c for c in cs if c.type=='dimensions_expr']
            if len(dims)!=1:raise Fault('当前只支持一维数组。',n)
            size=self.expr(children(dims[0])[0])
            if type(size)is not int or not 0<=size<=128:raise Fault('数组长度须为 0 到 128。',n,'LIMIT')
            return [0]*size
        if t in ('subscript','subscript_expression','array_access'):
            target,index=self.target(n)
            if target[index] is UNSET:raise Fault('数组元素尚未初始化。',n,'RUNTIME')
            return target[index]
        if t in ('attribute','member_expression','field_access'):
            obj=self.expr(field(n,'object'));name=text(field(n,'attribute','property','field'))
            if name=='length' and isinstance(obj,(list,str)):return len(obj)
            raise Fault('此属性不可访问。',n)
        if t in ('binary_expression','binary_operator','boolean_operator','comparison_operator'):
            left=field(n,'left') or cs[0];right=field(n,'right') or cs[-1];op=self.operator(n)
            a=self.expr(left)
            if op in ('and','&&'):return self.expr(right) if a else a
            if op in ('or','||'):return a if a else self.expr(right)
            if t=='comparison_operator' and len(cs)>2:raise Fault('请将连续比较拆成两个条件。',n)
            return self.binary(op,a,self.expr(right),n)
        if t in ('unary_expression','unary_operator','not_operator'):
            value=self.expr(field(n,'argument') or cs[-1]);op=self.operator(n)
            if op in ('!','not'):return not value
            if type(value) not in (int,float):raise Fault('一元运算需要数字。',n,'RUNTIME')
            if op=='+':return value
            if op=='-':return self.checked(-value)
            if op=='~' and type(value)is int:return self.checked(~value)
            raise Fault('不支持此一元运算。',n)
        if t in ASSIGNS:
            left,right=field(n,'left'),field(n,'right');op=self.operator(n)
            value=self.expr(right)
            if op not in ('=',''):
                value=self.binary(op[:-1],self.expr(left),value,n)
            return self.assign(left,value)
        if t=='update_expression':
            target=field(n,'argument') or cs[0];old=self.expr(target);op='+' if '++' in raw else '-'
            new=self.binary(op,old,1,n);self.assign(target,new)
            return new if raw.startswith(('++','--')) else old
        if t in ('conditional_expression','ternary_expression'):
            if self.language=='python':return self.expr(cs[0] if self.expr(cs[1]) else cs[2])
            return self.expr(field(n,'consequence') if self.expr(field(n,'condition')) else field(n,'alternative'))
        if t in ('call','call_expression','method_invocation'):
            fn=field(n,'function');name=text(fn) if fn else ((text(field(n,'object'))+'.') if field(n,'object') else '')+text(field(n,'name'))
            args=[self.expr(c) for c in children(field(n,'arguments'))]
            if name in self.functions:return self.call(name,args,n)
            return self.builtin(name,args,n)
        raise Fault('暂不支持表达式：'+t,n)

    def builtin(self,name,args,n):
        if name in ('print','console.log','System.out.println','System.out.print','printf'):
            if name=='printf':
                if not args or not isinstance(args[0],str):raise Fault('printf 的第一个参数应为格式字符串。',n)
                pattern=args[0]
                if re.search(r'%(?!%|d|s)',pattern):raise Fault('printf 仅支持 %d、%s 和 %%。',n)
                out=pattern%tuple(args[1:])
            else:out=' '.join(self.format_value(v) for v in args)+('' if name=='System.out.print' else '\n')
            if len(self.output)+len(out)>LIMITS['output']:raise Fault('输出达到 8000 字符上限。',n,'LIMIT')
            self.output+=out;return None
        if name in ('len',) and len(args)==1 and isinstance(args[0],(list,str)):return len(args[0])
        if name=='range':
            if not 1<=len(args)<=3 or any(type(x)is not int for x in args):raise Fault('range 需要 1 到 3 个整数。',n)
            values=range(*args)
            if len(values)>128:raise Fault('单个 range 最多 128 项。',n,'LIMIT')
            return list(values)
        if name in ('abs','Math.abs') and len(args)==1 and type(args[0])in(int,float):return self.checked(abs(args[0]))
        if name in ('min','max','Math.min','Math.max'):
            values=args[0] if len(args)==1 and isinstance(args[0],list) else args
            if not values or any(type(v)not in(int,float) for v in values):raise Fault('min / max 需要数字。',n)
            return min(values) if name.endswith('min') else max(values)
        if name in ('int','str','float') and self.language=='python' and len(args)==1:
            if not isinstance(args[0],(str,int,float,bool)):raise Fault('转换只支持标量。',n)
            return self.checked({'int':int,'str':str,'float':float}[name](args[0]))
        match=re.fullmatch(r'([A-Za-z_]\w*)\.(append|push|pop)',name)
        if match and self.language in ('python','javascript'):
            array=self.lookup(match[1],n)
            if not isinstance(array,list):raise Fault('此方法只支持列表。',n)
            method=match[2]
            if method in ('append','push') and len(args)==1:
                if len(array)>=128 or args[0] is array:raise Fault('列表达到上限或包含循环引用。',n,'LIMIT')
                self.checked([*array,args[0]]);array.append(args[0]);return len(array) if method=='push' else None
            if method=='pop' and len(args)==0:
                if not array:raise Fault('不能从空列表移除元素。',n,'RUNTIME')
                return array.pop()
        raise Fault('不支持或禁止调用：'+name,n)

    def format_value(self,v):
        if self.language=='python':return str(v)
        if v is True:return 'true'
        if v is False:return 'false'
        if v is None:return 'null'
        if isinstance(v,list):raise Fault('请逐项输出数组，避免误用语言原生数组显示语义。',self.last)
        return str(v)

    def call(self,name,args,site):
        if len(self.frames)>=LIMITS['stack']:raise Fault('调用栈达到 24 层上限。',site,'LIMIT')
        fn=self.functions[name];decl=field(fn,'declarator')
        parameters=field(fn,'parameters') or field(decl,'parameters') if decl else field(fn,'parameters')
        params=[]
        for p in children(parameters):
            if p.type=='parameter_declaration' and text(field(p,'type'))=='void' and not field(p,'declarator'):continue
            key=field(p,'name','declarator') or p
            if key.type=='array_declarator':key=field(key,'declarator')
            params.append(text(key))
        if len(args)!=len(params):raise Fault(f'{name} 需要 {len(params)} 个参数，当前为 {len(args)} 个。',site,'INPUT')
        declarations=[p for p in children(parameters) if not (p.type=='parameter_declaration' and text(field(p,'type'))=='void' and not field(p,'declarator'))]
        for p,value in zip(declarations,args):self.typed(value,field(p,'type'),field(p,'declarator'),p)
        self.calls[name]+=1
        types={key:(field(p,'type'),field(p,'declarator')) for key,p in zip(params,declarations)} if self.language in ('c','java') else {}
        self.frames.append({'name':name,'scopes':[dict(zip(params,args))],'const':[set()], 'types':[types]})
        self.emit(fn,'call')
        result=None
        try:
            self.stmt(field(fn,'body'),scoped=False)
        except Return as done:result=done.value
        except (Break,Continue):raise Fault('break / continue 必须位于循环内。',self.last,'SYNTAX')
        return_type=field(fn,'type')
        if return_type and text(return_type)!='void':self.typed(result,return_type,None,fn)
        self.emit(fn,'return',result)
        self.frames.pop()
        return result

    def stmt(self,n,scoped=True):
        if n is None:return
        self.tick(n);t=n.type;cs=children(n)
        if t in COMMENTS or t in FUNCS or t=='preproc_include':return
        if t in ('class_declaration','class_body'):
            if t=='class_declaration':self.stmt(field(n,'body'))
            else:
                for c in cs:self.stmt(c)
            return
        if t in BLOCKS:
            local=scoped and self.language!='python' and t not in ('module','program','translation_unit')
            frame=self.frames[-1]
            if local:frame['scopes'].append({});frame['const'].append(set());frame['types'].append({})
            try:
                for c in cs:self.stmt(c)
            finally:
                if local:frame['scopes'].pop();frame['const'].pop();frame['types'].pop()
            return
        if t in ('declaration','local_variable_declaration','lexical_declaration','variable_declaration'):
            const=text(n).startswith('const ')
            for d in cs:
                if d.type not in ('init_declarator','variable_declarator','array_declarator','identifier'):continue
                name=field(d,'name','declarator') or d;value=field(d,'value');size=None
                if name.type=='array_declarator':size=field(name,'size');name=field(name,'declarator')
                if d.type=='array_declarator':size=field(d,'size');name=field(d,'declarator')
                v=self.expr(value) if value else UNSET
                if size:
                    length=self.expr(size)
                    if type(length)is not int or not 0<=length<=128:raise Fault('数组长度须为 0 到 128。',n,'LIMIT')
                    if v is UNSET:v=[UNSET]*length
                    elif not isinstance(v,list) or len(v)>length:raise Fault('数组初值超过声明长度。',n,'RUNTIME')
                    else:v += [0]*(length-len(v))
                self.typed(v,field(n,'type'),field(d,'declarator') or d,n)
                self.assign(name,v,declare=True,const=const)
                if self.language in ('c','java'):self.frames[-1]['types'][-1][text(name)]=(field(n,'type'),field(d,'declarator') or d)
            self.emit(n);return
        if t=='expression_statement':
            for c in cs:self.expr(c)
            self.emit(n);return
        if t in ('if_statement','elif_clause'):
            condition=bool(self.expr(field(n,'condition')));self.emit(n,'branch',condition)
            self.stmt(field(n,'consequence') if condition else field(n,'alternative'));return
        if t=='else_clause':
            for c in cs:self.stmt(c)
            return
        if t in ('for_statement','while_statement'):
            if t=='for_statement' and self.language=='python':
                if field(n,'alternative'):raise Fault('for-else 尚未支持。',n)
                values=self.expr(field(n,'right'))
                if not isinstance(values,(list,str)):raise Fault('for 仅支持列表、字符串与 range。',n)
                for value in list(values):
                    self.assign(field(n,'left'),value);self.loops[str(self.line(n))]+=1;self.loop_visits[n.start_byte]+=1;self.emit(n,'loop',True)
                    try:self.stmt(field(n,'body'))
                    except Continue:continue
                    except Break:break
                self.emit(n,'loop-end');return
            local=self.language!='python'
            frame=self.frames[-1]
            if local:frame['scopes'].append({});frame['const'].append(set());frame['types'].append({})
            try:
                init=field(n,'initializer','init')
                if init:
                    if init.type in ('declaration','local_variable_declaration','lexical_declaration','variable_declaration'):self.stmt(init)
                    else:self.expr(init);self.emit(init)
                condition=field(n,'condition');update=field(n,'update','increment')
                while True:
                    self.tick(n);ok=True if condition is None else bool(self.expr(condition));self.emit(n,'condition',ok)
                    if not ok:break
                    self.loops[str(self.line(n))]+=1
                    self.loop_visits[n.start_byte]+=1
                    try:self.stmt(field(n,'body'))
                    except Continue:pass
                    except Break:break
                    if update:self.expr(update);self.emit(update,'update')
                if field(n,'alternative'):raise Fault('while-else 尚未支持。',n)
            finally:
                if local:frame['scopes'].pop();frame['const'].pop();frame['types'].pop()
            return
        if t=='return_statement':
            value=self.expr(cs[0]) if cs else None;self.emit(n,'return-value',value);raise Return(value)
        if t=='break_statement':self.emit(n,'break');raise Break()
        if t=='continue_statement':self.emit(n,'continue');raise Continue()
        if t in ('pass_statement','empty_statement'):self.emit(n);return
        raise Fault('暂不支持语句：'+t,n)

    def complexity(self, analysis):
        roots=[self.functions[self.entry]] if self.entry in self.functions else [self.root]
        depth=max((1+sum(p.type=='for_statement' for p in self.parents(n))
                   for root in roots for n in walk(root) if n.type=='for_statement'),default=0)
        return {'estimate':analysis.get('upperBound','unknown'),'loopDepth':depth,
                'basis':'从固定边界的单规模 for / range 循环逐层求和；使用单位标量成本模型。详细公式、适用范围与核对结果见 analysis。',
                'measured':'steps 是记录的语句与控制事件；operations 是解释器表达式求值次数，不等于 CPU 指令。'}

    def run(self):
        result=None;error=None;status='completed'
        try:
            self.validate()
            if not self.entry and self.language in ('c','java'):
                if 'main' in self.functions:self.entry='main'
                else:raise Fault('没有 main。请在入口框填写函数名，并提供 JSON 参数。',code='ENTRY_REQUIRED')
            if self.entry:
                if self.entry not in self.functions:raise Fault('找不到指定入口函数。',code='INPUT')
                if self.arguments==[] and self.entry=='main' and self.language=='java' and 'String' in text(field(self.functions['main'],'parameters')):self.arguments=[[]]
                result=self.call(self.entry,self.arguments,self.functions[self.entry])
            else:self.stmt(self.root)
        except Fault as e:
            error={'code':e.code,'message':e.message,'line':self.line(e.node or self.last)};status='limited' if e.code=='LIMIT' else 'error'
        except (ZeroDivisionError,IndexError,ValueError,TypeError,OverflowError,RecursionError) as e:
            error={'code':'RUNTIME','message':'运算失败：'+type(e).__name__+'。请检查类型、除数和下标。','line':self.line(self.last)};status='error'
        except (Return,Break,Continue):
            error={'code':'SYNTAX','message':'return / break / continue 所在位置不正确。','line':self.line(self.last)};status='error'
        from analysis import analyze
        analysis=analyze(self,status)
        complexity=self.complexity(analysis)
        return {'language':self.language,'entry':self.entry,'status':status,'error':error,'steps':self.steps,
                'output':self.output,'result':self.snapshot(result),'lineCounts':dict(self.counts),
                'loopCounts':dict(self.loops),'callCounts':dict(self.calls),'operations':self.ops,
                'complexity':complexity,'analysis':analysis,'limits':LIMITS,'engine':'bounded-interpreter-v1'}


def execute(request):
    try:return Engine(request).run()
    except Fault as e:return {'status':'error','error':{'code':e.code,'message':e.message,'line':1},'steps':[],'limits':LIMITS}


if __name__=='__main__':
    import sys
    try:
        request=json.loads(sys.stdin.buffer.read(65537))
        if not isinstance(request,dict):raise ValueError()
        print(json.dumps(execute(request),ensure_ascii=False,allow_nan=False))
    except Exception:
        print(json.dumps({'status':'error','error':{'code':'INPUT','message':'请求格式不正确。','line':1},'steps':[]}))
