"""Sushi as served in Baku sushi bars: one catalog food per roll, maki, nigiri, gunkan and sashimi.

Every item is a model of one piece built from USDA SR Legacy ingredients: seasoned rice (about 4 % sugar), a nori
strip, fish, cheese, vegetables, sauces and, for hot rolls, batter, crumbs and absorbed frying oil. For the twelve
rolls from the user's set the user's per-piece ranges are kept: energy, protein and carbohydrate are the middle of the
range and fat is the Atwater remainder; the model gives the piece weight and fibre. The other items take every value
from the model. Restaurants differ, so the cards say how the numbers were obtained and the piece weight stays editable.

Run: python tools/catalog/sushi.py  (writes tools/catalog/sushi-foods.json, read by build_catalog.py)
"""
import json
from pathlib import Path

RICE, SUGAR, NORI = 'usda:168880', 'usda:169655', 'usda:168458'
SALMON, TUNA, EEL, SHRIMP, SURIMI, ROE, SKIPJACK = 'usda:175167', 'usda:175159', 'usda:174194', 'usda:175179', 'usda:173702', 'usda:175132', 'usda:172005'
CREAM, CHEDDAR, CUCUMBER, AVOCADO, WAKAME = 'usda:173418', 'usda:173414', 'usda:168409', 'usda:171705', 'usda:170496'
MAYO, TERIYAKI, SRIRACHA, SESAME, SESAME_OIL, CHIPS = 'usda:171009', 'usda:171167', 'usda:171186', 'usda:170150', 'usda:171016', 'usda:169677'
FLOUR, CRUMBS, OIL, CHICKEN = 'usda:168894', 'usda:174928', 'usda:171025', 'usda:171477'
MISO, TOFU, SCALLION, WATER, SOY = 'usda:172442', 'usda:172476', 'usda:170005', 'usda:173647', 'usda:174277'

# Short ingredient names for the cards: (Russian, English).
NAMES = {
    RICE: ('рис', 'rice'), NORI: ('нори', 'nori'), SALMON: ('лосось', 'salmon'), TUNA: ('тунец', 'tuna'), EEL: ('угорь', 'eel'),
    SHRIMP: ('креветка', 'shrimp'), SURIMI: ('крабовые палочки (сурими)', 'surimi crab'), ROE: ('икра тобико', 'tobiko roe'),
    SKIPJACK: ('стружка бонито', 'bonito flakes'), CREAM: ('сливочный сыр', 'cream cheese'), CHEDDAR: ('сыр', 'cheese'),
    CUCUMBER: ('огурец', 'cucumber'), AVOCADO: ('авокадо', 'avocado'), WAKAME: ('водоросли чука', 'chuka seaweed'),
    MAYO: ('майонез', 'mayonnaise'), TERIYAKI: ('соус унаги', 'unagi sauce'), SRIRACHA: ('острый соус', 'hot sauce'),
    SESAME: ('кунжут', 'sesame'), SESAME_OIL: ('кунжутное масло', 'sesame oil'), CHIPS: ('чипсы', 'potato chips'),
    FLOUR: ('мука кляра', 'batter flour'), CRUMBS: ('панировка', 'crumbs'), OIL: ('масло фритюра', 'frying oil'),
    CHICKEN: ('курица', 'chicken'), MISO: ('паста мисо', 'miso paste'), TOFU: ('тофу', 'tofu'), SCALLION: ('зелёный лук', 'scallion'),
    WATER: ('вода', 'water'), SOY: ('соевый соус', 'soy sauce'),
}

def rice(grams):
    """Sushi rice seasoned with vinegar and sugar; the sugar is about 4 % of the cooked rice."""
    return [[RICE, grams], [SUGAR, round(grams * 0.04, 2)]]

def uramaki(*filling):  # inside-out roll, 8 pieces: about 16 g rice per piece and a quarter of a nori strip
    return rice(16) + [[NORI, 1.5]] + list(filling)
def maki(*filling):  # thin roll, 6 pieces
    return rice(12) + [[NORI, 1.5]] + list(filling)
def hot(*filling):  # tempura roll: batter, crumbs and the oil it soaks up
    return uramaki(*filling) + [[FLOUR, 3], [CRUMBS, 2], [OIL, 3]]

