"""Bounded local acceptance check. Reads only the configured test account's data.

Example: python scripts/performance/operational_acceptance.py --url http://127.0.0.1:4273 --credentials .env
The report contains status/latency only, never response bodies, passwords or tokens.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import math
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import urllib.parse


def call(base,path,token=None,method='GET',body=None,content_type='application/json'):
    headers={'Content-Type':content_type}
    if token:headers['Authorization']='Bearer '+token
    data=body if isinstance(body,bytes) else None if body is None else json.dumps(body).encode()
    start=time.monotonic()
    try:
        with urllib.request.urlopen(urllib.request.Request(base+'/api/v1'+path,data=data,headers=headers,method=method),timeout=20) as response:
            raw=response.read(2_000_000);status=response.status
    except urllib.error.HTTPError as error:status=error.code;raw=error.read(20000)
    return status,(time.monotonic()-start)*1000,raw


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--url',default='http://127.0.0.1:4273');parser.add_argument('--credentials',type=Path);parser.add_argument('--concurrency',type=int,default=8);parser.add_argument('--requests',type=int,default=48);parser.add_argument('--restart',action='store_true');parser.add_argument('--output',type=Path,default=Path('.runtime/operational-acceptance.json'));args=parser.parse_args()
    if not 1<=args.concurrency<=32 or not 8<=args.requests<=256:parser.error('Use 1–32 workers and 8–256 requests')
    if urllib.parse.urlsplit(args.url).hostname not in ('localhost','127.0.0.1','::1'):parser.error('This script is restricted to localhost')
    env=dict(os.environ)
    if args.credentials:
        for line in args.credentials.read_text(encoding='utf-8-sig').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key,value=line.split('=',1)
                if key in ('BOOTSTRAP_TEST_USERNAME','BOOTSTRAP_TEST_PASSWORD'):env[key]=value.strip().strip('"').strip("'")
    user=env.get('BOOTSTRAP_TEST_USERNAME','test');password=env.get('BOOTSTRAP_TEST_PASSWORD','')
    if not password:parser.error('Set test credentials locally; do not pass passwords on the command line')
    status,_,body=call(args.url,'/auth/login',method='POST',body={'username':user,'password':password})
    if status!=200:raise RuntimeError('Test login failed; credentials were not changed')
    token=json.loads(body)['data']['token'];checks={}
    checks['unauthenticatedDenied']=call(args.url,'/learning/plan')[0]==401
    checks['missingJobDoesNotLeak']=call(args.url,'/workspace-jobs/00000000-0000-0000-0000-000000000000',token)[0]==404
    checks['invalidPlanRejected']=call(args.url,'/learning/plan',token,'PUT',{'minutes':0,'spreadDays':7,'revision':0})[0]==400
    paths=['/learning/plan','/learning/statistics?days=30','/workspace-jobs','/research/search?q=overfit&sort=natural']
    def measure(index):
        try:code,elapsed,_=call(args.url,paths[index%len(paths)],token);return code,elapsed
        except Exception:return 0,20000
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:results=list(pool.map(measure,range(args.requests)))
    durations=sorted(ms for _,ms in results);checks['parallelRequestsSucceeded']=all(code==200 for code,_ in results)
    # Reject an oversized PDF before allocating an asynchronous document job.
    boundary='pkb_acceptance_boundary';payload=(f'--{boundary}\r\nContent-Disposition: form-data; name="requestId"\r\n\r\n00000000-0000-0000-0000-000000000001\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="oversized.pdf"\r\nContent-Type: application/pdf\r\n\r\n').encode()+b'x'*(10*1024*1024+1)+f'\r\n--{boundary}--\r\n'.encode()
    checks['oversizedUploadRejected']=call(args.url,'/workspace-jobs/documents',token,'POST',payload,'multipart/form-data; boundary='+boundary)[0] in (400,413)
    if args.restart:
        root=Path(__file__).resolve().parents[2]
        subprocess.run(['docker','compose','restart','backend'],cwd=root,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        for _ in range(90):
            try:
                if call(args.url,'/auth/me',token)[0]==200:checks['sessionSurvivesRestart']=True;break
            except Exception:pass
            time.sleep(2)
        else:checks['sessionSurvivesRestart']=False
    result={'checks':checks,'requests':len(results),'concurrency':args.concurrency,'p50Ms':round(durations[len(durations)//2]),'p95Ms':round(durations[math.ceil(len(durations)*.95)-1]),'maxMs':round(max(durations)),'passed':all(checks.values())}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2));print(json.dumps(result))
    call(args.url,'/account/logout',token,'POST')
    if not result['passed']:raise SystemExit(1)


if __name__=='__main__':
    try:main()
    except Exception as exc:print('Acceptance check failed: '+type(exc).__name__+'; private response content suppressed.');raise SystemExit(1)
