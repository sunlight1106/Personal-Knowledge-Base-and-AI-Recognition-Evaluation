"""Build additive V49 books from pinned ECDICT and original supplemental entries.

Usage: python scripts/build-vocabulary-expansion.py .runtime/ecdict.csv
No new dictionary downloads, no changes to old catalogs or user progress.
"""
import csv
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'database/vocabulary'


def build(source):
    manifest = json.loads((DATA / 'catalog.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(source.read_bytes()).hexdigest() == manifest['sourceSha256']
    raw = (DATA / 'v17.json.gz').read_bytes()
    assert hashlib.sha256(raw).hexdigest() == manifest['catalogSha256']
    base = json.loads(gzip.decompress(raw))
    entries = list(base['entries'])
    index = {e['term']: i for i, e in enumerate(entries)}
    topics = json.loads((DATA / 'topics-v49.json').read_text(encoding='utf-8'))
    supplement = json.loads((DATA / 'supplement-v49.json').read_text(encoding='utf-8'))
    requested = set(' '.join(t['terms'] for t in topics).split())
    frequency = {}
    rows = {}
    with source.open(encoding='utf-8') as stream:
        for row in csv.DictReader(stream):
            if row['word'] in index or row['word'] in requested:
                ranks = [int(row[k]) for k in ('frq', 'bnc') if row[k].isdigit() and int(row[k]) > 0]
                frequency[row['word']] = min(ranks or [999999])
                rows[row['word']] = row
    for term in sorted(requested - index.keys()):
        if term in supplement:
            entry = dict(term=term, **supplement[term], source='original')
        else:
            row = rows[term]
            lines = row['translation'].replace('\\n', '\n').splitlines()
            meaning = next((re.sub(r'^\[[^]]+\]\s*', '', v).strip() for v in lines if re.search('[\u4e00-\u9fff]', v) and len(v) <= 160), '')
            match = re.match(r'^(n|v|vt|vi|a|adj|ad|adv|prep|pron|conj|num|interj)\.', meaning)
            pos = ({'a':'adj','ad':'adv','vt':'v','vi':'v'}.get(match[1],match[1])+'.') if match else ''
            ipa = row['phonetic'].strip()
            assert ipa and meaning, term
            entry = dict(term=term, ipa='/'+ipa+'/', pos=pos, meaning=meaning, source='ECDICT')
        chars = set(re.findall('[\u4e00-\u9fff]', entry['meaning']))
        choices = []
        for candidate in base['entries']:
            other = set(re.findall('[\u4e00-\u9fff]', candidate['meaning']))
            if other and not chars & other and candidate['pos'] == entry['pos'] and len(candidate['meaning']) <= 70:
                choices.append(candidate['meaning']); chars |= other
                if len(choices) == 3: break
        if len(choices) < 3:
            for candidate in base['entries']:
                other = set(re.findall('[\u4e00-\u9fff]', candidate['meaning']))
                if other and not chars & other and len(candidate['meaning']) <= 70:
                    choices.append(candidate['meaning']); chars |= other
                    if len(choices) == 3: break
        assert len(choices) == 3, term
        entry['distractors'] = choices
        index[term] = len(entries); entries.append(entry)
    def rank(i):
        term = entries[i]['term']
        return frequency.get(term, 999999), term
    common = sorted((i for term, i in index.items() if re.fullmatch('[a-z]+', term) and frequency.get(term, 999999) < 999999), key=rank)
    originals = {b['id']: b for b in base['books']}
    books = []
    def add(slug, title, level, description, words):
        words = sorted(set(words), key=rank)
        assert len(words) >= 80, slug
        books.append(dict(id='vocab-ec-'+slug, title=title, level=level, description=description, words=words))
    add('common-1000', '通用英语 · 常用 1000', '通用分级', '从内置词库按通用语料词频选取常见 1000 词，适合建立阅读基础；并非某个考试的官方词表。', common[:1000])
    add('common-3000', '通用英语 · 阅读 3000', '通用分级', '在常用 1000 的基础上扩展至 3000 词，适合过渡到较长的日常阅读。同词进度自动接续。', common[:3000])
    add('cet4-core', '四级英语 · 常用 1200', '四级', '从内置四级词书按通用词频选取 1200 词，适合先打基础，再进入综合词书；词频不是历年试题出现次数。', originals['vocab-ec-cet4']['words'][:1200])
    bridge = [i for i in originals['vocab-ec-cet6']['words'] if i not in set(originals['vocab-ec-cet4']['words'])]
    add('cet6-bridge', '六级英语 · 进阶衔接', '六级', '选取内置六级词书中四级词书未收录的词，减少重复浏览。已有词汇的学习记录仍然共用。', bridge)
    omitted = {}
    for topic in topics:
        terms = set(topic['terms'].split())
        omitted[topic['slug']] = sorted(terms - index.keys())
        add(topic['slug'], topic['title'], topic['level'], topic['description'], [index[t] for t in terms if t in index])
    assert len({b['id'] for b in books}) == len(books) == 16
    assert not {b['id'] for b in books} & originals.keys()
    result = dict(baseCatalogSha256=manifest['catalogSha256'], sourceCommit=manifest['commit'], books=books, entries=entries[len(base['entries']):])
    payload = json.dumps(result, ensure_ascii=False, separators=(',', ':')).encode()
    (DATA / 'v49.json').write_bytes(payload + b'\n')
    report = dict(baseCatalogSha256=manifest['catalogSha256'], catalogSha256=hashlib.sha256(payload+b'\n').hexdigest(),
                  totalEntries=sum(len(b['words']) for b in books), uniqueWords=len({i for b in books for i in b['words']}), newHeadwords=len(result['entries']),
                  books=[{k:b[k] for k in ('id','title','level')} | {'words':len(b['words'])} for b in books],
                  omittedTerms=omitted)
    (DATA / 'expansion-v49-manifest.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    build(Path(sys.argv[1]))