# id, Russian name, English name, search words, kind, ingredients per piece, user's ranges per piece or None.
ITEMS = [
    ('philadelphia', 'Ролл Филадельфия', 'Philadelphia roll', 'филадельфия filadelfiya philadelphia',
     'roll', uramaki([SALMON, 7], [CREAM, 6], [CUCUMBER, 2]), {'kcal': (50, 65), 'protein': (2, 3), 'carbs': (5, 7)}),
    ('california', 'Ролл Калифорния', 'California roll', 'калифорния kaliforniya california краб',
     'roll', uramaki([SURIMI, 6], [AVOCADO, 4], [CUCUMBER, 3], [ROE, 3], [MAYO, 1]), {'kcal': (40, 60), 'protein': (1, 2), 'carbs': (6, 8)}),
    ('alaska', 'Ролл Аляска', 'Alaska roll', 'аляска alyaska alaska',
     'roll', uramaki([SALMON, 6], [AVOCADO, 4], [CUCUMBER, 3], [SESAME, 1]), {'kcal': (45, 65), 'protein': (2, 3), 'carbs': (5, 8)}),
    ('sake-maki', 'Саке маки с лососем', 'Sake maki with salmon', 'саке маки sake maki лосось losos',
     'maki', maki([SALMON, 5]), {'kcal': (25, 35), 'protein': (1, 2), 'carbs': (4, 6)}),
    ('ebi-maki', 'Эби маки с креветкой', 'Ebi maki with shrimp', 'эби маки ebi maki креветка krevet',
     'maki', maki([SHRIMP, 5], [MAYO, 0.5]), {'kcal': (25, 40), 'protein': (1, 2), 'carbs': (4, 6)}),
    ('spicy-cheese', 'Пряный сырный ролл', 'Spicy cheese roll', 'пряный сырный острый спайси spicy cheese pendirli',
     'roll', uramaki([CREAM, 8], [MAYO, 2], [SRIRACHA, 0.5], [CUCUMBER, 3]), {'kcal': (55, 80), 'protein': (1, 2), 'carbs': (6, 9)}),
    ('chips', 'Чипсы ролл', 'Chips roll', 'чипсы чипс chips çips',
     'roll', uramaki([CREAM, 5], [SURIMI, 4], [CUCUMBER, 3], [CHIPS, 4]), {'kcal': (55, 80), 'protein': (1, 3), 'carbs': (7, 10)}),
    ('bonito', 'Ролл Бонито', 'Bonito roll', 'бонито bonito',
     'roll', uramaki([SALMON, 5], [CREAM, 5], [CUCUMBER, 2], [SKIPJACK, 1]), {'kcal': (45, 65), 'protein': (2, 3), 'carbs': (5, 8)}),
    ('chuka', 'Чука ролл', 'Chuka roll', 'чука chuka водоросли вегетарианский',
     'roll', uramaki([WAKAME, 8], [SESAME_OIL, 0.5], [SESAME, 0.5], [CUCUMBER, 3]), {'kcal': (35, 55), 'protein': (0.5, 1.5), 'carbs': (6, 9)}),
    ('kani', 'Кани ролл с сурими', 'Kani roll with surimi', 'кани kani краб сурими surimi',
     'roll', uramaki([SURIMI, 8], [MAYO, 1.5], [CUCUMBER, 3]), {'kcal': (35, 55), 'protein': (1, 2), 'carbs': (6, 9)}),
    ('hot-ebi', 'Горячий ролл Эби с креветкой', 'Hot ebi roll with shrimp', 'горячий темпура эби креветка isti ebi hot tempura',
     'hot', hot([SHRIMP, 6], [CREAM, 4]), {'kcal': (65, 90), 'protein': (2, 3), 'carbs': (7, 11)}),
    ('hot-salmon', 'Горячий ролл с лососем', 'Hot roll with salmon', 'горячий темпура лосось isti losos hot tempura',
     'hot', hot([SALMON, 7], [CREAM, 4]), {'kcal': (65, 95), 'protein': (2, 4), 'carbs': (7, 11)}),
    # Other rolls usual on Baku menus; every value from the model.
    ('canada', 'Ролл Канада', 'Canada roll', 'канада kanada canada угорь лосось',
     'roll', uramaki([SALMON, 4], [EEL, 4], [CREAM, 5], [CUCUMBER, 2], [TERIYAKI, 1], [SESAME, 0.3]), None),
    ('dragon', 'Ролл Дракон с угрём', 'Dragon roll with eel', 'дракон dragon угорь unagi',
     'roll', uramaki([EEL, 6], [AVOCADO, 5], [CUCUMBER, 3], [TERIYAKI, 1.5], [SESAME, 0.3]), None),
    ('green-dragon', 'Ролл Зелёный дракон', 'Green dragon roll', 'зелёный дракон green dragon авокадо креветка',
     'roll', uramaki([SHRIMP, 4], [AVOCADO, 7], [CREAM, 4], [CUCUMBER, 2], [ROE, 1]), None),
    ('baked-salmon', 'Запечённый ролл с лососем', 'Baked roll with salmon', 'запечённый запеченный лосось bişmiş baked',
     'roll', uramaki([SALMON, 5], [CREAM, 4], [CUCUMBER, 2], [MAYO, 3], [CHEDDAR, 3], [ROE, 1]), None),
    ('baked-crab', 'Запечённый ролл с крабом', 'Baked crab roll', 'запечённый запеченный краб сурими bişmiş baked crab',
     'roll', uramaki([SURIMI, 8], [AVOCADO, 3], [CUCUMBER, 2], [MAYO, 3], [CHEDDAR, 2]), None),
    ('hot-chicken', 'Горячий ролл с курицей', 'Hot roll with chicken', 'горячий темпура курица toyuq isti hot chicken',
     'hot', hot([CHICKEN, 7], [CREAM, 4], [CUCUMBER, 2]), None),
    ('kappa-maki', 'Каппа маки с огурцом', 'Kappa maki with cucumber', 'каппа маки огурец kappa xiyar',
     'maki', maki([CUCUMBER, 6], [SESAME, 0.3]), None),
    ('avocado-maki', 'Маки с авокадо', 'Avocado maki', 'авокадо маки avocado avokado',
     'maki', maki([AVOCADO, 6]), None),
    ('tekka-maki', 'Тэкка маки с тунцом', 'Tekka maki with tuna', 'тэкка текка маки тунец tuna tekka',
     'maki', maki([TUNA, 5]), None),
    ('unagi-maki', 'Унаги маки с угрём', 'Unagi maki with eel', 'унаги маки угорь unagi',
     'maki', maki([EEL, 5], [TERIYAKI, 1], [SESAME, 0.3]), None),
    ('nigiri-salmon', 'Нигири с лососем', 'Salmon nigiri', 'нигири суши лосось sake nigiri',
     'nigiri', rice(18) + [[SALMON, 12]], None),
    ('nigiri-eel', 'Нигири с угрём', 'Eel nigiri', 'нигири суши угорь unagi nigiri',
     'nigiri', rice(18) + [[EEL, 11], [TERIYAKI, 1], [NORI, 0.5]], None),
    ('nigiri-shrimp', 'Нигири с креветкой', 'Shrimp nigiri', 'нигири суши креветка ebi nigiri',
     'nigiri', rice(18) + [[SHRIMP, 10]], None),
    ('nigiri-tuna', 'Нигири с тунцом', 'Tuna nigiri', 'нигири суши тунец maguro nigiri',
     'nigiri', rice(18) + [[TUNA, 12]], None),
    ('gunkan-spicy-salmon', 'Гункан со спайси лососем', 'Spicy salmon gunkan', 'гункан спайси острый лосось gunkan spicy',
     'gunkan', rice(15) + [[NORI, 2], [SALMON, 8], [MAYO, 2], [SRIRACHA, 0.5]], None),
    ('gunkan-tobiko', 'Гункан с икрой тобико', 'Tobiko gunkan', 'гункан икра тобико tobiko gunkan',
     'gunkan', rice(15) + [[NORI, 2], [ROE, 10]], None),
    ('chuka-salad', 'Салат чука', 'Chuka seaweed salad', 'чука салат водоросли chuka salad wakame',
     'salad', [[WAKAME, 85], [SESAME_OIL, 5], [SUGAR, 3], [SESAME, 2], [SOY, 5]], None),
    ('miso-soup', 'Мисо-суп', 'Miso soup', 'мисо суп miso soup',
     'soup', [[WATER, 213], [MISO, 18], [TOFU, 15], [WAKAME, 2], [SCALLION, 2]], None),
]
# Sashimi is plain raw fish: the USDA values with a slice as the portion.
SASHIMI = [('sashimi-salmon', SALMON, 'Сашими из лосося', 'Salmon sashimi', 'сашими лосось sashimi salmon losos'),
           ('sashimi-tuna', TUNA, 'Сашими из тунца', 'Tuna sashimi', 'сашими тунец sashimi tuna')]

