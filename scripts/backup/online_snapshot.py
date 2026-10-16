"""Encrypted online database + immutable-object snapshot. Training checkpoints use full backups.

Permanent deletion/cleanup pauses while reads, edits and uploads continue. Restore only
to a new named Compose project. Never restores over the current installation.
"""
import argparse
from datetime import datetime,timezone
import hashlib
import json
import os
from pathlib import Path,PurePosixPath
import re
import subprocess
import tempfile
import time
import urllib.parse
import uuid
import zipfile
from functools import lru_cache
from compose_backup import crypt,password,digest,deployment_images

ROOT=Path(__file__).resolve().parents[2]

def run(args,*,output=None,data=None,cwd=ROOT):
    r=subprocess.run(args,cwd=cwd,input=data,stdout=output or subprocess.PIPE,stderr=subprocess.PIPE,timeout=1800)
    if r.returncode:raise RuntimeError('Snapshot command failed; private diagnostics withheld: '+' '.join(args[:3]))
    return r.stdout

def sql(statement,prefix=None):
    return run((prefix or ['docker','compose'])+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE"'],data=statement.encode())

def wait_for_database(prefix,timeout=240):
    # mysqladmin ping also succeeds for an Access Denied response, including
    # the temporary initialization server. Require a real authenticated query.
    deadline=time.monotonic()+timeout
    while True:
        try:
            if sql('SELECT 1',prefix).strip()==b'1':return
        except RuntimeError:pass
        if time.monotonic()>=deadline:raise RuntimeError('Restored database did not finish authenticated initialization')
        time.sleep(2)

def dump_coordinate(path):
    # mysqldump writes the coordinate in its header, before any table rows.
    # Do not load a potentially multi-gigabyte SQL snapshot into memory.
    with path.open(encoding='utf-8') as stream:
        for _ in range(1000):
            line=stream.readline()
            if not line:break
            match=re.search(r"SOURCE_LOG_FILE='([^']+)', SOURCE_LOG_POS=(\d+)",line)
            if match:return {'file':match[1],'position':int(match[2])}
    raise RuntimeError('Database binary log coordinate is missing')

def import_sql(path,prefix):
    with path.open('rb') as source:
        result=subprocess.run(prefix+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'],cwd=ROOT,stdin=source,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,timeout=1800)
    if result.returncode:raise RuntimeError('Snapshot SQL import failed; restore into a fresh project before retrying')

def key_path(key):
    path=PurePosixPath(key)
    if not key or path.is_absolute() or any(x in ('..','.') for x in path.parts) or '\\' in key or '\x00' in key:raise ValueError('Unsafe object key')
    return urllib.parse.quote(key,safe='/')

def object_command(key,prefix,upload=False):
    command='curl --fail --silent --show-error --max-time 120 --aws-sigv4 aws:amz:us-east-1:s3 --user "$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD" '
    # Buffer the piped body so curl sends a known Content-Length. Generic HTTP
    # chunked uploads from --upload-file - are not accepted by S3 PutObject.
    if upload:command+='-X PUT --data-binary @- '
    command+='"http://127.0.0.1:9000/$1/$2"'
    return prefix+['exec','-T','minio','sh','-c',command,'snapshot',bucket(tuple(prefix)),key_path(key)]

@lru_cache(maxsize=8)
def bucket(prefix):
    config=json.loads(run(list(prefix)+['config','--format','json']))
    value=config['services']['backend']['environment'].get('S3_BUCKET','personal-platform')
    if not re.fullmatch(r'[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]',value):raise ValueError('Invalid bucket')
    return value

def backup(destination,secret):
    from scheduled_backup import acquire_deployment_guard
    (ROOT/'.runtime').mkdir(exist_ok=True)
    with acquire_deployment_guard(ROOT):
        _backup(destination,secret)

def _backup(destination,secret):
    destination=destination.resolve()
    if destination.exists():raise ValueError('Archive already exists')
    destination.parent.mkdir(parents=True,exist_ok=True)
    token=uuid.uuid4().hex
    acquired=sql("UPDATE runtime_operation_lock SET token='%s',expires_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 2 HOUR) WHERE name='object-snapshot' AND expires_at<=UTC_TIMESTAMP(); SELECT ROW_COUNT();"%token).strip()
    if acquired!=b'1':raise RuntimeError('Another online snapshot holds the deletion guard')
    prefix=['docker','compose']
    try:
      with tempfile.TemporaryDirectory(prefix='pkb-online-',dir=destination.parent) as temporary:
        stage=Path(temporary);os.chmod(stage,0o700)
        with (stage/'database.sql').open('wb') as output:
            run(prefix+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --quick --source-data=2 --set-gtid-purged=OFF --hex-blob --routines --events --triggers --no-tablespaces "$MYSQL_DATABASE"'],output=output)
        keys=[line for line in sql('SELECT DISTINCT storage_path FROM file_asset ORDER BY storage_path').decode().splitlines() if line]
        source=sql('SELECT @@server_uuid,DATABASE()').decode().strip().split('\t')
        coordinate=dump_coordinate(stage/'database.sql')
        manifest={'format':'pkb-online-1','createdAt':datetime.now(timezone.utc).isoformat(),'sourceUuid':source[0],'database':source[1],'binlog':coordinate,'files':{},'objects':{},'excludes':['training checkpoints','Redis cache/queued transport'],'images':{}}
        # Database commits before object writes are forbidden by storage services. All published object keys are immutable.
        for i,key in enumerate(keys):
            if i%20==0:
                heartbeat=sql("UPDATE runtime_operation_lock SET expires_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 2 HOUR) WHERE name='object-snapshot' AND token='%s'; SELECT ROW_COUNT();"%token).strip()
                if heartbeat!=b'1':raise RuntimeError('Snapshot guard lost')
            name='objects/'+hashlib.sha256(key.encode()).hexdigest();path=stage/name;path.parent.mkdir(exist_ok=True)
            with path.open('wb') as output:run(object_command(key,prefix),output=output)
            manifest['objects'][name]=key
        (stage/'installation.env').write_bytes((ROOT/'.env').read_bytes())
        ids=run(prefix+['ps','-aq']).decode().split()
        manifest['images']=deployment_images(ROOT,{item['Config']['Labels'].get('com.docker.compose.service'):item for item in json.loads(run(['docker','inspect',*ids]))})
        for file in [stage/'database.sql',stage/'installation.env',*(stage/name for name in manifest['objects'])]:manifest['files'][file.relative_to(stage).as_posix()]={'sha256':digest(file),'bytes':file.stat().st_size}
        payload=stage/'payload.zip'
        with zipfile.ZipFile(payload,'x',compression=zipfile.ZIP_STORED) as z:
            z.writestr('manifest.json',json.dumps(manifest))
            for name in manifest['files']:z.write(stage/name,name)
        partial=destination.with_suffix(destination.suffix+'.partial')
        try:crypt(payload,partial,secret);partial.replace(destination)
        finally:partial.unlink(missing_ok=True)
        print('Encrypted online snapshot completed: %d objects. Training checkpoints are not included.'%len(keys))
    finally:sql("UPDATE runtime_operation_lock SET expires_at=UTC_TIMESTAMP(),token=NULL WHERE name='object-snapshot' AND token='%s'"%token)

def restore(archive,secret,name,port,verify=False):
    if not verify and (not re.fullmatch(r'pkb-online-restore-[a-z0-9]{8,32}',name or '') or not 1024<=port<=65535):raise ValueError('Use a fresh pkb-online-restore-<8+ characters> project and a free local port')
    parent=ROOT/'.runtime';parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='online-verify-',dir=parent) as temporary:
        stage=Path(temporary);os.chmod(stage,0o700);payload=stage/'payload.zip';crypt(archive,payload,secret,True)
        with zipfile.ZipFile(payload) as z:
            manifest=json.loads(z.read('manifest.json'))
            if manifest.get('format')!='pkb-online-1':raise ValueError('Unsupported snapshot')
            if set(z.namelist())!={'manifest.json',*manifest['files']} or not {'database.sql','installation.env'}.issubset(manifest['files']):raise ValueError('Archive members do not match manifest')
            if len(z.namelist())!=len(set(z.namelist())):raise ValueError('Duplicate archive members')
            for filename,info in manifest['files'].items():
                if filename not in ('database.sql','installation.env') and not re.fullmatch(r'objects/[a-f0-9]{64}',filename):raise ValueError('Unsafe archive path')
                target=stage/filename;target.parent.mkdir(exist_ok=True)
                with z.open(filename) as source,target.open('xb') as output:
                    total=0;checksum=hashlib.sha256()
                    while chunk:=source.read(1024*1024):
                        total+=len(chunk)
                        if total>info['bytes']:raise ValueError('Archive size mismatch')
                        checksum.update(chunk);output.write(chunk)
                if total!=info['bytes'] or checksum.hexdigest()!=info['sha256']:raise ValueError('Archive verification failed')
            for filename,key in manifest['objects'].items():
                if filename not in manifest['files']:raise ValueError('Missing object manifest entry')
                key_path(key)
        if verify:print('Online snapshot authentication and checksums verified.');return
        destination=parent/name
        if destination.exists() or run(['docker','ps','-aq','--filter','label=com.docker.compose.project='+name]).strip() or run(['docker','volume','ls','-q','--filter','label=com.docker.compose.project='+name]).strip():raise ValueError('Restore project already exists')
        destination.mkdir();os.chmod(destination,0o700)
        env=(stage/'installation.env').read_text(encoding='utf-8-sig');env='\n'.join(line for line in env.splitlines() if not line.startswith(('WEB_PORT=','PUBLIC_WEB_URL=','RESET_ADMIN_PASSWORD=','RESET_TEST_PASSWORD=')))+f'\nWEB_PORT={port}\nPUBLIC_WEB_URL=http://localhost:{port}\nRESET_ADMIN_PASSWORD=false\nRESET_TEST_PASSWORD=false\n'
        (destination/'installation.env').write_text(env,encoding='utf-8');os.chmod(destination/'installation.env',0o600)
        services={s:{'image':image} for s,image in manifest['images'].items() if s in ('mysql','redis','minio','clamav','backend','frontend','model-worker')}
        services.setdefault('backend',{})['environment']={'APP_BOOTSTRAP_ENABLED':'false','WORKER_ENABLED':'false','APP_WORKSPACE_JOBS_ENABLED':'false','PERSONAL_AI_REMOTE_ENABLED':'false','SMTP_HOST':'','SMS_WEBHOOK_URL':'','TRAINING_SERVICE_URL':'http://unavailable:8090'}
        (destination/'images.json').write_text(json.dumps({'services':services}),encoding='utf-8')
        (destination/'snapshot.json').write_text(json.dumps({k:manifest[k] for k in ('sourceUuid','database','binlog')}),encoding='utf-8')
        prefix=['docker','compose','-p',name,'--env-file',str(destination/'installation.env'),'-f',str(ROOT/'compose.yaml'),'-f',str(destination/'images.json')]
        run(prefix+['up','-d','--no-build','--wait','--wait-timeout','240','mysql','redis','minio'])
        wait_for_database(prefix)
        import_sql(stage/'database.sql',prefix)
        sql("UPDATE runtime_operation_lock SET expires_at=UTC_TIMESTAMP(),token=NULL",prefix)
        sql("UPDATE user_session SET revoked_at=UTC_TIMESTAMP() WHERE revoked_at IS NULL",prefix)
        create='curl --fail --silent --show-error --aws-sigv4 aws:amz:us-east-1:s3 --user "$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD" -X PUT "http://127.0.0.1:9000/$1"'
        run(prefix+['exec','-T','minio','sh','-c',create,'snapshot',bucket(tuple(prefix))])
        for filename,key in manifest['objects'].items():run(object_command(key,prefix,True),data=(stage/filename).read_bytes())
        run(prefix+['up','-d','--no-build','--wait','--wait-timeout','300','clamav','backend','frontend'])
        print('Restored into new project '+name+' at http://localhost:'+str(port)+'. Verify business data before switching traffic. Worker remains stopped pending task review.')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['backup','verify','restore']);p.add_argument('archive',type=Path);p.add_argument('--name');p.add_argument('--port',type=int,default=14373);a=p.parse_args();secret=password(a.action=='backup')
    if a.action=='backup':backup(a.archive,secret)
    else:restore(a.archive,secret,a.name,a.port,a.action=='verify')
