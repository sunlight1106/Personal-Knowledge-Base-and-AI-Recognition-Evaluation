"""One release ledger generates VERSION, package versions and CHANGELOG.md.

sync/check do not publish anything or access credentials.
"""
import argparse
import datetime as dt
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER = Path('cle/src/data/releases.json')
KINDS = {'added': '新增', 'fixed': '修复', 'improved': '优化', 'notes': '使用与更新说明'}


def version_parts(value):
    if not isinstance(value, str) or not re.fullmatch(r'(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,3})', value):
        raise ValueError('Version must be a two-part number, for example 0.11')
    return tuple(map(int, value.split('.')))


def validate(data):
    version_parts(data['current'])
    entries = data['releases']
    if not entries or entries[0]['version'] != data['current']:
        raise ValueError('The newest entry must match current')
    previous = None
    for index, entry in enumerate(entries):
        parts = version_parts(entry['version'])
        if previous is not None and parts >= previous:
            raise ValueError('Release versions must be unique and newest first')
        previous = parts
        if entry['status'] not in ('draft', 'ready', 'published'):
            raise ValueError('Unknown release status')
        if index and entry['status'] != 'published':
            raise ValueError('Finish the current release before starting another')
        if entry['status'] != 'draft' and not entry.get('date'):
            raise ValueError('Ready and published versions require a date')
        if entry.get('date'):
            dt.date.fromisoformat(entry['date'])
        for value in [entry['title']] + [item for key in KINDS for item in entry[key]]:
            if not all(isinstance(value.get(lang), str) and value[lang].strip() for lang in ('zh', 'en')):
                raise ValueError('Release notes require Chinese and English text')
        if not any(entry[key] for key in ('added', 'fixed', 'improved')):
            raise ValueError('Describe at least one change before syncing a release')
    return data


def load(root=ROOT):
    return validate(json.loads((root / LEDGER).read_text(encoding='utf-8')))


def entry_markdown(entry):
    states = {'draft': '待发布', 'ready': '待发布 · 已定版', 'published': '已发布'}
    lines = [f"## v{entry['version']} · {states[entry['status']]}" + (f" · {entry['date']}" if entry.get('date') else ''), '', entry['title']['zh'], '']
    for key, title in KINDS.items():
        if entry[key]:
            lines += [f'### {title}', ''] + ['- ' + item['zh'] for item in entry[key]] + ['']
    return '\n'.join(lines)


def generated(root, data):
    version = data['current']
    semver = version + '.0'
    files = {Path('VERSION'): version + '\n'}
    # The notification API and the public history use the same authored release notes.
    announcement = {key: data['releases'][0][key] for key in ('version', 'date', 'title', *KINDS)}
    files[Path('src/main/resources/release-announcement.json')] = json.dumps(announcement, ensure_ascii=False, indent=2) + '\n'
    for name in ('cle/package.json', 'cle/package-lock.json'):
        package = json.loads((root / name).read_text(encoding='utf-8'))
        package['version'] = semver
        if name.endswith('package-lock.json'):
            package['packages']['']['version'] = semver
        files[Path(name)] = json.dumps(package, ensure_ascii=False, indent=2) + '\n'
    pom = (root / 'pom.xml').read_text(encoding='utf-8')
    pom, count = re.subn(r'(<artifactId>pkb-ai-evaluation-backend</artifactId>\s*<version>)[^<]+', lambda m: m[1] + semver, pom, count=1)
    if count != 1:
        raise ValueError('Cannot locate backend project version')
    files[Path('pom.xml')] = pom
    files[Path('CHANGELOG.md')] = '# 更新日志\n\n每个版本保留独立记录。待发布内容不代表 GitHub 安装包已更新。\n\n' + '\n'.join(entry_markdown(entry) for entry in data['releases']).rstrip() + '\n'
    return files


def sync(root=ROOT, check=False):
    data = load(root)
    mismatches = []
    for path, content in generated(root, data).items():
        target = root / path
        if not target.exists() or target.read_text(encoding='utf-8') != content:
            mismatches.append(str(path))
            if not check:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content, encoding='utf-8', newline='\n')
    if check and mismatches:
        raise ValueError('Version files are out of sync: ' + ', '.join(mismatches) + '. Run python scripts/versioning.py sync')
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('sync', 'check', 'notes'))
    args = parser.parse_args()
    data = sync(check=args.command != 'sync')
    if args.command == 'notes':
        print(entry_markdown(data['releases'][0]))
    else:
        print(f"Version {data['current']}: {args.command} passed")


if __name__ == '__main__':
    main()
