"""Switch the API between healthy local image slots; retain the previous slot for rollback.

Only additive/backward-compatible schema changes are suitable. Frontend and destructive
schema upgrades still use the normal maintenance deployment. No image is downloaded.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/backup'))
from scheduled_backup import acquire_deployment_guard
STATE=ROOT/'.runtime/releases/state.json'
OVERLAY=ROOT/'.runtime/releases/slots.json'

def run(args,**kwargs):
    result=subprocess.run(args,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE,**kwargs)
    if result.returncode:raise RuntimeError('Deployment step failed: '+' '.join(args[:3]))
    return result.stdout

def compose(*args):return run(['docker','compose','-f','compose.yaml','-f',str(OVERLAY),*args])

def switch(target):
    if target not in ('backend','backend-blue','backend-green'):raise ValueError('Invalid slot')
    content=f'set $api_upstream {target}:8080;\n'.encode()
    run(['docker','compose','exec','-T','frontend','sh','-c','cat > /etc/nginx/snippets/api-upstream.conf'],input=content)
    run(['docker','compose','exec','-T','frontend','nginx','-t'])
    run(['docker','compose','exec','-T','frontend','nginx','-s','reload'])

def healthy(service):
    return json.loads(compose('exec','-T',service,'curl','--max-time','5','-fsS','http://127.0.0.1:8080/actuator/health/readiness')).get('status')=='UP'

def deploy(image,compatible,rollback=False):
    STATE.parent.mkdir(parents=True,exist_ok=True)
    (ROOT/'.runtime').mkdir(exist_ok=True)
    guard=acquire_deployment_guard(ROOT)
    try:
        state=json.loads(STATE.read_text()) if STATE.exists() else {'active':'backend','previous':None}
        previous=state['active']
        if rollback:
            target=state.get('previous')
            if not target:raise ValueError('No previous healthy slot recorded')
            if not healthy(target):raise RuntimeError('Previous slot is not ready; routing unchanged')
        else:
            if not compatible:raise ValueError('Review migration compatibility and pass --compatible-schema')
            if not image or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._/@:+-]{0,250}',image):raise ValueError('Specify an installed image')
            image_id=run(['docker','image','inspect','--format','{{.Id}}',image]).decode().strip()
            target='backend-green' if previous=='backend-blue' else 'backend-blue'
            overlay=json.loads(OVERLAY.read_text()) if OVERLAY.exists() else {'services':{}}
            overlay['services'][target]={'extends':{'file':str(ROOT/'compose.yaml'),'service':'backend'},'image':image_id,'environment':{'APP_BOOTSTRAP_ENABLED':'false'},'profiles':['rollout']}
            OVERLAY.write_text(json.dumps(overlay,indent=2),encoding='utf-8')
            compose('up','-d','--no-deps','--no-build','--wait','--wait-timeout','240',target)
            if not healthy(target):raise RuntimeError('Candidate not ready; routing unchanged')
        try:
            switch(target)
            if not healthy(target):raise RuntimeError('Candidate became unavailable')
        except Exception:
            switch(previous)
            raise
        temp=STATE.with_suffix('.tmp');temp.write_text(json.dumps({'active':target,'previous':previous,'switchedAt':time.time()}),encoding='utf-8');temp.replace(STATE)
        print('API routing switched to '+target+'. Previous slot retained; use --rollback if needed.')
    finally:guard.close()

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--image');p.add_argument('--compatible-schema',action='store_true');p.add_argument('--rollback',action='store_true');a=p.parse_args()
    deploy(a.image,a.compatible_schema,a.rollback)
