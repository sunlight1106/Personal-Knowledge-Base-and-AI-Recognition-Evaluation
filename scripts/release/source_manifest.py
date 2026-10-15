"""Non-secret deployment source checksums for ZIP and Git installations."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
FILES=('compose.yaml','compose.images.yaml','.env.example','scripts/release/install.py','scripts/release/source_manifest.py',
       'scripts/backup/compose_backup.py','scripts/backup/scheduled_backup.py')
def build():
    # Git on Windows may use CRLF; normalize source text, not image archives.
    return {name:hashlib.sha256((ROOT/name).read_text(encoding='utf-8').encode()).hexdigest() for name in FILES}
if __name__=='__main__':print(json.dumps(build(),sort_keys=True))
