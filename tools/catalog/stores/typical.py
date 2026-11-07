"""Typical store products of Baku shelves (kefir 2.5 %, cottage cheese 5 %, lavash, pelmeni…) as label medians.

Input: .local/stores/off-regional.jsonl from off_regional.py (Open Food Facts labels sold in Azerbaijan and
neighbouring markets). For each item below the labels are chosen by category tag or name words and, where the
item names a fat share, by the label fat within a window. Labels whose energy disagrees with Atwater factors by
more than 20 % are dropped as typing errors. Each nutrient is the median of the remaining labels; the card says
how many labels were used. Output: tools/catalog/typical-foods.json (read by build_catalog.py).
"""
import json
import re
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
LIQUID = 'liquid'  # values per 100 ml on labels; density ~1 g/ml is used for grams

# id, Russian, English, search words, categories (any), name regex (any language), fat window or None, kind, portions
ITEMS = [
    ('kefir-1', 'Кефир 1%', 'Kefir 1%', 'kefir кефир 1 нежирный', ['en:kefir', 'en:kefir-1'], r'кефир|kefir', (0.7, 1.3), LIQUID, [('Стакан (250 мл)', 'Glass (250 ml)', 250)]),
    ('kefir-2-5', 'Кефир 2,5%', 'Kefir 2.5%', 'kefir кефир 2,5 2.5', ['en:kefir', 'en:kefir-2-5'], r'кефир|kefir', (2.2, 2.8), LIQUID, [('Стакан (250 мл)', 'Glass (250 ml)', 250)]),
    ('kefir-3-2', 'Кефир 3,2%', 'Kefir 3.2%', 'kefir кефир 3,2 3.2 жирный', ['en:kefir'], r'кефир|kefir', (2.9, 3.6), LIQUID, [('Стакан (250 мл)', 'Glass (250 ml)', 250)]),
    ('ryazhenka', 'Ряженка', 'Ryazhenka (baked fermented milk)', 'ряженка ryazhenka', [], r'ряженк|ryazhenka', None, LIQUID, [('Стакан (250 мл)', 'Glass (250 ml)', 250)]),
    ('ayran-store', 'Айран магазинный', 'Store-bought ayran', 'ayran айран тан tan', ['en:ayran', 'en:doogh-and-ayran'], r'\bayran|айран|\btan\b|\bтан\b', None, LIQUID, [('Бутылочка (250 мл)', 'Bottle (250 ml)', 250)]),
    ('milk-1-5', 'Молоко 1,5%', 'Milk 1.5%', 'süd молоко 1,5 1.5', ['en:milks', 'ru:молоко-питьевое'], r'молоко|\bsüd\b|\bsut\b', (1.3, 1.7), LIQUID, [('Стакан (250 мл)', 'Glass (250 ml)', 250)]),
    ('milk-2-5', 'Молоко 2,5%', 'Milk 2.5%', 'süd молоко 2,5 2.5', ['en:milks', 'ru:молоко-питьевое'], r'молоко|\bsüd\b|\bsut\b', (2.3, 2.7), LIQUID, [('Стакан (250 мл)', 'Glass (250 ml)', 250)]),
    ('milk-3-2', 'Молоко 3,2%', 'Milk 3.2%', 'süd молоко 3,2 3.2', ['en:milks', 'ru:молоко-питьевое'], r'молоко|\bsüd\b|\bsut\b', (3.0, 3.6), LIQUID, [('Стакан (250 мл)', 'Glass (250 ml)', 250)]),
    ('tvorog-0', 'Творог обезжиренный (0–1,8%)', 'Fat-free cottage cheese (tvorog 0–1.8%)', 'творог kəsmik обезжиренный нежирный', ['en:quarks', 'en:cottage-cheeses'], r'творог|kəsmik|kesmik', (0, 1.9), None, [('Пачка (180 г)', 'Pack (180 g)', 180)]),
    ('tvorog-5', 'Творог 5%', 'Cottage cheese (tvorog) 5%', 'творог kəsmik 5', ['en:quarks', 'en:cottage-cheeses'], r'творог|kəsmik|kesmik', (4, 6), None, [('Пачка (180 г)', 'Pack (180 g)', 180)]),
    ('tvorog-9', 'Творог 9%', 'Cottage cheese (tvorog) 9%', 'творог kəsmik 9', ['en:quarks', 'en:cottage-cheeses'], r'творог|kəsmik|kesmik', (8, 10), None, [('Пачка (180 г)', 'Pack (180 g)', 180)]),
    ('smetana-15', 'Сметана 15%', 'Sour cream (smetana) 15%', 'сметана xama 15', ['en:smetana', 'en:sour-creams'], r'сметан|xama', (13, 17), None, [('Ст. ложка (20 г)', 'Tablespoon (20 g)', 20)]),
    ('smetana-20', 'Сметана 20%', 'Sour cream (smetana) 20%', 'сметана xama 20', ['en:smetana', 'en:sour-creams'], r'сметан|xama', (18, 22), None, [('Ст. ложка (20 г)', 'Tablespoon (20 g)', 20)]),
    ('smetana-25', 'Сметана 25–30%', 'Sour cream (smetana) 25–30%', 'сметана xama 25 30 жирная', ['en:smetana', 'en:sour-creams'], r'сметан|xama', (23, 32), None, [('Ст. ложка (20 г)', 'Tablespoon (20 g)', 20)]),
    ('syrok', 'Сырок творожный глазированный', 'Glazed curd snack (syrok)', 'сырок глазированный творожный', ['en:curd-snacks'], r'сырок', None, None, [('1 сырок (≈45 г)', '1 bar (~45 g)', 45)]),
    ('yogurt-drink', 'Йогурт питьевой', 'Drinking yogurt', 'йогурт питьевой içməli yoqurt', ['en:drinkable-yogurts', 'en:yogurt-drinks'], r'питьев', None, LIQUID, [('Бутылочка (290 г)', 'Bottle (290 g)', 290)]),
    ('brynza', 'Брынза (белый сыр)', 'Brynza white cheese', 'брынза ağ pendir pendir белый сыр', [], r'брынз|ağ pendir|ag pendir|beyaz peynir', None, None, [('Ломтик (30 г)', 'Slice (30 g)', 30)]),
    ('suluguni', 'Сулугуни', 'Suluguni cheese', 'сулугуни suluquni suluguni', [], r'сулугун|sulu?guni|suluquni', None, None, [('Ломтик (30 г)', 'Slice (30 g)', 30)]),
    ('lavash-store', 'Лаваш магазинный', 'Store-bought lavash', 'lavaş lavash лаваш', [], r'лаваш|lavaş|lavash', None, None, [('1 лист (≈60 г)', '1 sheet (~60 g)', 60)]),
    ('baton', 'Батон нарезной', 'Sliced white loaf (baton)', 'батон нарезной хлеб çörək', [], r'батон(?!ч)', None, None, [('Ломтик (25 г)', 'Slice (25 g)', 25)]),
    ('borodinsky', 'Хлеб бородинский', 'Borodinsky rye bread', 'бородинский хлеб ржаной', [], r'бородинск', None, None, [('Ломтик (30 г)', 'Slice (30 g)', 30)]),
    ('pelmeni-store', 'Пельмени замороженные магазинные', 'Frozen store-bought pelmeni', 'пельмени замороженные pelmeni', ['en:pasta-stuffed-with-meat'], r'пельмен|pelmen', None, None, [('Порция (≈200 г)', 'Portion (~200 g)', 200)]),
    ('vareniki-store', 'Вареники замороженные магазинные', 'Frozen store-bought vareniki', 'вареники замороженные', [], r'вареник', None, None, [('Порция (≈200 г)', 'Portion (~200 g)', 200)]),
    ('sosiski-store', 'Сосиски молочные (магазинные)', 'Milk sausages (frankfurters), store-bought', 'сосиски молочные sosiska', [], r'сосиск', None, None, [('1 сосиска (≈45 г)', '1 sausage (~45 g)', 45)]),
    ('doktorskaya', 'Колбаса варёная «Докторская»', 'Doktorskaya boiled sausage', 'докторская колбаса варёная kolbasa', [], r'докторск', None, None, [('Ломтик (20 г)', 'Slice (20 g)', 20)]),
    ('servelat', 'Сервелат варёно-копчёный', 'Cervelat smoked sausage', 'сервелат колбаса копчёная', [], r'сервелат|servelat', None, None, [('Ломтик (10 г)', 'Slice (10 g)', 10)]),
    ('halva', 'Халва подсолнечная', 'Sunflower halva', 'халва halva подсолнечная', ['en:halva'], r'халв|halva|helva', None, None, [('Кусочек (30 г)', 'Piece (30 g)', 30)]),
    ('sguschenka', 'Сгущённое молоко с сахаром', 'Sweetened condensed milk', 'сгущёнка сгущенка сгущённое молоко', ['en:condensed-milks'], r'сгущ', None, None, [('Ст. ложка (25 г)', 'Tablespoon (25 g)', 25)]),
    ('plombir', 'Мороженое пломбир', 'Plombir ice cream', 'пломбир мороженое dondurma', [], r'пломбир', None, None, [('Стаканчик (≈80 г)', 'Cup (~80 g)', 80)]),
    ('pryanik', 'Пряники', 'Pryaniki spice cakes', 'пряник пряники', ['en:pryaniki', 'en:gingerbreads'], r'пряник', None, None, [('1 пряник (≈35 г)', '1 piece (~35 g)', 35)]),
    ('sushki', 'Сушки и баранки', 'Sushki and baranki rings', 'сушки баранки', [], r'сушк|баранк', None, None, [('1 сушка (≈8 г)', '1 ring (~8 g)', 8)]),
    ('zefir', 'Зефир', 'Zefir (marshmallow)', 'зефир zefir', [], r'зефир', None, None, [('1 зефирина (≈35 г)', '1 piece (~35 g)', 35)]),
    ('vafli', 'Вафли с начинкой', 'Filled wafers', 'вафли vafli', ['en:stuffed-wafers', 'en:filled-wafers', 'en:Filled wafers'], r'вафл|vafli|gofret', None, None, [('1 вафля (≈20 г)', '1 wafer (~20 g)', 20)]),
    ('kvass', 'Квас', 'Kvass', 'квас kvas', ['en:kvass'], r'квас(?!ш)', None, LIQUID, [('Стакан (250 мл)', 'Glass (250 ml)', 250)]),
    ('mayo-67', 'Майонез 67%', 'Mayonnaise 67%', 'майонез mayonez 67', ['en:mayonnaises'], r'майонез|mayonez', (60, 70), None, [('Ст. ложка (15 г)', 'Tablespoon (15 g)', 15)]),
    ('mayo-light', 'Майонез лёгкий (30–50%)', 'Light mayonnaise (30–50%)', 'майонез лёгкий mayonez', ['en:mayonnaises'], r'майонез|mayonez', (25, 52), None, [('Ст. ложка (15 г)', 'Tablespoon (15 g)', 15)]),
    ('ketchup', 'Кетчуп', 'Ketchup', 'кетчуп ketçup ketchup', ['en:ketchup'], r'кетчуп|ketçup|ketchup', None, None, [('Ст. ложка (15 г)', 'Tablespoon (15 g)', 15)]),
    ('semechki', 'Семечки подсолнечника жареные', 'Roasted sunflower seeds', 'семечки tum günəbaxan', ['en:roasted-sunflower-seeds', 'en:sunflower-seeds'], r'семечк|çərəz|tum\b', None, None, [('Горсть (30 г)', 'Handful (30 g)', 30)]),
    ('chips-store', 'Картофельные чипсы (магазинные)', 'Potato crisps, store-bought', 'чипсы çips chips', ['en:potato-crisps'], r'чипс|çips|chips', None, None, [('Пачка (≈90 г)', 'Bag (~90 g)', 90)]),
    ('instant-noodles', 'Лапша быстрого приготовления (сухая)', 'Instant noodles (dry)', 'лапша быстрого приготовления доширак роллтон', ['en:instant-noodles', 'en:instant-noodle-soups'], r'лапша|noodle|əriştə', None, None, [('Пачка (≈70 г)', 'Pack (~70 g)', 70)]),
    ('milk-chocolate', 'Шоколад молочный (плитка)', 'Milk chocolate bar', 'шоколад молочный şokolad', ['en:milk-chocolates'], r'.', None, None, [('Плитка (90 г)', 'Bar (90 g)', 90), ('Долька (≈5 г)', 'Square (~5 g)', 5)]),
]