COMMON = 'суши ролл роллы sushi suşi roll rol'

def grams_label(g):
    return f'{g:.0f}'

def portions(kind, weight):
    w = grams_label(weight)
    if kind in ('roll', 'hot'):
        return [{'name': f'1 кусочек (≈{w} г)', 'nameEn': f'1 piece (~{w} g)', 'grams': weight},
                {'name': f'Ролл 8 шт. (≈{grams_label(weight * 8)} г)', 'nameEn': f'Roll of 8 (~{grams_label(weight * 8)} g)', 'grams': round(weight * 8, 2)}]
    if kind == 'maki':
        return [{'name': f'1 кусочек (≈{w} г)', 'nameEn': f'1 piece (~{w} g)', 'grams': weight},
                {'name': f'Ролл 6 шт. (≈{grams_label(weight * 6)} г)', 'nameEn': f'Roll of 6 (~{grams_label(weight * 6)} g)', 'grams': round(weight * 6, 2)}]
    if kind == 'nigiri':
        return [{'name': f'1 нигири (≈{w} г)', 'nameEn': f'1 nigiri (~{w} g)', 'grams': weight},
                {'name': f'Пара, 2 шт. (≈{grams_label(weight * 2)} г)', 'nameEn': f'Pair of 2 (~{grams_label(weight * 2)} g)', 'grams': round(weight * 2, 2)}]
    if kind == 'gunkan':
        return [{'name': f'1 гункан (≈{w} г)', 'nameEn': f'1 gunkan (~{w} g)', 'grams': weight}]
    if kind == 'salad':
        return [{'name': f'Порция (≈{w} г)', 'nameEn': f'Serving (~{w} g)', 'grams': weight}]
    return [{'name': f'Пиала (≈{w} мл)', 'nameEn': f'Bowl (~{w} ml)', 'grams': weight}]

