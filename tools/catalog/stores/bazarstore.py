"""Collect the food assortment of Bazarstore (bazarstore.az, a Baku supermarket chain) with barcodes.

Category pages give the product links of each food department; each product page carries schema.org data with the
name, brand and GTIN. The site publishes no nutrition values, so the catalog takes them from Open Food Facts by
barcode (stores/match.py). Output: .local/stores/bazarstore.jsonl, one product per line; reruns resume.
Usage: python tools/catalog/stores/bazarstore.py [delay seconds, default 0.6] [parallel requests, default 4]
"""
import html
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.local/stores'
BASE = 'https://bazarstore.az'
# Food departments; household goods, cosmetics and pet food are left out.
DEPARTMENTS = ['sud-seher-yemeyi', 'temel-qida', 'et-toyuq-baliq', 'meyve-terevez', 'atisdirmaliq', 'ickiler', 'tort-bismeler-corek']
UA = 'Mozilla/5.0 (compatible; HealthChecker-catalog/1.0; one request per second)'
DELAY = float(sys.argv[1]) if len(sys.argv) > 1 else 0.6
WORKERS = int(sys.argv[2]) if len(sys.argv) > 2 else 4


def get(url, tries=3):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept-Language': 'az'})
            with urllib.request.urlopen(req, timeout=40) as r:
                return r.read().decode('utf-8', 'replace')
        except Exception as e:  # network hiccup: wait and retry
            if attempt == tries - 1:
                print('failed', url, e, flush=True)
                return ''
            time.sleep(5 * (attempt + 1))
        finally:
            time.sleep(DELAY)


def listing():
    path = OUT / 'bazarstore-links.json'
    if path.exists():
        return json.loads(path.read_text(encoding='utf-8'))
    links = {}
    for dep in DEPARTMENTS:
        page = 1
        while True:
            s = get(f'{BASE}/{dep}?pagenumber={page}')
            items = re.findall(r'<h2 class="product-title">\s*<a href="([^"]+)"', s)
            for href in items:
                links.setdefault(href, dep)
            last = re.search(r'data-page="(\d+)"[^>]*>Son', s)
            print(dep, page, len(items), flush=True)
            if not items or not last or page >= int(last.group(1)):
                break
            page += 1
    path.write_text(json.dumps(links, ensure_ascii=False), encoding='utf-8')
    return links


def product(href, dep):
    s = get(BASE + href)
    data = next((json.loads(m) for m in re.findall(r'<script type="application/ld\+json">(.*?)</script>', s, re.S)
                 if '"Product"' in m), None)
    bar = re.search(r'<div class="breadcrumb">(.*?)</ul>', s, re.S)
    crumbs = [html.unescape(c).strip() for c in re.findall(r'<a href="/[^"]+">\s*<span>([^<]+)</span>', bar.group(1))] if bar else []
    if not data:
        return {'url': href, 'department': dep, 'error': 'no product data'}
    brand = data.get('brand')
    brand = brand[0].get('name') if isinstance(brand, list) and brand else (brand or {}).get('name') if isinstance(brand, dict) else None
    return {'url': href, 'department': dep, 'name': data.get('name'), 'brand': brand, 'gtin': data.get('gtin'),
            'sku': data.get('sku'), 'categories': crumbs, 'price': (data.get('offers') or {}).get('price')}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    links = listing()
    out = OUT / 'bazarstore.jsonl'
    done = set()
    if out.exists():
        done = {json.loads(line)['url'] for line in out.read_text(encoding='utf-8').splitlines() if line.strip()}
    todo = [(h, d) for h, d in links.items() if h not in done]
    print(f'{len(links)} products, {len(todo)} to fetch', flush=True)
    from concurrent.futures import ThreadPoolExecutor
    with out.open('a', encoding='utf-8') as f, ThreadPoolExecutor(WORKERS) as pool:
        for i, row in enumerate(pool.map(lambda t: product(*t), todo)):
            f.write(json.dumps(row, ensure_ascii=False) + '\n')
            f.flush()
            if i % 200 == 0:
                print('fetched', i, flush=True)
    print('done', flush=True)


if __name__ == '__main__':
    main()
