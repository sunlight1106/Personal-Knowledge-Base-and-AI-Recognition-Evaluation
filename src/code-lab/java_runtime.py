"""Java class state for the bounded interpreter, never native Python attributes.

Only classes declared in the submitted syntax tree can be instantiated. Objects
hold a bounded dictionary of declared fields; member resolution never uses
getattr, imports, reflection, eval, a compiler, or a host-language class loader.
"""
from contextlib import contextmanager


def text(n): return n.text.decode('utf-8') if n else ''
def field(n, name): return n.child_by_field_name(name) if n else None
def children(n): return [c for c in n.named_children if c.type not in ('line_comment','block_comment','comment')] if n else []
def modifiers(n): return set(text(next((c for c in children(n) if c.type == 'modifiers'), None)).split())


class JavaClass:
    def __init__(self, name): self.name = name


class JavaObject:
    def __init__(self, name, identity): self.name, self.identity, self.values = name, identity, {}


class JavaRuntime:
    def java_prepare(self, nodes):
        self.java_classes, self.java_methods, self.java_objects = {}, {}, 0
        classes = [n for n in nodes if n.type == 'class_declaration']
        if len(classes) > 32: self.java_fault('同一段代码最多 32 个类。', self.root, 'LIMIT')
        if sum('public' in modifiers(n) for n in classes) > 1:
            self.java_fault('同一段代码只能有一个 public 主类，辅助类请去掉 public。', self.root)
        for n in classes:
            if n.parent != self.root: self.java_fault('暂不支持内部类，请将辅助类放在主类外。', n)
            name = text(field(n, 'name'))
            if name in self.java_classes: self.java_fault('类名重复：' + name, n)
            self.java_classes[name] = dict(node=n, fields={}, methods={}, constructors={}, values={}, state='new')
        for owner, cls in self.java_classes.items():
            for n in children(field(cls['node'], 'body')):
                if n.type == 'field_declaration':
                    for declaration in children(n):
                        if declaration.type != 'variable_declarator': continue
                        name = text(field(declaration, 'name'))
                        if name in cls['fields']: self.java_fault('字段重复：' + owner + '.' + name, n)
                        cls['fields'][name] = dict(owner=owner, node=n, type=field(n,'type'), declaration=declaration,
                                                  static='static' in modifiers(n), private='private' in modifiers(n))
                    if len(cls['fields']) > 64: self.java_fault('每个类最多 64 个字段。', n, 'LIMIT')
                elif n.type in ('method_declaration', 'constructor_declaration'):
                    constructor = n.type == 'constructor_declaration'
                    name = text(field(n, 'name'))
                    arity = len(children(field(n, 'parameters')))
                    table, lookup = (cls['constructors'], arity) if constructor else (cls['methods'], name)
                    if lookup in table: self.java_fault('同一类中的方法重载暂不支持；构造方法可按参数个数区分。', n)
                    if constructor and name != owner: self.java_fault('构造方法名称必须与类名相同。', n)
                    key = f'{owner}.<init>/{arity}' if constructor else (f'{owner}.{name}' if len(classes) > 1 else name)
                    member = dict(owner=owner, node=n, name=name, key=key, constructor=constructor,
                                  static=not constructor and 'static' in modifiers(n), private='private' in modifiers(n))
                    table[lookup] = member; self.java_methods[key] = member; self.functions[key] = n
                elif n.type != 'empty_statement': self.java_fault('暂不支持此类成员：' + n.type, n)
        for cls in self.java_classes.values():
            for name, member in cls['fields'].items():
                if member['static']: cls['values'][name] = self.java_default(member['type'], member['declaration'])

    def java_default(self, type_node, declaration=None):
        kind = text(type_node).replace(' ', '')
        if '[' in kind or field(declaration, 'dimensions'): return None
        return 0 if kind == 'int' else False if kind == 'boolean' else None

    def java_access(self, member, site):
        if member['private'] and self.frames[-1].get('java_class') != member['owner']:
            self.java_fault('不能从其他类访问 private 成员。', site, 'RUNTIME')

    @contextmanager
    def java_context(self, owner, receiver, name, site):
        if len(self.frames) >= 24: self.java_fault('调用栈达到 24 层上限。', site, 'LIMIT')
        frame = dict(name=name, java_class=owner, java_this=receiver, scopes=[{}], const=[set()], types=[{}])
        self.frames.append(frame)
        try: yield
        finally:
            if self.frames and self.frames[-1] is frame: self.frames.pop()

    def java_initialize(self, owner):
        cls = self.java_classes[owner]
        if cls['state'] != 'new': return
        cls['state'] = 'initializing'
        with self.java_context(owner, None, owner + '.<static>', cls['node']):
            for name, member in cls['fields'].items():
                value = field(member['declaration'], 'value')
                if member['static'] and value:
                    self.java_set(JavaClass(owner), name, self.expr(value), member['node'])
                    self.emit(member['node'], 'initialize')
        cls['state'] = 'ready'

    def java_member(self, target, name, site):
        if not isinstance(target, (JavaObject, JavaClass)):
            self.java_fault('访问字段需要类或非空对象。', site, 'RUNTIME')
        cls = self.java_classes[target.name]
        member = cls['fields'].get(name)
        if member is None: self.java_fault('没有字段：' + target.name + '.' + name, site, 'RUNTIME')
        self.java_access(member, site)
        if member['static']:
            self.java_initialize(target.name)
            return member, cls['values']
        if isinstance(target, JavaClass): self.java_fault('实例字段需要先用 new 创建对象。', site, 'RUNTIME')
        return member, target.values

    def java_get(self, target, name, site):
        _, values = self.java_member(target, name, site)
        return values[name]

    def java_set(self, target, name, value, site):
        member, values = self.java_member(target, name, site)
        self.typed(value, member['type'], member['declaration'], site)
        self.checked(value); values[name] = value
        return value

    def java_implicit_target(self, name):
        frame = self.frames[-1]; owner = frame.get('java_class')
        if owner and name in self.java_classes[owner]['fields']:
            return frame.get('java_this') or JavaClass(owner)
        return None

    def java_lookup(self, name, site):
        target = self.java_implicit_target(name)
        if target is not None: return True, self.java_get(target, name, site)
        if name in self.java_classes: return True, JavaClass(name)
        return False, None

    def java_receiver(self, node):
        return self.expr(node) if node is not None else (self.frames[-1].get('java_this') or JavaClass(self.frames[-1].get('java_class', '')))

    def java_builtin(self, name):
        if name not in ('System.out.print','System.out.println','Math.abs','Math.min','Math.max'): return False
        root = name.split('.')[0]
        return (root not in self.java_classes and self.java_implicit_target(root) is None
                and not any(root in scope for scope in self.frames[-1]['scopes']))

    def java_invoke(self, n):
        object_node = field(n, 'object'); name = text(field(n, 'name'))
        qualified = (text(object_node) + '.' if object_node else '') + name
        if self.java_builtin(qualified):
            return self.builtin(qualified, [self.expr(c) for c in children(field(n, 'arguments'))], n)
        target = self.java_receiver(object_node)
        if not isinstance(target, (JavaObject, JavaClass)) or target.name not in self.java_classes:
            self.java_fault('调用方法需要类或非空对象。', n, 'RUNTIME')
        member = self.java_classes[target.name]['methods'].get(name)
        if member is None: self.java_fault('没有方法：' + target.name + '.' + name, n, 'RUNTIME')
        self.java_access(member, n)
        if not member['static'] and isinstance(target, JavaClass): self.java_fault('实例方法需要先用 new 创建对象。', n, 'RUNTIME')
        args = [self.expr(c) for c in children(field(n, 'arguments'))]
        return self.call(member['key'], args, n, receiver=target if isinstance(target, JavaObject) and not member['static'] else None)

    def java_new(self, n):
        owner = text(field(n, 'type'))
        if owner not in self.java_classes: self.java_fault('只能创建这段代码中声明的类：' + owner, n)
        self.java_initialize(owner)
        cls = self.java_classes[owner]
        args = [self.expr(c) for c in children(field(n, 'arguments'))]
        constructor = cls['constructors'].get(len(args))
        if cls['constructors'] and constructor is None or not cls['constructors'] and args:
            self.java_fault('没有匹配参数个数的构造方法：' + owner, n, 'INPUT')
        if constructor: self.java_access(constructor, n)
        self.java_objects += 1
        if self.java_objects > 128: self.java_fault('一次运行最多创建 128 个对象。', n, 'LIMIT')
        obj = JavaObject(owner, self.java_objects)
        for name, member in cls['fields'].items():
            if not member['static']: obj.values[name] = self.java_default(member['type'], member['declaration'])
        with self.java_context(owner, obj, owner + '.<fields>', n):
            for name, member in cls['fields'].items():
                value = field(member['declaration'], 'value')
                if not member['static'] and value:
                    self.java_set(obj, name, self.expr(value), member['node']); self.emit(member['node'], 'initialize')
        if constructor: self.call(constructor['key'], args, n, receiver=obj)
        self.emit(n, 'new', obj)
        return obj

    def java_entry(self):
        requested = self.entry or 'main'
        if '.' in requested:
            owner, name = requested.split('.', 1)
            member = self.java_classes.get(owner, {}).get('methods', {}).get(name)
            matches = [member] if member else []
        else:
            matches = [cls['methods'][requested] for cls in self.java_classes.values() if requested in cls['methods']]
            if requested == 'main':
                preferred = [m for m in matches if 'public' in modifiers(self.java_classes[m['owner']]['node'])]
                if preferred: matches = preferred
        if not matches:
            self.java_fault('没有 main。请填写方法名或 类名.方法名。' if not self.entry else '找不到指定入口函数。', self.root, 'ENTRY_REQUIRED' if not self.entry else 'INPUT')
        if len(matches) > 1: self.java_fault('多个类含有同名入口，请填写 类名.方法名，例如 main.main。', self.root, 'ENTRY_REQUIRED')
        member = matches[0]
        if not member['static']: self.java_fault('入口必须是 static 方法；实例方法请在 main 中创建对象后调用。', member['node'], 'INPUT')
        self.entry = member['key']
        params = children(field(member['node'], 'parameters'))
        if member['name'] == 'main' and self.arguments == [] and len(params) == 1 and text(field(params[0], 'type')).replace(' ', '') == 'String[]':
            self.arguments = [[]]
        return self.call(self.entry, self.arguments, member['node'])
