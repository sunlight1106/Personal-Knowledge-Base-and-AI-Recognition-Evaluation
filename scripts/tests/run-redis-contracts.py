"""Exercise real Redis delivery against a disposable local container."""
import os
from pathlib import Path
import subprocess
import time
import uuid
root=Path(__file__).resolve().parents[2];name='pkb-redis-contracts-'+uuid.uuid4().hex[:12]
def docker(*args):return subprocess.run(['docker',*args],check=True,capture_output=True,text=True,timeout=120).stdout.strip()
try:
    docker('run','--rm','-d','--name',name,'--memory=96m','-p','127.0.0.1::6379','redis:7.4-alpine')
    port=docker('port',name,'6379/tcp').rsplit(':',1)[1]
    for i in range(30):
        try:
            if docker('exec',name,'redis-cli','ping')=='PONG':break
        except subprocess.CalledProcessError:pass
        time.sleep(1)
    env=os.environ.copy();env['REDIS_TEST_PORT']=port
    command=['cmd.exe','/d','/c',str(root/'mvnw.cmd')] if os.name=='nt' else [str(root/'mvnw')]
    result=subprocess.run(command+['-B','-Dtest=RedisDeliveryIntegrationTest','test'],cwd=root,env=env)
finally:
    subprocess.run(['docker','rm','-f',name],capture_output=True,timeout=120)
raise SystemExit(result.returncode)
