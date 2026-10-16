"""Isolated-mode bootstrap: only add this read-only application directory."""
import sys
import os
from pathlib import Path
if os.name=='posix':
    import resource
    resource.setrlimit(resource.RLIMIT_AS,(256*1024*1024,256*1024*1024))
    resource.setrlimit(resource.RLIMIT_CPU,(3,3))
    resource.setrlimit(resource.RLIMIT_FSIZE,(0,0))
    resource.setrlimit(resource.RLIMIT_NOFILE,(32,32))
sys.path.insert(0,str(Path(__file__).resolve().parent))
import json
from engine import execute
try:
    request=json.loads(sys.stdin.buffer.read(65537))
    if not isinstance(request,dict):raise ValueError()
    print(json.dumps(execute(request),ensure_ascii=False,allow_nan=False))
except Exception:
    print(json.dumps({'status':'error','error':{'code':'INPUT','message':'请求格式或代码不受支持。','line':1},'steps':[]}))
