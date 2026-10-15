"""Restore a trusted archive into an isolated, temporary deployment and verify it.

No production volume is mounted writable. No restored workload may contact the internet.
Only this run's labeled volumes and runtime directory are removed on completion.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import time
import urllib.parse
import uuid
import compose_backup as backup


def container_request(command,url,method='GET',headers=None,data=None):
    """Run inside the isolated backend; credentials travel on stdin, never argv."""
    if urllib.parse.urlsplit(url).hostname not in ('127.0.0.1','minio'):raise ValueError('Only restored services may be contacted')
    config=[('url',url),('request',method)]
    config.extend(('header',key+': '+value) for key,value in (headers or {}).items())
    if data is not None:config.append(('data',json.dumps(data)))
    payload='\n'.join(key+' = '+json.dumps(value,ensure_ascii=False) for key,value in config)+'\n'
    return backup.run(command+['exec','-T','backend','curl','--silent','--show-error','--fail','--max-time','30','--max-filesize','22020096','--config','-'],input=payload.encode(),stdout=subprocess.PIPE).stdout


def api(command, path, method='GET', data=None, token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    return json.loads(container_request(command,'http://127.0.0.1:8080/api/v1'+path,method,headers,data)).get('data')


def object_bytes(command, base, bucket, key, access, secret):
    """AWS Signature v4, local restored object storage only."""
    now=datetime.now(timezone.utc);stamp=now.strftime('%Y%m%dT%H%M%SZ');day=now.strftime('%Y%m%d')
    host=urllib.parse.urlsplit(base).netloc
    path='/'+urllib.parse.quote(bucket,safe='')+'/'+urllib.parse.quote(key,safe='/')
    empty=hashlib.sha256(b'').hexdigest();headers=f'host:{host}\nx-amz-content-sha256:{empty}\nx-amz-date:{stamp}\n'
    signed='host;x-amz-content-sha256;x-amz-date';scope=day+'/us-east-1/s3/aws4_request'
    canonical='GET\n'+path+'\n\n'+headers+'\n'+signed+'\n'+empty
    string='AWS4-HMAC-SHA256\n'+stamp+'\n'+scope+'\n'+hashlib.sha256(canonical.encode()).hexdigest()
    def sign(key,text):return hmac.new(key,text.encode(),hashlib.sha256).digest()
    signing=sign(sign(sign(sign(('AWS4'+secret).encode(),day),'us-east-1'),'s3'),'aws4_request')
    signature=hmac.new(signing,string.encode(),hashlib.sha256).hexdigest()
    return container_request(command,base+path,headers={'x-amz-date':stamp,'x-amz-content-sha256':empty,'Authorization':f'AWS4-HMAC-SHA256 Credential={access}/{scope}, SignedHeaders={signed}, Signature={signature}'})


def drill(root, archive, secret):
    root=root.resolve();name='pkb-restore-drill-'+uuid.uuid4().hex[:12];stage=root/'.runtime'/name
    if not stage.resolve().is_relative_to((root/'.runtime').resolve()) or stage.exists():raise ValueError('Unsafe drill destination')
    status=root/'.runtime'/'backups'/'drill.json';status.parent.mkdir(parents=True,exist_ok=True)
    started=datetime.now(timezone.utc).isoformat();checks={};command=None;phase='archive'
    try:
        backup.inspect_archive(archive,secret,root,name)
        phase='configuration'
        # Resolve original settings against the recovered env, never the production env.
        config=json.loads(backup.run(['docker','compose','--env-file',str(stage/'.env'),'-f',str(root/'compose.yaml'),'config','--format','json'],cwd=root,stdout=subprocess.PIPE).stdout)
        restored=json.loads((stage/'override.json').read_text());services={}
        for service in ('mysql','redis','minio','clamav','backend'):
            original=config['services'][service]
            selected={k:original[k] for k in ('image','command','entrypoint','environment','healthcheck','user') if k in original}
            if service in restored['services']:selected['image']=restored['services'][service]['image']
            if service in backup.VOLUMES:selected['volumes']=[{'type':'volume','source':name+'_'+backup.ENV_NAMES[service],'target':backup.VOLUMES[service]}]
            if service=='backend':
                env=selected.setdefault('environment',{});env.update({'APP_BOOTSTRAP_ENABLED':'false','RESET_ADMIN_PASSWORD':'false','RESET_TEST_PASSWORD':'false','WORKER_ENABLED':'false','APP_WORKSPACE_JOBS_ENABLED':'false','PERSONAL_AI_REMOTE_ENABLED':'false','SMTP_HOST':'','SMS_WEBHOOK_URL':'','TRAINING_SERVICE_URL':'http://unavailable:8090'})
                selected['depends_on']={s:{'condition':'service_healthy'} for s in ('mysql','redis','minio','clamav')}
            services[service]=selected
        # A private copy of current virus definitions avoids disabling scanning in the drill.
        definitions=name+'_definitions';backup.run(['docker','volume','create','--label','pkb.restore='+name,definitions],stdout=subprocess.DEVNULL)
        clam_id=backup.run(['docker','compose','ps','-q','clamav'],cwd=root,stdout=subprocess.PIPE).stdout.decode().strip()
        clam=backup.docker_json('inspect',clam_id)[0]
        source=next(m['Name'] for m in clam['Mounts'] if m['Destination']=='/var/lib/clamav' and m['Type']=='volume')
        helper=services['backend']['image']
        backup.run(['docker','run','--rm','--network','none','--user','0','--mount',f'type=volume,source={source},target=/source,readonly','--mount',f'type=volume,source={definitions},target=/target','--entrypoint','cp',helper,'-a','/source/.','/target/'],stdout=subprocess.DEVNULL)
        services['clamav']['volumes']=[{'type':'volume','source':definitions,'target':'/var/lib/clamav'}]
        volumes={v['name']:{'external':True,'name':v['name']} for v in restored['volumes'].values()};volumes[definitions]={'external':True,'name':definitions}
        isolated={'name':name,'services':services,'volumes':volumes,'networks':{'default':{'internal':True}}}
        compose=stage/'drill.json';compose.write_text(json.dumps(isolated));os.chmod(compose,0o600)
        command=['docker','compose','-p',name,'-f',str(compose)]
        phase='services'
        backup.run(command+['up','-d','--no-build'],stdout=subprocess.DEVNULL)
        backend_id=backup.run(command+['ps','-q','backend'],stdout=subprocess.PIPE).stdout.decode().strip()
        for _ in range(120):
            state=backup.docker_json('inspect',backend_id)[0]['State']
            if state.get('Health',{}).get('Status')=='healthy':break
            if state.get('Status') in ('exited','dead'):raise RuntimeError('Recovered backend stopped')
            time.sleep(5)
        else:raise TimeoutError('Recovered backend did not become ready')
        checks['services']=True
        phase='database'
        def sql(query):
            script='MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names'
            return backup.run(command+['exec','-T','mysql','sh','-c',script],input=query.encode(),stdout=subprocess.PIPE).stdout.decode().strip()
        # Check recovered rows before creating the synthetic note below.
        checks['restoredNoteCount']=int(sql('SELECT COUNT(*) FROM note WHERE body IS NOT NULL;'))
        note_hashes=sql('SELECT SHA2(body,256) FROM note WHERE body IS NOT NULL LIMIT 3;').splitlines()
        if any(not re.fullmatch('[a-f0-9]{64}',value) for value in note_hashes):raise ValueError('Recovered note could not be read')
        checks['restoredNotesChecked']=len(note_hashes)
        phase='login'
        username='drill_'+uuid.uuid4().hex[:12];password=secrets.token_urlsafe(32)+'aA1!'
        api(command,'/auth/register','POST',{'username':username,'email':username+'@example.invalid','password':password,'confirmPassword':password})
        token=api(command,'/auth/login','POST',{'username':username,'password':password})['token'];checks['login']=True
        phase='notes'
        note=api(command,'/notes','POST',{'title':'Recovery drill','body':'Isolated verification','contentFormat':'MARKDOWN','status':'DRAFT'},token)
        assert api(command,'/notes/'+note['id'],token=token)['body']=='Isolated verification';checks['noteRoundTrip']=True
        samples=sql("SELECT storage_path,sha256 FROM file_asset WHERE scan_status='CLEAN' AND size_bytes<=20971520 LIMIT 3;")
        phase='attachments'
        checked=0;env=services['backend']['environment']
        for line in samples.splitlines():
            key,expected=line.split('\t');data=object_bytes(command,'http://minio:9000',env['S3_BUCKET'],key,env['S3_ACCESS_KEY'],env['S3_SECRET_KEY'])
            if hashlib.sha256(data).hexdigest()!=expected:raise ValueError('Recovered attachment checksum mismatch')
            checked+=1
        checks['attachmentsChecked']=checked
        result={'lastAttempt':started,'lastSuccess':datetime.now(timezone.utc).isoformat(),'success':True,'checks':checks,'archiveSha256':backup.digest(archive)}
        status.write_text(json.dumps(result));return result
    except Exception as error:
        status.write_text(json.dumps({'lastAttempt':started,'success':False,'checks':checks,'phase':phase,'errorType':type(error).__name__,'message':'恢复演练未通过，请检查本机演练日志。当前部署数据未被覆盖。'}));raise
    finally:
        if command:
            try:backup.run(command+['down','--remove-orphans'],stdout=subprocess.DEVNULL)
            except Exception:pass
        # Labels plus exact prefix restrict cleanup to objects created for this run.
        for volume in backup.run(['docker','volume','ls','--filter','label=pkb.restore='+name,'--format','{{.Name}}'],stdout=subprocess.PIPE).stdout.decode().split():
            if not volume.startswith(name+'_'):raise ValueError('Unexpected drill volume')
            backup.run(['docker','volume','rm',volume],stdout=subprocess.DEVNULL)
        if stage.exists() and stage.resolve().parent==(root/'.runtime').resolve() and stage.name==name:shutil.rmtree(stage)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('archive',type=Path);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);args=parser.parse_args()
    try:print(json.dumps(drill(args.root,args.archive.resolve(),backup.password()),ensure_ascii=False))
    except Exception as exc:print('Restore drill failed: '+type(exc).__name__+'; private diagnostics suppressed.');raise SystemExit(1)
