"""Package a local desktop snapshot. Public release guards remain unchanged.

Called by sync-local.ps1 after backup. Includes uncommitted, non-ignored source,
never private runtime/configuration. Docker images stay on this computer.
"""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('release_build', Path(__file__).with_name('build-release.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def source_names():
    names = release.command('git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z').split('\0')
    result = []
    for name in sorted(set(filter(None, names))):
        parts = PurePosixPath(name).parts
        path = ROOT / name
        if ('..' in parts or path.is_symlink() or not path.resolve().is_relative_to(ROOT.resolve()) or
                any(p.lower() in {'.git', '.runtime', 'output', 'node_modules', 'target', 'backups', 'uploads', '.tools', '.venv'} for p in parts) or
                any(p.lower().startswith('.env') and p.lower() != '.env.example' for p in parts) or
                path.suffix.lower() in {'.pkb', '.pem', '.key', '.pfx', '.p12', '.log'}):
            raise ValueError('Private or unsafe source path: ' + name)
        if path.is_file():
            result.append(name)
    return result


def build(desktop, installed, backup_dir, reuse_images=False):
    data = release.sync(ROOT, check=True)
    version = data['current']
    previous = (installed / 'VERSION').read_text().strip()
    if release.version_parts(previous) > release.version_parts(version):
        raise ValueError('Refusing to downgrade the installed version')
    old = json.loads((installed / 'release.json').read_text(encoding='utf-8-sig'))
    stamp = datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S%f')
    out = ROOT / 'output' / 'desktop-local' / stamp
    out.mkdir(parents=True)
    if not reuse_images:
        with (out / 'build.log').open('wb') as log:
            subprocess.run(['docker', 'compose', 'build', 'backend', 'frontend', 'code-lab'], cwd=ROOT,
                           stdout=log, stderr=subprocess.STDOUT, check=True,
                           env={k:v for k,v in os.environ.items() if k not in ('COMPOSE_PROJECT_NAME','COMPOSE_FILE','PKB_PREBUILT_RELEASE')})
    manifest = {'version': version, 'revision': 1, 'localSnapshot': True, 'snapshotId': stamp,
                'sourceCommit': release.command('git', 'rev-parse', 'HEAD'), 'includesWorkingTree': True,
                'createdAt': datetime.now(timezone.utc).isoformat(), 'images': [], 'infrastructure': old['infrastructure']}
    for name, image in release.IMAGES.items():
        # Keep storage and optional training images exactly as installed.
        if name in ('minio', 'training'):
            image = next(i['id'] for i in old['images'] if i['name'] == name)
        image_id = release.command('docker', 'image', 'inspect', image, '--format', '{{.Id}}')
        if not re.fullmatch('sha256:[a-f0-9]{64}', image_id):
            raise ValueError('Invalid local image ID')
        tag = f'pkb-{name}:{version}-local-{stamp}'
        release.command('docker', 'tag', image_id, tag)
        manifest['images'].append({'name': name, 'id': image_id, 'tag': tag})
    tags = {i['name']: i['tag'] for i in manifest['images']}
    tags['model-worker'] = tags['backend']
    tags.update({i['name']: i['image'] for i in manifest['infrastructure']})
    override = 'services:\n' + ''.join(f'  {name}:\n    image: {tag}\n' for name, tag in tags.items())
    notes = '# 本地测试快照 / Local test snapshot\n\n' + release.entry_markdown(data['releases'][0]) + '\n'
    runtime = out / f'runtime-{version}.zip'
    hashes = {}
    with zipfile.ZipFile(runtime, 'w', zipfile.ZIP_DEFLATED) as z:
        for name in source_names():
            content = (ROOT / name).read_bytes()
            hashes[name] = __import__('hashlib').sha256(content).hexdigest()
            if name.endswith('.ps1'):
                content = content.decode('utf-8-sig').encode('utf-8-sig')
            z.writestr(name, content)
        z.writestr('release.json', json.dumps(manifest, indent=2))
        z.writestr('compose.desktop.yaml', override)
        z.writestr('RELEASE-NOTES.md', notes)
        z.writestr('local-source-hashes.json', json.dumps(hashes, indent=2))
    source = release.launcher_source(version, release.sha(runtime), runtime.name)
    (out / 'Launcher.generated.cs').write_text(source, encoding='utf-8-sig')
    compiler = Path(os.environ['WINDIR']) / 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    exe = out / f'{version}.exe'
    subprocess.run([str(compiler), '/nologo', '/target:winexe', '/platform:x64', '/optimize+',
                    '/reference:System.Windows.Forms.dll', '/reference:System.Drawing.dll',
                    '/reference:System.IO.Compression.dll', '/reference:System.IO.Compression.FileSystem.dll',
                    '/out:' + str(exe), str(out / 'Launcher.generated.cs')], check=True)
    subprocess.run([str(exe), '--self-test', str(runtime)], check=True)
    (out / 'RELEASE-NOTES.md').write_text(notes, encoding='utf-8-sig')
    guide = f'''# 个人知识库 {version} · 本地测试版

双击 `{version}.exe` 启动，保留旁边的 `{runtime.name}`。本文件夹名称作为固定入口保留，实际版本以 EXE 和网站更新记录为准。

这是同步当前源码的本机测试包，使用这台电脑已经校验的 Docker 镜像，尚未发布 GitHub；不能直接复制到其他电脑安装。
现有账号、笔记、文件、个人 AI 配置与端口保留。安装目录仍是 `%LOCALAPPDATA%\\PKB-AI\\0.1`，与源码版数据独立。

更新记录见 RELEASE-NOTES.md。更新前的加密备份与旧启动文件在安装目录 `.runtime\\desktop-updates`。
备份密钥由当前 Windows 账号保护，验证和恢复方法见 `docs/LOCAL_DESKTOP_SYNC.md`。不要运行备份中的旧 EXE 降级。
'''
    (out / 'START-HERE.md').write_text(guide, encoding='utf-8-sig')
    names = [exe.name, runtime.name, 'START-HERE.md', 'RELEASE-NOTES.md']
    (out / 'SHA256SUMS').write_text(''.join(release.sha(out/n) + '  ' + n + '\n' for n in names), encoding='ascii')
    names.append('SHA256SUMS')
    # Archive only managed launch files, never user-added material.
    saved = backup_dir / 'desktop-files'
    saved.mkdir()
    for path in desktop.iterdir():
        if path.is_file() and (re.fullmatch(r'\d+\.\d+\.exe|runtime-\d+\.\d+(?:-r\d+)?\.zip', path.name)
                               or path.name in ('START-HERE.md', 'RELEASE-NOTES.md', 'SHA256SUMS')):
            shutil.copy2(path, saved / path.name)
    for name in names:
        temporary = desktop / (name + '.updating')
        shutil.copy2(out / name, temporary)
        temporary.replace(desktop / name)
    for path in saved.iterdir():
        if path.name not in names:
            (desktop / path.name).unlink()
    (backup_dir / 'snapshot.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
    print('Local desktop package prepared: ' + str(desktop / exe.name), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--desktop', type=Path, required=True)
    p.add_argument('--installed', type=Path, required=True)
    p.add_argument('--backup-dir', type=Path, required=True)
    p.add_argument('--reuse-images', action='store_true', help='Use only immediately previously built and validated images')
    args = p.parse_args()
    build(args.desktop.resolve(), args.installed.resolve(), args.backup_dir.resolve(), args.reuse_images)
