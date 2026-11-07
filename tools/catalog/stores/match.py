"""Turn the collected Bazarstore assortment into catalog foods using Open Food Facts label values by barcode.

Bazarstore publishes names, brands, categories and GTINs but no nutrition. A product enters the catalog only when
its GTIN is in the bundled Open Food Facts snapshot with explicit per-100 g values that pass an Atwater sanity
check; nothing is estimated. The shelf name is kept (it is what people see in Baku), units are written in Russian,
and the Russian or English Open Food Facts name becomes a search alias.
Run after bazarstore.py: python tools/catalog/stores/match.py  (writes tools/catalog/store-foods.json)
"""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
STORE = ROOT / '.local/stores/bazarstore.jsonl'
OFF = ROOT / '.local/off/products.sqlite'
UNITS = {'q': 'г', 'qr': 'г', 'qram': 'г', 'gr': 'г', 'g': 'г', 'kq': 'кг', 'kg': 'кг', 'ml': 'мл', 'l': 'л', 'lt': 'л', 'litr': 'л', 'əd': 'шт.', 'ədəd': 'шт.'}


# The Russian kind of product, so a shelf name reads in a Russian list: "Йогурт · Aktivia Yoqurt 110 г Çiyələk".
# First by a word of the shelf name, then by the store category, then by the department.
KIND_WORDS = [
    (r'düyü vafli|duyu vafli|xlebs|хлебц', 'Хлебцы'), (r'suxari|сухари', 'Сухарики'), (r'rulet', 'Рулет'), (r'haribo', 'Мармелад'),
    (r'pie\b|piroq', 'Пирожное'),
    (r'kefir', 'Кефир'), (r'ayran', 'Айран'), (r'qatıq|qatiq', 'Катык'), (r'yoq?urt|yogurt|yoğurt', 'Йогурт'), (r'xama', 'Сметана'),
    (r'kəsmik|kesmik|творог', 'Творог'), (r'sırok|sirok', 'Сырок'), (r'pendir|peynir|kaşar|kasar|cheese|mozzarella|brinza', 'Сыр'),
    (r'dondurma|ice ?cream', 'Мороженое'), (r'süd|sud\b|milk|молоко', 'Молоко'), (r'kərə yağ|kere yag|butter', 'Сливочное масло'),
    (r'şokolad|sokolad|chocolate', 'Шоколад'), (r'peçenye|pecenye|biscuit|cookie', 'Печенье'), (r'vafli|gofret|wafer', 'Вафли'),
    (r'keks|biskvit|cake', 'Кекс'), (r'tort', 'Торт'), (r'kraker|cracker', 'Крекеры'), (r'çips|cips|chips', 'Чипсы'),
    (r'jelibon|marmelad', 'Мармелад'), (r'konfet|karamel', 'Конфеты'), (r'makaron|spagetti|spaghetti|pasta|əriştə|eriste|lapşa', 'Макароны'),
    (r'yulaf|oat', 'Овсяные хлопья'), (r'düyü|duyu|rice', 'Рис'), (r'qarabaşaq|qarabasaq|grechka', 'Гречка'), (r'mərci|merci|lentil', 'Чечевица'),
    (r'mayonez|mayonnaise', 'Майонез'), (r'ketçup|ketcup|ketchup', 'Кетчуп'), (r'sous|sauce', 'Соус'), (r'kolbasa|salyami|sucuk|sosis', 'Колбаса'),
    (r'sosiska', 'Сосиски'), (r'tuna|ton balığı', 'Тунец'), (r'qəhvə|qehve|coffee|kofe|amerikano|kapuçino|kapucino|latte|espresso', 'Кофе'), (r'çay|cay\b|tea\b', 'Чай'),
    (r'şirə|sire|juice|nektar', 'Сок'), (r'limonad|kola|cola|fanta|sprite', 'Газированный напиток'), (r'\bsu\b|water', 'Вода'),
    (r'enerji|energy', 'Энергетик'), (r'zeytun yağ|olive oil', 'Оливковое масло'), (r'yağ|yag\b|oil', 'Масло'), (r'zeytun|olive', 'Оливки'),
    (r'tum\b|seeds', 'Семечки'), (r'fındıq|findiq|badam|qoz|püstə|puste|keşyu|nuts', 'Орехи'), (r'bal\b|honey', 'Мёд'),
    (r'mürəbbə|murebbe|cem\b|jam', 'Варенье'), (r'kakao|cocoa', 'Какао'), (r'\bsup\b|şorba|soup', 'Суп'), (r'un\b|flour', 'Мука'),
    (r'şəkər|seker|sugar', 'Сахар'), (r'çörək|corek|bread|lavaş|lavas', 'Хлеб'), (r'müsli|musli|granola|corn ?flakes|qarğıdalı', 'Сухой завтрак'),
    (r'bar\b|batonçik', 'Батончик'), (r'krokan', 'Козинак'),
]
KIND_CATEGORIES = {'Şokolad barlar': 'Шоколадный батончик', 'Konservlər': 'Консервы', 'Konserva': 'Консервы', 'Dəniz məhsulları': 'Морепродукты',
                   'Dəniz məhsulu konservləri': 'Рыбные консервы', 'Dondurulmuş tərəvəzlər': 'Замороженные овощи', 'Dondurulmuş meyvələr': 'Замороженные фрукты',
                   'Dondurulmuş dəniz məhsulları': 'Замороженные морепродукты', 'Dondurulmuş Hazır Qida Məhsulları': 'Замороженные полуфабрикаты',
                   'Bişmiş kolbasa': 'Варёная колбаса', 'Diyetik məhsullar': 'Диетический продукт', 'Diabetik məhsullar': 'Продукт для диабетиков',
                   'Qlutensiz məhsullar': 'Безглютеновый продукт', 'Tort ləvazimatları': 'Для выпечки', 'Bişmələr': 'Выпечка', 'Hədiyyəlik şokolad': 'Шоколад'}
