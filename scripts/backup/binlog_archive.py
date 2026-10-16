"""Encrypt closed MySQL binary logs, or replay them into an online-snapshot restore.

Replay is deliberately limited to a separate restored database. It does not recover
attachments created after the snapshot; archive immutable objects separately before
promoting a database recovery to a complete site recovery.
"""
import argparse
import json
from pathlib import Path
import re
import tempfile
import zipfile
from online_snapshot import ROOT,run,sql
from compose_backup import crypt,password,digest
TOOLS_IMAGE='robust-vision-binlog-tools:8.4'

def valid_log(name):
    if not re.fullmatch(r'[A-Za-z0-9_-]+\.\d{6,}',name):raise ValueError('Invalid binary log filename')
    return name

def archive(destination,secret):
    destination=destination.resolve()
    if destination.exists():raise ValueError('Archive already exists')
    destination.parent.mkdir(parents=True,exist_ok=True)
    sql('FLUSH BINARY LOGS')
    source=sql('SELECT @@server_uuid,DATABASE()').decode().strip().split('\t')
    rows=sql('SHOW BINARY LOGS').decode().strip().splitlines()[:-1] # Only closed, immutable logs.
    if not rows:raise ValueError('No closed binary logs available')
    with tempfile.TemporaryDirectory(prefix='pkb-binlog-',dir=destination.parent) as temporary:
        stage=Path(temporary);stage.chmod(0o700)
        manifest={'format':'pkb-binlogs-1','sourceUuid':source[0],'database':source[1],'files':{}}
        for row in rows:
            name=valid_log(row.split('\t')[0]);path=stage/name
            with path.open('wb') as output:run(['docker','compose','exec','-T','mysql','cat','/var/lib/mysql/'+name],output=output)
            manifest['files'][name]={'bytes':path.stat().st_size,'sha256':digest(path)}
        payload=stage/'payload.zip'
        with zipfile.ZipFile(payload,'x') as z:
            z.writestr('manifest.json',json.dumps(manifest))
            for name in manifest['files']:z.write(stage/name,name)
        partial=destination.with_suffix(destination.suffix+'.partial')
        try:crypt(payload,partial,secret);partial.replace(destination)
        finally:partial.unlink(missing_ok=True)
    print('Encrypted closed-log archive created. Keep it with its original online snapshot.')

def replay(archive_path,secret,project,stop_file,stop_position,database_only):
    if not database_only:raise ValueError('Review attachment recovery limits, then pass --database-only')
    if not re.fullmatch(r'pkb-online-restore-[a-z0-9]{8,32}',project or ''):raise ValueError('Replay only supports isolated online-snapshot restores')
    valid_log(stop_file)
    if stop_position<4:raise ValueError('Invalid stop position')
    target=ROOT/'.runtime'/project;record=target/'snapshot.json';marker=target/'replay-started.json'
    if marker.exists():raise ValueError('Replay was already attempted. Create a fresh snapshot restore before retrying')
    original=json.loads(record.read_text())
    try:run(['docker','image','inspect',TOOLS_IMAGE])
    except RuntimeError:raise RuntimeError('Build recovery tools first: docker build -f scripts/backup/BinlogTools.Dockerfile -t '+TOOLS_IMAGE+' .') from None
    prefix=['docker','compose','-p',project,'--env-file',str(target/'installation.env'),'-f',str(ROOT/'compose.yaml'),'-f',str(target/'images.json')]
    with tempfile.TemporaryDirectory(prefix='binlog-verify-',dir=ROOT/'.runtime') as temporary:
        stage=Path(temporary);stage.chmod(0o700);payload=stage/'payload.zip';crypt(archive_path,payload,secret,True)
        with zipfile.ZipFile(payload) as z:
            manifest=json.loads(z.read('manifest.json'))
            if manifest.get('format')!='pkb-binlogs-1' or any(manifest[k]!=original[k] for k in ('sourceUuid','database')):raise ValueError('Logs do not belong to the snapshot source')
            if set(z.namelist())!={'manifest.json',*manifest['files']} or len(z.namelist())!=len(manifest['files'])+1:raise ValueError('Unexpected archive members')
            names=sorted(valid_log(n) for n in manifest['files'])
            start=original['binlog']['file']
            if start not in names or stop_file not in names or names.index(start)>names.index(stop_file):raise ValueError('Required log range is not present')
            selected=names[names.index(start):names.index(stop_file)+1]
            suffixes=[int(n.rsplit('.',1)[1]) for n in selected]
            if suffixes!=list(range(suffixes[0],suffixes[-1]+1)):raise ValueError('Binary log gap detected')
            if stop_position>manifest['files'][stop_file]['bytes'] or (start==stop_file and stop_position<original['binlog']['position']):raise ValueError('Stop position is outside the available range')
            for name in selected:
                path=stage/name
                with z.open(name) as source,path.open('xb') as output:
                    import shutil
                    shutil.copyfileobj(source,output)
                info=manifest['files'][name]
                if path.stat().st_size!=info['bytes'] or digest(path)!=info['sha256']:raise ValueError('Log checksum mismatch')
        # Disable every application writer in this isolated project before replay.
        running=run(['docker','ps','-q','--filter','label=com.docker.compose.project='+project]).decode().split()
        inspected=json.loads(run(['docker','inspect',*running])) if running else []
        writers=[c['Id'] for c in inspected if c['Config']['Labels'].get('com.docker.compose.service') not in ('mysql','redis','minio','clamav')]
        if writers:run(['docker','stop',*writers])
        mysql=run(prefix+['ps','-q','mysql']).decode().strip()
        if not mysql:raise ValueError('Restored MySQL is not running')
        marker.write_text(json.dumps({'stopFile':stop_file,'stopPosition':stop_position,'complete':False}))
        # One offline process preserves transaction/session context across logs.
        # It sees only verified files; no live database volume or network access.
        output=stage/'replay.sql'
        with output.open('wb') as stream:
            run(['docker','run','--rm','--network','none','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--memory','256m','--pids-limit','64','--mount','type=bind,source='+str(stage)+',target=/logs,readonly',TOOLS_IMAGE,'--verify-binlog-checksum','--skip-gtids','--start-position='+str(original['binlog']['position']),'--stop-position='+str(stop_position),*['/logs/'+name for name in selected]],output=stream)
        with output.open('rb') as stream:
            import subprocess
            result=subprocess.run(prefix+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'],cwd=ROOT,stdin=stream,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=1800)
        if result.returncode:raise RuntimeError('Replay failed; restore a fresh copy before retrying')
        sql('UPDATE user_session SET revoked_at=UTC_TIMESTAMP() WHERE revoked_at IS NULL; UPDATE runtime_operation_lock SET expires_at=UTC_TIMESTAMP(),token=NULL;',prefix)
        marker.write_text(json.dumps({'stopFile':stop_file,'stopPosition':stop_position,'complete':True}))
    print('Isolated database replay complete. Applications remain stopped. Verify attachment coverage and business rows before promotion.')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['archive','replay']);p.add_argument('archive',type=Path);p.add_argument('--project');p.add_argument('--stop-file');p.add_argument('--stop-position',type=int,default=0);p.add_argument('--database-only',action='store_true');a=p.parse_args();secret=password(a.action=='archive')
    if a.action=='archive':archive(a.archive,secret)
    else:replay(a.archive,secret,a.project,a.stop_file,a.stop_position,a.database_only)
