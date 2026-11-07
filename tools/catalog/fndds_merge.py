"""Merge translated FNDDS batches into i18n/fndds-names-ru.json and cut the next batches from what is left.

Batches live in .local/fndds-batches: bNN.txt holds "n|English" lines, rNN.json the Russian {"n": text}, and
index.json maps n to the catalog id. Run after translating a batch: python tools/catalog/fndds_merge.py [batch size]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BATCHES = ROOT / '.local/fndds-batches'
NAMES = Path(__file__).with_name('i18n') / 'fndds-names-ru.json'


def main():
    names = json.loads(NAMES.read_text(encoding='utf-8')) if NAMES.exists() else {}
    if (BATCHES / 'index.json').exists():
        index = json.loads((BATCHES / 'index.json').read_text(encoding='utf-8'))
        for r in sorted(BATCHES.glob('r*.json')):
            for n, text in json.loads(r.read_text(encoding='utf-8')).items():
                names[index[n]] = text.strip()
            r.unlink()
    NAMES.write_text(json.dumps(dict(sorted(names.items(), key=lambda kv: int(kv[0].split(':')[1]))), ensure_ascii=False, indent=0), encoding='utf-8')
    slim = next(iter(json.loads((ROOT / 'tools/catalog/raw/fndds-slim.json').read_text(encoding='utf-8')).values()))
    todo = sorted(((f'fndds:{f["fdcId"]}', f['description']) for f in slim if f'fndds:{f["fdcId"]}' not in names), key=lambda kv: kv[1].lower())
    size = int(sys.argv[1]) if len(sys.argv) > 1 else 450
    for old in BATCHES.glob('b*.txt'):
        old.unlink()
    index = {}
    for b in range(0, len(todo), size):
        lines = []
        for i, (k, v) in enumerate(todo[b:b + size]):
            index[b + i] = k
            lines.append(f'{b + i}|{v}')
        (BATCHES / f'b{b // size:02d}.txt').write_text('\n'.join(lines), encoding='utf-8')
    (BATCHES / 'index.json').write_text(json.dumps(index), encoding='utf-8')
    print(f'translated {len(names)}, left {len(todo)} in {len(list(BATCHES.glob("b*.txt")))} batches')


if __name__ == '__main__':
    main()