KIND_DEPARTMENTS = {'sud-seher-yemeyi': 'Молочное и завтрак', 'temel-qida': 'Бакалея', 'et-toyuq-baliq': 'Мясо, птица, рыба',
                    'meyve-terevez': 'Фрукты и овощи', 'atisdirmaliq': 'Снеки и сладости', 'ickiler': 'Напиток', 'tort-bismeler-corek': 'Выпечка'}


def kind(row):
    """The kind word nearest the start wins: "Vafli Rulet … Qatılaşdırılmış Süd" is a wafer, not milk."""
    low = az_lower(row['name']).replace('ı', 'i')  # shelf names write i as I, which lowers to ı
    hits = [(m.start(), -len(m.group(0)), ru) for pattern, ru in KIND_WORDS for m in [re.search(pattern, low)] if m]
    if hits:
        return min(hits)[2]
    for c in reversed(row.get('categories', [])):
        if c in KIND_CATEGORIES:
            return KIND_CATEGORIES[c]
    return KIND_DEPARTMENTS.get(row.get('department'), 'Товар')


def az_lower(word):
    return word.replace('İ', 'i').replace('I', 'ı').lower()


def shelf_name(raw):
    """"AKTİVİA YOQURT 110 Q ÇİYƏLƏK" -> "Aktivia Yoqurt 110 г Çiyələk"."""
    words = []
    for w in re.sub(r'\s+', ' ', raw).strip().split(' '):
        low = az_lower(w)
        # Store names often spell i with a plain I; Latin brand words read better with i than with ı.
        if 'ı' in low and not re.search(r'[əğşçöü]', low):
            low = low.replace('ı', 'i')
        if low.rstrip('.') in UNITS and words and re.search(r'\d$', words[-1]):
            words.append(UNITS[low.rstrip('.')])
        elif re.fullmatch(r'\d+([.,]\d+)?(q|kq|ml|l|gr|g)', low):
            num, unit = re.fullmatch(r'(\d+(?:[.,]\d+)?)(\D+)', low).groups()
            words.append(f'{num} {UNITS[unit]}')
        else:
            first = 'İ' if low[:1] == 'i' and re.search(r'[əğşçöüı]', low) else low[:1].upper()
            words.append(first + low[1:])
    return ' '.join(words)


def plausible(kcal, protein, fat, carbs):
    if kcal is None or kcal <= 0 or kcal > 900 or None in (protein, fat, carbs):
        return False
    return abs(4 * protein + 9 * fat + 4 * carbs - kcal) <= 0.2 * kcal + 5


def build():
    db = sqlite3.connect(OFF)
    seen, out = set(), []
    for line in STORE.open(encoding='utf-8'):
        r = json.loads(line)
        gtin = (r.get('gtin') or '').strip()
        if not gtin.isdigit() or not r.get('name'):
            continue
        code = gtin.zfill(14)
        if code in seen:
            continue
        row = db.execute('SELECT name,brand,kcal,protein,fat,carbs,fiber,basis FROM products WHERE code=?', (code,)).fetchone()
        if not row or row[7] != 'MASS' or not plausible(*row[2:6]):
            continue
        seen.add(code)
        off_name, off_brand, kcal, protein, fat, carbs, fiber = row[:7]
        name = shelf_name(r['name'])
        crumbs = [c for c in r.get('categories', []) if c and not c.startswith(('Ana səhifə', 'Bütün'))]
        aliases = ' '.join(filter(None, [r['name'], off_name, off_brand, r.get('brand'), ' '.join(crumbs)]))
        out.append({
            'id': 'store:' + code, 'name': f'{kind(r)} · {name}', 'nameEn': name, 'aliases': [aliases], 'common': False, 'barcode': code,
            'per100g': {'kcal': round(kcal, 2), 'protein': round(protein, 2), 'fat': round(fat, 2), 'carbs': round(carbs, 2),
                        'fiber': round(fiber, 2) if fiber is not None else (0.0 if carbs == 0 else None)},
            'portions': [],
            'source': f'Товар Bazarstore (Баку), штрихкод {gtin}; пищевая ценность на 100 г — с этикетки по Open Food Facts · {code}.',
            'sourceEn': f'Bazarstore (Baku) product, barcode {gtin}; nutrition per 100 g from its label via Open Food Facts · {code}.',
        })
    return out


if __name__ == '__main__':
    items = build()
    (ROOT / 'tools/catalog/store-foods.json').write_text(json.dumps(items, ensure_ascii=False, indent=0), encoding='utf-8')
    print(len(items), 'store products with label nutrition')
    for i in items[:12]:
        print(i['name'], '|', i['per100g'])
