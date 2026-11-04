"""Read-only localhost smoke: shipped entry assets and anonymous permission boundary.

No credentials, private records, configuration files or provider calls are used.
Run against either the source deployment or the installed desktop deployment.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', default='http://127.0.0.1:4273')
parser.add_argument('--report', type=Path)
args = parser.parse_args()
origin = urllib.parse.urlsplit(args.base_url)
if (origin.scheme not in {'http', 'https'} or origin.hostname not in {'localhost', '127.0.0.1', '::1'}
        or origin.username or origin.password or origin.path not in {'', '/'} or origin.query or origin.fragment):
    parser.error('Use a loopback origin without credentials, path, query or fragment')
base = args.base_url.rstrip('/')
root = Path(__file__).resolve().parents[2]
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
checks = []

def get(path, expected=200):
    started = time.perf_counter()
    try:
        response = opener.open(urllib.request.Request(base + path, headers={'Cache-Control': 'no-cache'}), timeout=20)
    except urllib.error.HTTPError as failure:
        response = failure
    with response:
        status, headers, body = response.status, response.headers, response.read(4_000_000)
    checks.append({'path': path, 'status': status, 'ms': round((time.perf_counter()-started)*1000, 2)})
    if status != expected:
        raise RuntimeError(f'{path}: expected {expected}, received {status}')
    return headers, body

try:
    _, html = get('/')
    match = re.search(rb'<script[^>]+src="(/assets/[\w.-]+\.js)"', html)
    if not match:
        raise RuntimeError('Built module entry missing')
    entry = match[1].decode()
    headers, metadata = get('/build-info.json')
    if json.loads(metadata).get('entry') != entry or 'no-store' not in headers.get('Cache-Control', ''):
        raise RuntimeError('Build metadata differs from served HTML or can be cached')
    assets = sorted(set(re.findall(rb'(?:src|href)="(/assets/[\w.-]+\.(?:js|css))"', html)))
    for asset in assets:
        headers, body = get(asset.decode())
        expected_type = 'javascript' if asset.endswith(b'.js') else 'text/css'
        if expected_type not in headers.get('Content-Type', '') or not body:
            raise RuntimeError('Asset returned an empty body or an HTML fallback')
    for path in ['/login', '/vocabulary-test', '/docs', '/models', '/updates']:
        _, page = get(path)
        if entry.encode() not in page:
            raise RuntimeError('Public route served a different application build')
    # The same finite read matrix used by server-side grant/deny integration tests.
    matrix = (root/'src/test/java/com/robustvision/platform/security/PermissionMatrixIntegrationTest.java').read_text(encoding='utf-8')
    paths = re.findall(r'endpoint\("([^"]+)"', matrix)
    if not paths:
        raise RuntimeError('Permission matrix is empty')
    for path in paths:
        get('/api/v1' + path, 401)
        time.sleep(.3)  # One bounded pass; respect the deployment's rate limits.
    result = {'result': 'PASS', 'baseUrl': base, 'entry': entry, 'entryHtmlSha256': hashlib.sha256(html).hexdigest(),
              'anonymousEndpoints': len(paths), 'checks': checks, 'providerCalls': 0}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(f'PASS: {len(checks)} HTTP checks, {len(paths)} anonymous permission boundaries, matching entry assets.')
except Exception:
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps({'result': 'FAIL', 'checks': checks}, indent=2), encoding='utf-8')
    raise
