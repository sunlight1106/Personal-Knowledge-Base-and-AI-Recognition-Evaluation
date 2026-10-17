import unittest
from engine import execute,Engine


class ExecutionTests(unittest.TestCase):
    def run_code(self,source,language='auto',entry='',arguments=None):
        return execute(dict(source=source,language=language,entry=entry,arguments=arguments or []))
    def complete(self,source,language='auto',entry='',arguments=None):
        result=self.run_code(source,language,entry,arguments)
        self.assertEqual(result['status'],'completed',result.get('error'))
        return result
    def test_four_languages_have_real_equal_sum_and_counts(self):
        samples={
          'python':'def sum(a):\n    s=0\n    for i in range(len(a)):\n        s+=a[i]\n    return s',
          'c':'int sum(int a[], int n){int s=0; for(int i=0;i<n;i++){s+=a[i];} return s;}',
          'java':'static int sum(int[] a){int s=0; for(int i=0;i<a.length;i++){s+=a[i];} return s;}',
          'javascript':'function sum(a){let s=0;for(let i=0;i<a.length;i++){s+=a[i];}return s;}'
        }
        for lang,code in samples.items():
            with self.subTest(language=lang):
                result=self.complete(code,lang,'sum',[[3,1,2],3] if lang=='c' else [[3,1,2]])
                self.assertEqual(result['result'],6)
                self.assertEqual(sum(result['loopCounts'].values()),3)
                self.assertEqual(result['callCounts'],{'sum':1})
                self.assertTrue(any(s['frames'][-1]['variables'].get('s')==4 for s in result['steps']))
                self.assertEqual(result['complexity']['estimate'],'O(n)')
    def test_recursion_and_stack_snapshots(self):
        result=self.complete('def fact(n):\n    if n<=1:\n        return 1\n    return n*fact(n-1)','python','fact',[5])
        self.assertEqual(result['result'],120);self.assertEqual(result['callCounts']['fact'],5)
        self.assertEqual(max(len(s['frames']) for s in result['steps']),6)
        self.assertEqual(result['complexity']['estimate'],'unknown')
    def test_python_swap_and_array_mutation(self):
        r=self.complete('a=[3,1,2]\na[0],a[1]=a[1],a[0]\nprint(a)','python')
        self.assertEqual(r['output'],'[1, 3, 2]\n')
        self.assertEqual(r['steps'][0]['frames'][0]['variables']['a'],[3,1,2])
    def test_c_main_array_and_printf(self):
        r=self.complete('#include <stdio.h>\nint main(){int a[4]={3,1}; a[1]+=2; printf("%d %d\\n",a[1],a[3]);return 0;}','c')
        self.assertEqual(r['output'],'3 0\n')
    def test_java_main_and_new_array(self):
        r=self.complete('class Main { public static void main(String[] args){int[] a=new int[3]; a[2]=7; System.out.println(a[2]);}}','java')
        self.assertEqual(r['output'],'7\n')
    def test_pasted_java_main_replaces_sort_entry_and_arguments(self):
        code='''class test{
public static void main(String[]args){
int n = 8;
for(int i = 1; i <= n-1;i++){
for(int j = i+1;j <=n;j++){
if(i>j){
int temp = i;
i = j;
j = temp;
}
}
}
}
}'''
        self.assertEqual(self.run_code(code,'auto','sort',[[5,2,8,1]])['status'],'error')
        for entry in ('main',''):
            r=self.complete(code,'auto',entry,[])
            self.assertEqual(r['language'],'java')
            self.assertEqual(r['callCounts'],{'main':1})
            self.assertEqual(sum(r['loopCounts'].values()),35)
            self.assertEqual(r['output'],'')
            self.assertGreater(len(r['steps']),0)
            self.assertFalse(any('temp' in f['variables'] for s in r['steps'] for f in s['frames']))
    def test_scopes_and_continue_break(self):
        r=self.complete('function f(){let s=0;for(let i=0;i<8;i++){if(i===2){continue;} if(i===5){break;}s+=i;}return s;}','javascript','f')
        self.assertEqual(r['result'],8);self.assertNotIn('i',r['steps'][-1]['frames'][-1]['variables'])
    def test_negative_division_remainder(self):
        self.assertEqual(self.complete('int f(){return -7/3;}','c','f')['result'],-2)
        self.assertEqual(self.complete('static int f(){return -7%3;}','java','f')['result'],-1)
        self.assertEqual(self.complete('x=-7//3\nprint(x)','python')['output'],'-3\n')
    def test_short_circuit_and_print(self):
        r=self.complete('let a=0; if(false && (1/0)){a=9;} console.log(a);','javascript')
        self.assertEqual(r['output'],'0\n')
    def test_bounds_and_uninitialized(self):
        for code in ['int f(){int a[2];return a[4];}','int f(){int a;return a;}','int f(){int a[1]={1};return a[-1];}']:
            with self.subTest(code=code):self.assertEqual(self.run_code(code,'c','f')['status'],'error')
    def test_partial_trace_on_zero_division(self):
        r=self.run_code('a=4\nb=a/0','python')
        self.assertEqual(r['status'],'error');self.assertEqual(r['error']['line'],2);self.assertEqual(len(r['steps']),1)
    def test_missing_main_needs_explicit_entry(self):
        r=self.run_code('int add(int a,int b){return a+b;}','c')
        self.assertEqual(r['error']['code'],'ENTRY_REQUIRED')
    def test_resource_caps(self):
        for code in ['while True:\n    pass','def f():\n    return f()\nf()','a=2**1000000','a=list(range(100000000))']:
            with self.subTest(code=code):self.assertIn(self.run_code(code,'python')['status'],('error','limited'))
    def test_no_system_network_import_reflection_or_hidden_unsupported_code(self):
        codes=['import os\nos.system("whoami")','__import__("os").system("whoami")','open("/etc/passwd")',
               'x=(1).__class__','def unused():\n    import socket\nx=1','eval("1+1")']
        for code in codes:
            with self.subTest(code=code):self.assertEqual(self.run_code(code,'python')['status'],'error')
        self.assertEqual(self.run_code('int main(){system("whoami");}','c')['status'],'error')
        self.assertEqual(self.run_code('class Main{static void main(String[] args){Runtime.getRuntime().exec("x");}}','java')['status'],'error')
    def test_string_is_data_not_code(self):
        r=self.complete('x="__import__(\\"os\\").system(\\"bad\\")"\nprint(x)','python')
        self.assertIn('__import__',r['output'])
    def test_ambiguous_language_and_bad_arguments(self):
        self.assertEqual(self.run_code('x=1')['error']['code'],'LANGUAGE_REQUIRED')
        self.assertEqual(self.run_code('def f(a):\n    return a','python','f',[])['status'],'error')
        self.assertEqual(self.run_code('def f(a):\n    return a','python','f',[{'x':1}])['status'],'error')
    def test_overflow_and_large_request(self):
        self.assertEqual(self.run_code('int f(){return 2147483647+1;}','c','f')['status'],'limited')
        self.assertEqual(self.run_code('x=1\n'*401,'python')['error']['code'],'LIMIT')
    def test_while_complexity_never_guessed(self):
        r=self.complete('x=1\nwhile x<10:\n    x*=2','python')
        self.assertEqual(r['complexity']['estimate'],'unknown')
        r=self.run_code('int f(int n){for(int i=0;i<n;i++){i--;}return 0;}','c','f',[3])
        self.assertEqual(r['complexity']['estimate'],'unknown')
    def test_const_shadow_scope_and_uninitialized_array(self):
        r=self.complete('let x=1; {const x=2;} x=3; console.log(x);','javascript')
        self.assertEqual(r['output'],'3\n')
        self.assertEqual(self.run_code('int f(){int a[2];if(a[0]){return 1;}return 0;}','c','f')['status'],'error')
    def test_mismatched_declared_types_are_rejected(self):
        self.assertEqual(self.run_code('static int f(int x){return x;}','java','f',['text'])['status'],'error')
        self.assertEqual(self.run_code('static int f(){int x="text";return 1;}','java','f')['status'],'error')
        self.assertEqual(self.run_code('int f(){return "text";}','c','f')['status'],'error')
        self.assertEqual(self.run_code('int f(){int a=1;a="bad";return 0;}','c','f')['status'],'error')
        self.assertEqual(self.run_code('static int f(){int[] a={1};a[0]="bad";return 0;}','java','f')['status'],'error')
    def test_recursive_limit_inside_scoped_block_preserves_limit_reason(self):
        result=self.run_code('static int f(int n){if(n>0){return f(n+1);}return 0;}','java','f',[1])
        self.assertEqual(result['status'],'limited');self.assertEqual(result['error']['code'],'LIMIT')
    def test_alias_preserved_without_mutating_prior_snapshots(self):
        r=self.complete('a=[1,2]\nb=a\nb[0]=8','python')
        self.assertEqual(r['steps'][0]['frames'][0]['variables']['a'],[1,2])
        self.assertEqual(r['steps'][-1]['frames'][0]['variables']['a'],[8,2])
    def test_every_sort_sample(self):
        # These are shared with the website through a generated fixture in CI.
        import json
        from pathlib import Path
        fixture=Path(__file__).with_name('samples.json')
        if not fixture.exists():self.skipTest('frontend sample fixture is generated by CI')
        for lang,sample in json.loads(fixture.read_text()).items():
            r=self.complete(sample['source'],lang,sample['entry'],json.loads(sample['args']))
            self.assertEqual(r['steps'][-1]['frames'][-1]['variables']['a'],[1,2,5,8],lang)
    def test_all_auto_language_detection(self):
        for lang,code in [('python','print(1)'),('c','int main(){return 0;}'),('java','class Main{static int main(){return 0;}}'),('javascript','console.log(1);')]:
            # Java main without String[] is a snippet, explicitly select it with its declared parameters.
            r=self.run_code(code)
            self.assertEqual(r['language'],lang)

if __name__=='__main__':unittest.main()
