"""Extract Open Food Facts products sold in Azerbaijan and neighbouring markets from the full food.parquet.

The result (.local/stores/off-regional.jsonl) feeds typical.py: medians of real labels for everyday products such as
kefir 2.5 % or cottage cheese 5 %. Only explicit per-100 g/ml label values are kept; nothing is estimated here.
Needs PyArrow (python -m pip install --target .local/off-deps pyarrow). Run:
PYTHONPATH=.local/off-deps python tools/catalog/stores/off_regional.py .local/off/food.parquet
"""
import json
import sys
from pathlib import Path

import pyarrow.parquet as pq

ROOT = Path(__file__).resolve().parents[3]
COUNTRIES = {'en:azerbaijan', 'en:russia', 'en:ukraine', 'en:belarus', 'en:kazakhstan', 'en:georgia', 'en:turkey',
             'en:uzbekistan', 'en:armenia', 'en:kyrgyzstan', 'en:moldova'}
KEYS = {'energy-kcal': 'kcal', 'proteins': 'protein', 'fat': 'fat', 'carbohydrates': 'carbs', 'fiber': 'fiber'}
COLUMNS = ['code', 'product_name', 'brands', 'categories_tags', 'countries_tags', 'nutriments', 'nutrition_data_per', 'quantity']


def main(path):
    out = ROOT / '.local/stores/off-regional.jsonl'
    out.parent.mkdir(parents=True, exist_ok=True)
    pf = pq.ParquetFile(path)
    kept = 0
    with out.open('w', encoding='utf-8') as f:
        for g in range(pf.num_row_groups):
            for row in pf.read_row_group(g, columns=COLUMNS).to_pylist():
                if not COUNTRIES.intersection(row['countries_tags'] or []):
                    continue
                values = {}
                for n in row['nutriments'] or []:
                    key = KEYS.get(n.get('name'))
                    if key and n.get('100g') is not None:
                        values[key] = n['100g']
                if 'kcal' not in values:
                    continue
                names = {p['lang']: p['text'] for p in row['product_name'] or [] if p.get('text')}
                f.write(json.dumps({'code': row['code'], 'names': names, 'brands': row['brands'], 'categories': row['categories_tags'] or [],
                                    'countries': row['countries_tags'], 'per': row['nutrition_data_per'], 'quantity': row['quantity'],
                                    'n': values}, ensure_ascii=False) + '\n')
                kept += 1
            if g % 20 == 0:
                print(f'group {g}/{pf.num_row_groups}, kept {kept}', flush=True)
    print('done', kept, flush=True)


if __name__ == '__main__':
    main(sys.argv[1])
