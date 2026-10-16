"""Internal-only HTTP adapter; each run gets a disposable bounded subprocess."""
import hmac
import json
import os
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

TOKEN=os.environ.get('CODE_LAB_SERVICE_TOKEN','')
SLOTS=threading.BoundedSemaphore(2)
ROOT=Path(__file__).resolve().parent


def run_process(payload):
    # No application secrets or provider credentials are inherited by the child.
    env={'PATH':os.environ.get('PATH',''),'PYTHONDONTWRITEBYTECODE':'1','PYTHONIOENCODING':'utf-8'}
    if os.name=='nt':env['SYSTEMROOT']=os.environ.get('SYSTEMROOT','C:\\Windows')
    try:
        completed=subprocess.run([sys.executable,'-I',str(ROOT/'runner.py')],input=payload,
            stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=5,cwd=ROOT,env=env)
        if completed.returncode!=0 or len(completed.stdout)>1800000:raise ValueError()
        result=json.loads(completed.stdout)
        if not isinstance(result,dict):raise ValueError()
        return result
    except (subprocess.TimeoutExpired,ValueError):
        return {'status':'limited','steps':[],'error':{'code':'LIMIT','message':'执行超时或超过资源上限，请缩小输入。','line':1}}


class Handler(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.0'
    def log_message(self,*args):pass  # Never log source, authorization or arguments.
    def setup(self):
        super().setup();self.connection.settimeout(8)
    def send_json(self,status,data):
        payload=json.dumps(data,ensure_ascii=False,allow_nan=False).encode()
        self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8')
        self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(payload)));self.end_headers()
        try:self.wfile.write(payload)
        except (BrokenPipeError,ConnectionResetError):pass
    def do_GET(self):
        if self.path=='/health':self.send_json(200,{'status':'up','configured':len(TOKEN)>=32})
        else:self.send_json(404,{'error':'not found'})
    def do_POST(self):
        if self.path!='/execute':return self.send_json(404,{'error':'not found'})
        if len(TOKEN)<32 or not hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+TOKEN):
            return self.send_json(401,{'error':'unauthorized'})
        try:size=int(self.headers.get('Content-Length','0'))
        except ValueError:return self.send_json(400,{'error':'invalid size'})
        if not 1<=size<=65536:return self.send_json(413,{'error':'request too large'})
        if not SLOTS.acquire(blocking=False):return self.send_json(429,{'error':'busy'})
        try:
            payload=self.rfile.read(size)
            if len(payload)!=size: return self.send_json(400,{'error':'incomplete request'})
            self.send_json(200,run_process(payload))
        except (OSError,ValueError):self.send_json(400,{'error':'invalid request'})
        finally:SLOTS.release()


if __name__=='__main__':
    if len(TOKEN)<32:raise SystemExit('CODE_LAB_SERVICE_TOKEN must contain at least 32 characters')
    ThreadingHTTPServer(('0.0.0.0',8091),Handler).serve_forever()
