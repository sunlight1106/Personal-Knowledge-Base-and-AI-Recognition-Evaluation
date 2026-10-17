import unittest
from engine import execute
from analysis import Poly, summation


class AnalysisTests(unittest.TestCase):
    def run_code(self,code,language='python',entry='f',args=None):
        return execute(dict(source=code,language=language,entry=entry,arguments=args or []))

    def derived(self,code,language='python',entry='f',args=None):
        result=self.run_code(code,language,entry,args)
        self.assertEqual(result['status'],'completed',result.get('error'))
        self.assertEqual(result['analysis']['status'],'derived',result['analysis'])
        for record in result['analysis']['records']:
            self.assertEqual(record['predicted'],record['measured'],record)
        return result['analysis']

    def test_triangular_user_java_and_unreachable_branch(self):
        code='class test{public static void main(String[]args){int n=8;for(int i=1;i<=n-1;i++){for(int j=i+1;j<=n;j++){if(i>j){int temp=i;i=j;j=temp;}}}}}'
        a=self.derived(code,'java','main')
        loops=[r for r in a['records'] if r['kind']=='loop']
        self.assertEqual([r['predicted'] for r in loops],[7,28])
        self.assertEqual(loops[-1]['formula'],'n(n − 1)/2')
        self.assertEqual(loops[-1]['exampleTerms'],[7,6,5,4,3,2,1])
        self.assertEqual(a['complexity'],'Θ(n^2)')
        self.assertEqual(a['size']['origin'],'literal')
        self.assertEqual(a['notes'][0]['kind'],'always-false')

    def test_triangles_across_sizes_and_languages(self):
        codes={
            'python':'def f(n):\n s=0\n for i in range(1,n):\n  for j in range(i+1,n+1):\n   s+=1\n return s',
            'c':'int f(int n){int s=0;for(int i=1;i<n;i++){for(int j=i+1;j<=n;j++){s+=1;}}return s;}',
            'java':'static int f(int n){int s=0;for(int i=1;i<n;i++){for(int j=i+1;j<=n;j++){s+=1;}}return s;}',
            'javascript':'function f(n){let s=0;for(let i=1;i<n;i++){for(let j=i+1;j<=n;j++){s+=1;}}return s;}'
        }
        for language,code in codes.items():
            for n in (1,2,3,8,12):
                with self.subTest(language=language,n=n):
                    a=self.derived(code,language,args=[n])
                    inner=[r for r in a['records'] if r['kind']=='loop'][-1]
                    self.assertEqual(inner['predicted'],n*(n-1)//2)
                    self.assertEqual(inner['formula'],'n(n − 1)/2')

    def test_rectangular_cubic_and_sequential(self):
        for depth in (1,2,3,4):
            code='def f(n):\n s=0\n'
            for level in range(depth):code+=' '*(level+1)+f'for {"ijkl"[level]} in range(n):\n'
            code+=' '*(depth+1)+'s+=1\n return s'
            a=self.derived(code,args=[3])
            self.assertEqual(a['degree'],depth)
            self.assertEqual([r for r in a['records'] if r['kind']=='loop'][-1]['predicted'],3**depth)
        a=self.derived('def f(n):\n for i in range(n):\n  pass\n for j in range(n):\n  pass',args=[5])
        self.assertEqual(a['degree'],1)

    def test_array_length_alias_and_sort_samples(self):
        import json
        from pathlib import Path
        for lang,sample in json.loads(Path(__file__).with_name('samples.json').read_text()).items():
            with self.subTest(lang=lang):
                a=self.derived(sample['source'],lang,sample['entry'],json.loads(sample['args']))
                self.assertEqual(a['degree'],2)
                self.assertEqual([r for r in a['records'] if r['kind']=='loop'][-1]['predicted'],6)

    def test_descending_and_inclusive(self):
        for code,lang in [('def f(n):\n for i in range(n,0,-1):\n  pass','python'),('int f(int n){for(int i=n;i>0;i--){}return 0;}','c')]:
            a=self.derived(code,lang,args=[5]); self.assertEqual(a['records'][0]['formula'],'n')
        a=self.derived('def f(n):\n for i in range(n+1):\n  for j in range(i):\n   pass',args=[5])
        self.assertEqual([r for r in a['records'] if r['kind']=='loop'][-1]['formula'],'n(n + 1)/2')

    def test_no_false_formula_for_mutation_break_recursion_or_dynamic_bound(self):
        cases=[
            'def f(n):\n for i in range(n):\n  if i>1:\n   break',
            'def f(n):\n while n>0:\n  n-=1',
            'def f(n):\n if n<1:\n  return 1\n return f(n-1)',
            'def f(n):\n for i in range(n):\n  n-=1',
            'def f(n):\n for i in range(n):\n  for j in range(n*n):\n   pass',
            'def f(n):\n for i in range(0,n,2):\n  pass',
            'def f(n):\n if n<2:\n  return 0\n for i in range(n):\n  pass',
        ]
        for code in cases:
            with self.subTest(code=code):self.assertEqual(self.run_code(code,args=[3])['analysis']['status'],'unavailable')
        r=self.run_code('def f(n,m):\n for i in range(n):\n  for j in range(m):\n   pass',args=[3,4])
        self.assertEqual(r['analysis']['reason'],'MULTIPLE_SIZES')
        r=self.run_code('def f(n,a):\n for i in range(n):\n  for j in range(len(a)):\n   pass',args=[3,[1,2,3]])
        self.assertEqual(r['analysis']['reason'],'MULTIPLE_SIZES')
        r=self.run_code('def f(a):\n a=[1,2,3]\n for i in range(len(a)):\n  pass',args=[[1,2,3]])
        self.assertEqual(r['analysis']['status'],'unavailable')

    def test_partial_trace_is_not_reported_as_complete_measurement(self):
        r=self.run_code('def f(n):\n for i in range(n):\n  for j in range(n):\n   pass',args=[80])
        self.assertEqual(r['status'],'limited')
        self.assertEqual(r['analysis']['status'],'derived')
        self.assertTrue(r['analysis']['partial'])
        self.assertTrue(all(x['verification']=='partial' for x in r['analysis']['records']))

    def test_polynomial_sum_is_exact_not_curve_fit(self):
        n,i=Poly.var('n'),Poly.var('i')
        p=summation(i,'i',Poly.number(1),n+1)
        for size in (0,1,8,100,512):self.assertEqual(p.at(size),size*(size+1)//2)
        self.assertEqual(p.display(),'n(n + 1)/2')

    def test_shifted_bounds_and_empty_iterations(self):
        shapes=[('0','n','0','i',lambda n:n*(n-1)//2),
                ('0','n','i','n',lambda n:n*(n+1)//2),
                ('0','n','0','n-i',lambda n:n*(n+1)//2),
                ('1','n+1','1','i+1',lambda n:n*(n+1)//2),
                ('0','n','2','n+2',lambda n:n*n)]
        for start,end,inner_start,inner_end,expected in shapes:
            for n in (0,1,2,5,9):
                with self.subTest(bounds=(start,end,inner_start,inner_end),n=n):
                    code=f'def f(n):\n for i in range({start},{end}):\n  for j in range({inner_start},{inner_end}):\n   pass'
                    a=self.derived(code,args=[n])
                    self.assertEqual([r for r in a['records'] if r['kind']=='loop'][-1]['predicted'],expected(n))

    def test_analysis_limits_do_not_break_execution(self):
        name='x'*65
        r=self.run_code(f'def f(n):\n {name}=1\n for i in range(n):\n  pass',args=[3])
        self.assertEqual(r['status'],'completed')
        self.assertEqual(r['analysis']['reason'],'ANALYSIS_LIMIT')

    def test_ordinary_input_named_size_and_literal_bounds(self):
        a=self.derived('def f(size):\n for i in range(size):\n  pass',args=[6])
        self.assertEqual(a['size']['name'],'size');self.assertEqual(a['records'][0]['formula'],'n')
        a=self.derived('def f():\n for i in range(5):\n  pass')
        self.assertIsNone(a['size']);self.assertEqual(a['complexity'],'Θ(1)')


if __name__=='__main__':unittest.main()