def composition(ingredients, en=False):
    shown = [(i, g) for i, g in ingredients if i != SUGAR]
    return ', '.join(f'{NAMES[i][1 if en else 0]} {g:g} {"g" if en else "г"}' for i, g in shown)

def rng(pair, en=False):
    a, b = pair
    sep = '–'
    text = f'{a:g}{sep}{b:g}'
    return text if en else text.replace('.', ',')

def build():
    out = []
    for ident, ru, en, words, kind, ingredients, given in ITEMS:
        # Whole grams read better in the piece-weight field; the per-piece values stay those of the model.
        weight = round(sum(g for _, g in ingredients))
        what_ru = 'кусочка' if kind in ('roll', 'hot', 'maki') else 'штуки' if kind in ('nigiri', 'gunkan') else 'порции'
        what_en = 'piece' if kind in ('roll', 'hot', 'maki', 'nigiri', 'gunkan') else 'serving'
        if given:
            basis = {'ingredients': ingredients, 'grams': weight, 'fat': 'remainder', 'fiber': 'ingredients'}
            for key, pair in given.items():
                basis[key] = round((pair[0] + pair[1]) / 2, 2)
            source = (f'Суши-бары Баку, оценка на 1 кусочек: {rng(given["kcal"])} ккал, белки {rng(given["protein"])} г, '
                      f'углеводы {rng(given["carbs"])} г — взята середина. Жиры — остаток энергии по Этуотеру (4/4/9). '
                      f'Вес кусочка и клетчатка — по составу (USDA): {composition(ingredients)}, рис с сахаром. Вес в заведении может отличаться.')
            source_en = (f'Baku sushi bars, estimate per piece: {rng(given["kcal"], True)} kcal, protein {rng(given["protein"], True)} g, '
                         f'carbs {rng(given["carbs"], True)} g — the middle is used. Fat is the Atwater energy remainder (4/4/9). '
                         f'Piece weight and fibre from the composition (USDA): {composition(ingredients, True)}, seasoned rice. Restaurants vary.')
        else:
            basis = {'ingredients': ingredients, 'grams': weight}
            source = (f'Оценка по типичному составу {what_ru} в суши-барах Баку, все значения по USDA: {composition(ingredients)}'
                      f'{", рис с сахаром" if RICE in dict(ingredients) else ""}. Вес и начинка в заведении могут отличаться.')
            source_en = (f'Estimate from a typical {what_en} in Baku sushi bars, all values from USDA: {composition(ingredients, True)}'
                         f'{", seasoned rice" if RICE in dict(ingredients) else ""}. Weight and filling vary by restaurant.')
        out.append({'id': 'local:sushi-' + ident, 'name': ru, 'nameEn': en, 'aliases': [words + ' ' + COMMON if kind not in ('salad', 'soup') else words],
                    'basis': basis, 'portions': portions(kind, weight), 'density': 1.0 if kind == 'soup' else None,
                    'source': source, 'sourceEn': source_en})
    for ident, base, ru, en, words in SASHIMI:
        out.append({'id': 'local:sushi-' + ident, 'base': base, 'name': ru, 'nameEn': en, 'aliases': [words + ' ' + COMMON],
                    'portions': [{'name': '1 кусочек (≈15 г)', 'nameEn': '1 slice (~15 g)', 'grams': 15},
                                 {'name': 'Порция 5 шт. (≈75 г)', 'nameEn': '5 slices (~75 g)', 'grams': 75}],
                    'source': 'Сырая рыба, значения USDA FoodData Central; кусочек сашими около 15 г.',
                    'sourceEn': 'Raw fish, USDA FoodData Central values; a sashimi slice is about 15 g.'})
    return out

if __name__ == '__main__':
    items = build()
    Path(__file__).with_name('sushi-foods.json').write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding='utf-8')
    print(len(items), 'sushi items')
