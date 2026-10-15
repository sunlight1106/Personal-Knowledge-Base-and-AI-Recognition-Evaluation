"""Check/install immutable public image releases, without compiling locally."""
import argparse, hashlib, json, os, re, secrets, shutil, subprocess, sys, urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPO = 'sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation'
COMPONENTS = ('backend', 'frontend', 'training', 'minio')
SECRET_KEYS = ('BOOTSTRAP_ADMIN_PASSWORD','MYSQL_PASSWORD','MYSQL_ROOT_PASSWORD','MINIO_ROOT_PASSWORD','JWT_SECRET','CREDENTIAL_MASTER_KEY','TRAINING_SERVICE_TOKEN')

def run(args, env=None):
    result = subprocess.run(args, cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('Command failed: '+ ' '.join(args[:3]) + '. Inspect local Docker logs; private command output is suppressed.')
    return result.stdout.decode('utf-8', errors='replace')

def manifest(text):
    result = {}
    for line in text.splitlines():
        match = re.fullmatch(r'([a-f0-9]{64})  ((?:backend|frontend|training|minio)\.tar\.gz)', line)
        if not match or match[2] in result: raise ValueError('Invalid release manifest')
        result[match[2]] = match[1]
    if set(result) != {x+'.tar.gz' for x in COMPONENTS}: raise ValueError('Incomplete image release')
    return result

def checksum(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def download(url,path,expected):
    if path.is_file() and checksum(path)==expected:return
    partial=path.with_suffix('.partial')
    try:
        with urllib.request.urlopen(url,timeout=90) as response,partial.open('wb') as output:
            length=0
            while block:=response.read(1024*1024):
                length+=len(block)
                if length>2*1024**3:raise ValueError('Release asset exceeds size limit')
                output.write(block)
        if checksum(partial)!=expected:raise ValueError('Image checksum mismatch')
        partial.replace(path)
    finally:
        if partial.exists():partial.unlink()

def configure(port):
    path=ROOT/'.env'
    if path.exists():return
    text=(ROOT/'.env.example').read_text(encoding='utf-8')
    for key in SECRET_KEYS:
        text,count=re.subn(r'^'+key+r'=.*$',lambda m:key+'='+secrets.token_hex(32),text,flags=re.M)
        if count!=1:raise ValueError('Unexpected configuration template')
    text=re.sub(r'^WEB_PORT=.*$',f'WEB_PORT={port}',text,flags=re.M)
    fd=os.open(path,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
    with os.fdopen(fd,'w',encoding='utf-8') as output:output.write(text)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--version',required=True,help='sha- followed by the 40-character commit hash')
    p.add_argument('--apply',action='store_true',help='Install after checking; otherwise only validate release availability')
    p.add_argument('--port',type=int,default=4173)
    args=p.parse_args()
    if not re.fullmatch(r'sha-[a-f0-9]{40}',args.version) or not 1024<=args.port<=65535:p.error('Invalid immutable version or port')
    base=f'https://github.com/{REPO}/releases/download/{args.version}'
    with urllib.request.urlopen(base+'/SHA256SUMS',timeout=30) as response:
        checks=manifest(response.read(4097).decode())
    info=json.loads(run(['docker','info','--format','{{json .}}']))
    if info.get('OSType')!='linux' or info.get('Architecture') not in ('x86_64','amd64'):raise ValueError('This release requires Linux amd64 containers')
    run(['docker','compose','version'])
    print('Release available: '+args.version+'; four checksummed images. No source compilation required.')
    if not args.apply:return
    from source_manifest import build
    with urllib.request.urlopen(base+'/SOURCE.json',timeout=30) as response:
        expected_sources=json.loads(response.read(16000))
    if build()!=expected_sources:raise ValueError('Deployment source does not match this release. Download its matching source archive first.')
    # Require matching source and migrations instead of pairing arbitrary Compose files with a binary.
    if (ROOT/'.git').exists() and run(['git','rev-parse','HEAD']).strip()!=args.version[4:]:raise ValueError('Check out the matching source version before installing its images')
    runtime=ROOT/'.runtime';runtime.mkdir(exist_ok=True)
    sys.path.insert(0,str(ROOT/'scripts/backup'))
    from scheduled_backup import acquire_deployment_guard
    with acquire_deployment_guard(ROOT):
        destination=runtime/'releases'/args.version;destination.mkdir(parents=True,exist_ok=True)
        if shutil.disk_usage(destination).free<8*1024**3:raise ValueError('At least 8 GB free space is needed for download and image import')
        env={**os.environ,'PKB_RELEASE_TAG':args.version}
        compose=['docker','compose','-f','compose.yaml','-f','compose.images.yaml']
        existing=run(['docker','compose','ps','-aq']).split() if (ROOT/'.env').exists() else []
        project=os.environ.get('COMPOSE_PROJECT_NAME','robust-vision')
        volumes=run(['docker','volume','ls','-q','--filter','label=com.docker.compose.project='+project]).split()
        if volumes and not existing:raise ValueError('Existing data volumes have no complete installation. Restore the original .env and containers before updating; no credentials will be regenerated.')
        # Pull/import all images before stopping services or asking for a backup password.
        for name in COMPONENTS:
            archive=destination/(name+'.tar.gz');print('Downloading/checking '+name+'...')
            download(base+'/'+archive.name,archive,checks[archive.name]);run(['docker','load','--input',str(archive)])
        configure(args.port)
        run(compose+['config','--quiet'],env)
        previous=[]
        for container in existing:
            previous.append(json.loads(run(['docker','inspect','--format','{{json .}}',container])))
        backup=None
        if existing:
            import compose_backup
            import cryptography
            backup=runtime/'backups'/('before-update-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'.pkb')
            backup.parent.mkdir(exist_ok=True)
            password=compose_backup.password(True)
            compose_backup.snapshot(ROOT,backup,password)
            compose_backup.inspect_archive(backup,password,ROOT,None)
        report={'version':args.version,'startedAt':datetime.now(timezone.utc).isoformat(),'backup':str(backup) if backup else None,
                'previousImages':{c['Name']:c['Image'] for c in previous},'status':'installing'}
        state=runtime/'release-status.json';state.write_text(json.dumps(report,indent=2),encoding='utf-8')
        try:
            run(compose+['up','-d','--no-build','--wait','--wait-timeout','600'],env)
            report['status']='ready';print('Release is healthy. Open the WEB_PORT address from your local .env.')
        except Exception:
            report['status']='failed'
            print('Update failed. Existing volumes are retained. Use the recorded encrypted backup and RELEASE_INSTALL.md; do not downgrade a migrated database in place.')
            raise
        finally:state.write_text(json.dumps(report,indent=2),encoding='utf-8')

if __name__=='__main__':
    try:main()
    except KeyboardInterrupt:raise SystemExit(130)
    except Exception as e:print(f'Install stopped: {type(e).__name__}: {e}');raise SystemExit(1)
