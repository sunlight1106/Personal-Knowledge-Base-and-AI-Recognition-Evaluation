"""Build immutable Windows v0.1 assets from tracked sources and local verified images.

Run after tests and Docker builds. No .env, user data, browser state or Git credentials
are read or included. Outputs are ignored under output/release/0.1.
"""
import argparse, gzip, hashlib, json, os, re, shutil, subprocess, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output/release/0.1'
IMAGES={'backend':'robust-vision-backend:local','frontend':'robust-vision-frontend:latest',
        'code-lab':'robust-vision-code-lab:local','training':'robust-vision-training:local',
        'minio':'robust-vision-minio:2025-10-15'}
INFRASTRUCTURE={'mysql':'mysql:8.4','redis':'redis:7.4-alpine','clamav':'clamav/clamav:stable'}
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def command(*args):return subprocess.check_output(args,cwd=ROOT,text=True).strip()
def build(skip_images=False):
    if command('git','status','--porcelain','--untracked-files=normal'):
        raise RuntimeError('Commit the tested source before packaging; release inputs must be clean.')
    OUT.mkdir(parents=True,exist_ok=True)
    manifest={'version':'0.1','sourceCommit':command('git','rev-parse','HEAD'),'images':[],'infrastructure':[]}
    for service,source in INFRASTRUCTURE.items():
        repository=source.rsplit(':',1)[0]
        digests=json.loads(command('docker','image','inspect',source,'--format','{{json .RepoDigests}}'))
        digest=next((value for value in digests if re.fullmatch(re.escape(repository)+r'@sha256:[a-f0-9]{64}',value)),None)
        if not digest:raise RuntimeError('Missing registry digest for '+service)
        manifest['infrastructure'].append({'name':service,'image':digest})
    for name,source in IMAGES.items():
        image_id=command('docker','image','inspect',source,'--format','{{.Id}}')
        tag='pkb-'+name+':0.1';command('docker','tag',source,tag)
        target=OUT/(name+'.tar.gz'); stamp=OUT/(name+'.image-id')
        if not(skip_images and target.exists() and stamp.exists() and stamp.read_text()==image_id):
            print('Exporting '+name,flush=True)
            with (OUT/(name+'.partial')).open('wb') as raw, gzip.GzipFile(filename='',mode='wb',fileobj=raw,compresslevel=1,mtime=0) as packed:
                proc=subprocess.Popen(['docker','save',tag],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
                try:shutil.copyfileobj(proc.stdout,packed,1024*1024)
                finally:proc.stdout.close()
                if proc.wait()!=0:raise RuntimeError('Docker export failed: '+name)
            (OUT/(name+'.partial')).replace(target);stamp.write_text(image_id)
        manifest['images'].append({'name':name,'file':target.name,'sha256':sha(target),'id':image_id})
    (OUT/'release.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
    override='services:\n'+''.join('  '+service+':\n    image: pkb-'+image+':0.1\n' for service,image in [('backend','backend'),('model-worker','backend'),('frontend','frontend'),('code-lab','code-lab'),('training','training'),('minio','minio')])
    override+=''.join('  '+item['name']+':\n    image: '+item['image']+'\n' for item in manifest['infrastructure'])
    # Include only Git-tracked sources: staging new files makes this packaging boundary explicit.
    files=command('git','ls-files','-z').split('\0')
    runtime=OUT/'runtime-0.1.zip'
    with zipfile.ZipFile(runtime,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name in sorted(filter(None,files)):
            path=ROOT/name
            if name=='.env' or name.startswith(('.runtime/','output/','.git/')) or path.is_symlink():raise ValueError('Private/unsafe tracked path: '+name)
            if not path.is_file():continue
            data=path.read_bytes()
            # Windows PowerShell 5.1 needs a BOM for Chinese strings; script content otherwise unchanged.
            if path.suffix=='.ps1':data=data.decode('utf-8-sig').encode('utf-8-sig')
            z.writestr(name,data)
        z.writestr('release.json',json.dumps(manifest,indent=2))
        z.writestr('compose.desktop.yaml',override)
    source=(ROOT/'scripts/desktop/Launcher.cs').read_text(encoding='utf8').replace('__RUNTIME_SHA256__',sha(runtime))
    (OUT/'Launcher.generated.cs').write_text(source,encoding='utf-8-sig')
    compiler=Path(os.environ['WINDIR'])/'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    subprocess.run([str(compiler),'/nologo','/target:winexe','/platform:x64','/optimize+',
        '/reference:System.Windows.Forms.dll','/reference:System.Drawing.dll','/reference:System.IO.Compression.dll',
        '/reference:System.IO.Compression.FileSystem.dll','/out:'+str(OUT/'0.1.exe'),str(OUT/'Launcher.generated.cs')],check=True)
    subprocess.run([str(OUT/'0.1.exe'),'--self-test',str(runtime)],check=True)
    shutil.copyfile(ROOT/'docs/TEST_RELEASE_0.1.md',OUT/'START-HERE.md')
    with zipfile.ZipFile(OUT/'PKB-AI-0.1-Windows-x64.zip','w',zipfile.ZIP_DEFLATED) as z:
        for name in ['0.1.exe','runtime-0.1.zip','START-HERE.md']:z.write(OUT/name,name)
    # No generator sources or local stamps are published as binary assets.
    names=['0.1.exe','runtime-0.1.zip','PKB-AI-0.1-Windows-x64.zip','START-HERE.md','release.json']+[x+'.tar.gz' for x in IMAGES]+['minio-sources.tar.gz']
    missing=[name for name in names if not(OUT/name).is_file()]
    if missing:raise RuntimeError('Missing release companions: '+str(missing))
    (OUT/'SHA256SUMS').write_text(''.join(sha(OUT/name)+'  '+name+'\n' for name in names),encoding='ascii')
    print('Built and hash-verified 0.1.exe and Windows package.',flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reuse-images',action='store_true');args=p.parse_args();build(args.reuse_images)
