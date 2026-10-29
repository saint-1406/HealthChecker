"""Build an offline GTIN SQLite from the complete official OFF Parquet snapshot.

python build_barcodes.py SOURCE.parquet --revision HASH --date YYYY-MM-DD
Requires pyarrow. Product data and this derived database: ODbL 1.0 / DbCL 1.0.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import json
import math
from pathlib import Path
import sqlite3


def normalize_gtin(value):
    code = str(value or '').strip()
    if len(code) not in (8, 12, 13, 14) or not code.isascii() or not code.isdigit() or not any(c != '0' for c in code):
        return None
    total = sum(int(c) * (3 if i % 2 == 0 else 1) for i, c in enumerate(reversed(code[:-1])))
    return code.zfill(14) if (10 - total % 10) % 10 == int(code[-1]) else None


def number(value, maximum):
    if isinstance(value, bool):
        return None
    try:
        n = float(value)
        return n if math.isfinite(n) and 0 <= n <= maximum else None
    except (ValueError, TypeError, OverflowError):
        return None


def title(value):
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, list):
        names = {n.get('lang'): n.get('text', '').strip() for n in value if isinstance(n, dict) and isinstance(n.get('text'), str)}
        return next((names[k] for k in ('ru', 'az', 'en', 'tr', 'main') if names.get(k)), next((v for v in names.values() if v), ''))
    return ''


def product_record(p):
    code = normalize_gtin(p.get('code'))
    if code is None:
        return None
    name = title(p.get('product_name')) or title(p.get('generic_name'))
    brand = p.get('brands') if isinstance(p.get('brands'), str) else ''
    result = dict(code=code, name=(name or f'Product {code}')[:150], brand=brand.strip()[:45],
                  kcal=None, protein=None, fat=None, carbs=None, fiber=None, package_amount=None, basis='MASS')
    nutrition = p.get('nutrition')
    if isinstance(nutrition, dict):
        aggregate = nutrition.get('aggregated_set') or {}
        per = aggregate.get('per')
        result['basis'] = 'VOLUME' if per == '100ml' else 'MASS'
        factor = 20 if result['basis'] == 'VOLUME' else 1
        nutrients = aggregate.get('nutrients') or {} if aggregate.get('preparation') == 'as_sold' and per in ('100g', '100ml') else {}

        def nutrient(key, unit, maximum):
            n = nutrients.get(key) or {}
            return number(n.get('value'), maximum) if n.get('unit') == unit and n.get('source') in ('packaging', 'manufacturer') else None

        result['kcal'] = nutrient('energy-kcal', 'kcal', 1000 * factor) if 'energy-kcal' in nutrients else (lambda n: n / 4.184 if n is not None else None)(nutrient('energy-kj', 'kJ', 4184 * factor))
        for dest, key in [('protein', 'proteins'), ('fat', 'fat'), ('carbs', 'carbohydrates'), ('fiber', 'fiber')]:
            result[dest] = nutrient(key, 'g', 100 * factor)
    else:
        # The official Parquet export uses canonical as-sold *_100g values (not prepared_*).
        # A missing/serving basis is kept as an identity-only card rather than guessed.
        per = p.get('nutrition_data_per')
        result['basis'] = 'VOLUME' if per == '100ml' else 'MASS'
        factor = 20 if result['basis'] == 'VOLUME' else 1
        ns = p.get('nutriments') or {}
        if isinstance(ns, list):
            ns = {f"{n['name']}{suffix}": n.get(src) for n in ns if isinstance(n, dict) and n.get('name')
                  for suffix, src in [('_100g', '100g'), ('_unit', 'unit')]}

        def nutrient(key, unit, maximum):
            if per not in ('100g', '100ml') or p.get('no_nutrition_data'):
                return None
            if ns.get(key + '_unit') not in (None, '', unit):
                return None
            return number(ns.get(key + '_100g'), maximum)

        if ns.get('energy-kcal_100g') is not None:
            result['kcal'] = nutrient('energy-kcal', 'kcal', 1000 * factor)
        else:
            energy = nutrient('energy-kj', 'kJ', 4184 * factor)
            if energy is None:
                energy = nutrient('energy', 'kJ', 4184 * factor)
            result['kcal'] = energy / 4.184 if energy is not None else None
        for dest, key in [('protein', 'proteins'), ('fat', 'fat'), ('carbs', 'carbohydrates'), ('fiber', 'fiber')]:
            result[dest] = nutrient(key, 'g', 100 * factor)
    # Protein + fat + carbohydrates cannot exceed the full mass; fibre can be part of carbohydrates.
    # Volume values are normalized only after the user provides density (up to 20 g/ml).
    if sum(result[k] or 0 for k in ('protein', 'fat', 'carbs')) > 100.5 * (20 if result['basis'] == 'VOLUME' else 1):
        for key in ('protein', 'fat', 'carbs', 'fiber'):
            result[key] = None
    unit = str(p.get('product_quantity_unit') or '').lower()
    compatible = unit in (('ml', 'l') if result['basis'] == 'VOLUME' else ('g', 'kg'))
    amount = number(p.get('product_quantity'), 100000)
    if compatible and amount is not None:
        amount *= 1000 if unit in ('kg', 'l') else 1
        if 0 < amount <= 100000:
            result['package_amount'] = amount
    result['quality'] = (10 if result['kcal'] is not None else 0) + sum(result[k] is not None for k in ('protein', 'fat', 'carbs', 'fiber')) + 2 * bool(name) + bool(brand)
    result['modified'] = int(number(p.get('last_modified_t'), 10**12) or 0)
    return result


def digest(path):
    with Path(path).open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def build(source, output, revision, snapshot_date, expected_sha256):
    import pyarrow.parquet as pq
    source_hash = digest(source)
    if expected_sha256 and source_hash != expected_sha256:
        raise ValueError('Source SHA-256 does not match the pinned OFF export')
    parquet = pq.ParquetFile(source)
    columns = [c for c in ['code', 'product_name', 'generic_name', 'brands', 'nutrition', 'nutriments',
        'nutrition_data_per', 'no_nutrition_data', 'product_quantity', 'product_quantity_unit',
        'countries_tags', 'last_modified_t'] if c in parquet.schema_arrow.names]
    output.mkdir(parents=True, exist_ok=True)
    work = source.parent / 'products.sqlite'
    if work.exists():
        work.unlink()  # only this deterministic build output, never user data
    db = sqlite3.connect(work)
    db.executescript('''PRAGMA page_size=4096; PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;
        PRAGMA cache_size=-32768; PRAGMA user_version=1;
        CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
        CREATE TABLE products(code TEXT PRIMARY KEY,name TEXT NOT NULL,brand TEXT NOT NULL,
            kcal REAL,protein REAL,fat REAL,carbs REAL,fiber REAL,package_amount REAL,basis TEXT NOT NULL,
            quality INTEGER NOT NULL,modified INTEGER NOT NULL) WITHOUT ROWID;''')
    fields = ['code', 'name', 'brand', 'kcal', 'protein', 'fat', 'carbs', 'fiber', 'package_amount', 'basis', 'quality', 'modified']
    sql = 'INSERT INTO products VALUES(' + ','.join(':' + f for f in fields) + ') ON CONFLICT(code) DO UPDATE SET ' + ','.join(f'{f}=excluded.{f}' for f in fields[1:]) + ' WHERE excluded.quality>products.quality OR (excluded.quality=products.quality AND excluded.modified>products.modified)'
    stats = Counter()
    for batch in parquet.iter_batches(batch_size=8192, columns=columns, use_threads=False):
        records = []
        for p in batch.to_pylist():
            stats['source_rows'] += 1
            row = product_record(p)
            if row is None:
                stats['invalid_gtin_rows'] += 1
                continue
            stats['valid_gtin_rows'] += 1
            if 'en:azerbaijan' in (p.get('countries_tags') or []):
                stats['azerbaijan_tagged_valid_rows'] += 1
            records.append(row)
        db.executemany(sql, records)
        db.commit()
        if stats['source_rows'] % 131072 == 0:
            print(f"Imported {stats['source_rows']:,}/{parquet.metadata.num_rows:,}", flush=True)
    count, known = db.execute('SELECT count(*),count(kcal) FROM products').fetchone()
    stats.update(products=count, with_calories=known, duplicate_alias_rows=stats['valid_gtin_rows'] - count)
    meta = {'revision': revision, 'snapshot_date': snapshot_date, 'source_sha256': source_hash,
            'source_url': f'https://huggingface.co/datasets/openfoodfacts/product-database/resolve/{revision}/food.parquet',
            'license': 'ODbL-1.0', 'contents_license': 'DbCL-1.0', 'attribution': 'Contains data from Open Food Facts, available under the Open Database License.'}
    db.executemany('INSERT INTO metadata VALUES(?,?)', meta.items())
    db.commit()
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    db.close()
    archive = source.parent / 'products.sqlite.gz'
    with work.open('rb') as src, archive.open('wb') as dst:
        with gzip.GzipFile(filename='', fileobj=dst, mode='wb', compresslevel=6, mtime=0) as compressed:
            while data := src.read(1024 * 1024):
                compressed.write(data)
    parts = []
    with archive.open('rb') as src:
        while data := src.read(32 * 1024 * 1024):
            name = f'products.sqlite.gz.part{len(parts):03d}'
            (output / name).write_bytes(data)
            parts.append(name)
    manifest = {'revision': revision, 'snapshotDate': snapshot_date, 'products': count, 'withCalories': known,
                'uncompressedBytes': work.stat().st_size, 'sha256': digest(work), 'parts': parts}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    report = {**meta, **dict(stats), 'source_rows_declared': parquet.metadata.num_rows,
              'database_bytes': work.stat().st_size, 'database_sha256': digest(work),
              'gzip_bytes': archive.stat().st_size, 'gzip_sha256': digest(archive),
              'part_sha256': {name: digest(output / name) for name in parts},
              'nutrition_policy': 'Explicit as-sold 100g/100ml values only; no serving/prepared/estimated substitutions. Unknown stays null. Every valid GTIN identity is retained.'}
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    assert stats['source_rows'] == parquet.metadata.num_rows
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('--output', type=Path, default=Path('app/src/main/assets/catalog/barcodes'))
    parser.add_argument('--revision', required=True)
    parser.add_argument('--date', required=True)
    parser.add_argument('--sha256', default='')
    args = parser.parse_args()
    build(args.source, args.output, args.revision, args.date, args.sha256)
