"""Rebuild the offline assessment bank from the already pinned ECDICT snapshot.

Usage: python scripts/build-vocabulary-assessment.py .runtime/ecdict.csv
No network access or personal data. Existing learning catalogs remain immutable.
"""
import csv
import gzip
import hashlib
import json
from pathlib import Path
import random
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SHA = '1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf'


def build(source):
    if hashlib.sha256(source.read_bytes()).hexdigest() != SOURCE_SHA:
        raise ValueError('Expected the pinned ECDICT source')
    catalog = json.loads(gzip.decompress((ROOT / 'database/vocabulary/v17.json.gz').read_bytes()))
    rows = {r['word']: r for r in csv.DictReader(source.open(encoding='utf-8'))
            if re.fullmatch('[a-z]{3,24}', r['word'])}
    def clean(text):
        text = re.sub(r'\\[rn]', ' ', text).strip()
        return re.sub(r'^(?:[a-z]+\.)\s*', '', text).strip()
    def rank(term):
        row = rows[term]
        return min([int(row[k]) for k in ('frq', 'bnc') if row[k].isdigit() and int(row[k]) > 0] or [999999])
    # Keep lexical headwords with concise standalone senses. Exclude cross-references,
    # inflected variants, proper names, clinical/abusive/sexual prompts from this public quiz.
    excluded = re.compile(r'[A-Za-z]{3,}|[\[\]＝=]|性交|淫|妓|阴茎|阴道|肛|婊|睾丸|精液|强奸|奴隶|黑鬼')
    eligible = [e for e in catalog['entries'] if e['term'] in rows
                and '0:' not in rows[e['term']]['exchange'] and rank(e['term']) < 999999
                and len(e['meaning']) <= 65 and not excluded.search(e['meaning'])
                and all(not excluded.search(v) for v in e['distractors'])]
    eligible.sort(key=lambda e: (rank(e['term']), e['term']))
    boundaries = [0, 1000, 3000, 6000, 9000, len(eligible)]
    assert boundaries == sorted(set(boundaries))
    rng = random.Random(20261104)
    bands, items = [], []
    for band in range(5):
        population = eligible[boundaries[band]:boundaries[band+1]]
        bands.append({'size': len(population)})
        for e in rng.sample(population, 100):
            items.append({'id': e['term'], 'band': band, 'meaning': clean(e['meaning']),
                          'pos': e['pos'], 'ipa': e['ipa'],
                          'distractors': [clean(v) for v in e['distractors']]})
    band_by_term = {e['term']: next(b for b in range(5) if i < boundaries[b+1]) for i, e in enumerate(eligible)}
    books = []
    for book in catalog['books']:
        terms = [catalog['entries'][i]['term'] for i in book['words']]
        counts = [sum(band_by_term.get(t) == b for t in terms) for b in range(5)]
        books.append({'id': book['id'], 'title': book['title'], 'level': book['level'],
                      'total': len(terms), 'bands': counts})
    result = {'version': 1, 'sourceSha256': SOURCE_SHA, 'population': len(eligible),
              'bands': bands, 'books': books, 'items': items}
    target = ROOT / 'cle/src/lib/vocabulary-assessment-bank.json'
    target.write_text(json.dumps(result, ensure_ascii=False, separators=(',', ':'))+'\n', encoding='utf-8')
    print(f'{len(items)} items; {len(eligible)} eligible headwords; {target.stat().st_size} bytes')


if __name__ == '__main__':
    build(Path(sys.argv[1]))