NO_FIBRE = {'kefir-1', 'kefir-2-5', 'kefir-3-2', 'ryazhenka', 'ayran-store', 'milk-1-5', 'milk-2-5', 'milk-3-2', 'tvorog-0', 'tvorog-5',
            'tvorog-9', 'smetana-15', 'smetana-20', 'smetana-25', 'yogurt-drink', 'brynza', 'suluguni', 'sguschenka', 'plombir',
            'mayo-67', 'mayo-light', 'sosiski-store', 'doktorskaya', 'servelat', 'zefir'}


def labels():
    for line in (ROOT / '.local/stores/off-regional.jsonl').open(encoding='utf-8'):
        yield json.loads(line)


def plausible(n):
    kcal = n.get('kcal')
    parts = [n.get(k) for k in ('protein', 'fat', 'carbs')]
    if kcal is None or kcal <= 0 or any(v is None or v < 0 or v > 100 for v in parts):
        return False
    atwater = 4 * parts[0] + 9 * parts[1] + 4 * parts[2]
    return abs(atwater - kcal) <= 0.2 * kcal + 5


def build():
    rows = [r for r in labels() if plausible(r['n'])]
    out, report = [], []
    for ident, ru, en, words, cats, pattern, fat, kind, portions in ITEMS:
        # The product word must open the name: "Сметана 20%" counts, "чипсы со сметаной" does not.
        rx = re.compile(r'^[^0-9A-Za-zА-Яа-яЁёƏəÜüÖöĞğŞşÇçIıİ]*(?:' + pattern + ')', re.I)
        chosen = []
        for r in rows:
            if not (set(cats) & set(r['categories']) or (pattern != '.' and any(rx.search(n) for n in r['names'].values()))):
                continue
            if cats and pattern == '.' and not set(cats) & set(r['categories']):
                continue
            if fat and not fat[0] <= r['n']['fat'] <= fat[1]:
                continue
            chosen.append(r)
        report.append((ident, len(chosen)))
        if len(chosen) < 5:
            continue
        med = {k: round(statistics.median([r['n'][k] for r in chosen]), 2) for k in ('kcal', 'protein', 'fat', 'carbs')}
        fibers = [r['n']['fiber'] for r in chosen if r['n'].get('fiber') is not None]
        if ident in NO_FIBRE:
            med['fiber'] = 0.0  # milk products and sugar syrups contain no dietary fibre; stray label values are typos
        else:
            med['fiber'] = round(statistics.median(fibers), 2) if len(fibers) >= max(3, len(chosen) * 0.4) else None
        basis = 'на 100 мл, плотность принята ≈1 г/мл' if kind == LIQUID else 'на 100 г'
        out.append({'id': 'local:typical-' + ident, 'name': ru, 'nameEn': en, 'aliases': [words], 'per100g': med,
                    'density': 1.0 if kind == LIQUID else None,
                    'portions': [{'name': a, 'nameEn': b, 'grams': g} for a, b, g in portions],
                    'source': f'Типичные значения: медиана {len(chosen)} этикеток товаров Open Food Facts из Азербайджана и соседних стран ({basis}). У конкретной марки цифры могут отличаться — точнее отсканировать штрихкод.',
                    'sourceEn': f'Typical values: median of {len(chosen)} Open Food Facts labels from Azerbaijan and neighbouring markets ({"per 100 ml, density ~1 g/ml" if kind == LIQUID else "per 100 g"}). A given brand may differ; scanning its barcode is more precise.'})
    return out, report


if __name__ == '__main__':
    items, report = build()
    for ident, n in report:
        print(f'{ident}: {n} labels')
    for i in items:
        print(i['name'], i['per100g'])
    (ROOT / 'tools/catalog/typical-foods.json').write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding='utf-8')
    print(len(items), 'typical items')
