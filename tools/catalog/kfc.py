"""KFC as sold in Baku (kfc.az menu, November 2026): one catalog food per menu item, count variants as portions.

KFC Azerbaijan publishes no nutrition values. Each item is modelled from its kfc.az description with USDA SR Legacy
ingredients, including USDA's laboratory analyses of KFC chicken (strips, wings, drumsticks, popcorn chicken, coleslaw).
Where KFC Ukraine publishes the same item (kfc.ua: weight and energy per portion, read 2026-11-20), that weight and
energy are kept; protein and carbohydrate come from the model scaled to the official weight, fat is the Atwater
remainder (4/4/9). The other items take every value from the model. The cards say which way each number was obtained,
and the portion weight stays editable.

Run: python tools/catalog/kfc.py  (writes tools/catalog/kfc-foods.json, read by build_catalog.py)
"""
import json
from pathlib import Path

HERE = Path(__file__).parent
CATALOG = HERE.parent.parent / 'app/src/main/assets/catalog/foods.json'

TORT, BUN = 'usda:175037', 'fndds:2707657'
STRIP, POP, WING, DRUM = 'usda:170341', 'usda:170737', 'usda:170747', 'usda:170349'
BREAST_OR, THIGH_OR, DRUM_OR = 'usda:170744', 'usda:170346', 'usda:170745'
LET, TOM, CUC, ONION, PICK, PEPPER, MUSH, GARLIC = 'usda:169248', 'usda:170457', 'usda:168409', 'usda:170000', 'usda:168558', 'usda:170108', 'usda:169251', 'usda:169230'
MAYO, WATER, CHEESE, HASH, FRIES, CORN, SLAW = 'usda:171009', 'usda:173647', 'usda:171290', 'usda:173298', 'usda:170698', 'usda:169999', 'usda:170340'
KETCH, BBQ, MUST, SRIR, CHSAUCE, BEEF, MOZZ = 'usda:168556', 'usda:174523', 'usda:172234', 'usda:171186', 'usda:171191', 'usda:171798', 'fndds:2705817'
FPIE, CPIE, MUFF_BLUE, MUFF_CHOC, COOKIE, CHEESECAKE, NAPOLEON, STRAW = 'usda:172784', 'usda:173240', 'usda:172765', 'fndds:2707832', 'fndds:2707909', 'fndds:2707862', 'local:dish-napoleon', 'usda:167762'

NAMES = {
    TORT: ('пшеничная тортилья', 'flour tortilla'), BUN: ('булочка', 'bun'), STRIP: ('куриное филе в панировке (стрипсы KFC)', 'breaded chicken fillet (KFC strips)'),
    POP: ('байтсы (попкорн-курица KFC)', 'bites (KFC popcorn chicken)'), WING: ('крыло KFC в панировке', 'breaded KFC wing'),
    DRUM: ('ножка KFC в панировке', 'breaded KFC drumstick'), BREAST_OR: ('грудка KFC', 'KFC breast'), THIGH_OR: ('бедро KFC', 'KFC thigh'),
    DRUM_OR: ('ножка KFC', 'KFC drumstick'), LET: ('салат айсберг', 'iceberg lettuce'), TOM: ('помидор', 'tomato'), CUC: ('огурец', 'cucumber'),
    ONION: ('лук', 'onion'), PICK: ('солёный огурец', 'pickle'), PEPPER: ('сладкий перец', 'sweet pepper'), MUSH: ('грибы', 'mushrooms'),
    GARLIC: ('чеснок', 'garlic'), MAYO: ('майонез', 'mayonnaise'), WATER: ('вода', 'water'), CHEESE: ('плавленый сыр', 'processed cheese'),
    HASH: ('хашбраун', 'hash brown'), FRIES: ('картофель фри', 'french fries'), CORN: ('кукуруза', 'corn'), SLAW: ('коул-слоу KFC', 'KFC coleslaw'),
    KETCH: ('кетчуп', 'ketchup'), BBQ: ('соус барбекю', 'barbecue sauce'), MUST: ('горчица', 'mustard'), SRIR: ('острый соус', 'hot sauce'),
    CHSAUCE: ('сырный соус', 'cheese sauce'), BEEF: ('говяжья котлета', 'beef patty'), MOZZ: ('сыр в панировке', 'breaded cheese'),
    FPIE: ('жареный пирожок с ягодами', 'fried berry pie'), CPIE: ('жареный пирожок с вишней', 'fried cherry pie'), MUFF_BLUE: ('маффин с ягодами', 'berry muffin'),
    MUFF_CHOC: ('шоколадный маффин', 'chocolate muffin'), COOKIE: ('печенье с шоколадом', 'chocolate cookie'), CHEESECAKE: ('фруктовый чизкейк', 'fruit cheesecake'),
    NAPOLEON: ('торт «Наполеон»', 'Napoleon cake'), STRAW: ('клубника', 'strawberry'),
}

def sauce(g):  # KFC sandwich sauce: mayonnaise-type, about 400 kcal/100 g
    return [[MAYO, round(g * 0.6, 1)], [WATER, round(g * 0.4, 1)]]

UA = 'kfc.ua'
# id, Russian, English, Azerbaijani, extra search words, ingredients of one unit, official (UA name, grams, kcal for
# `units` units) or None, portions [(Russian, English, units)].
def one(name_ru='1 шт.', name_en='1 pc'):
    return [(name_ru, name_en, 1)]
def pieces(*counts):
    return [(f'{n} шт.', f'{n} pcs', n) for n in counts]

ITEMS = [
    # Sandwiches and rolls
    ('twister-original', 'Твистер оригинальный', 'Twister Original', 'Tvister acısız', 'твистер twister tvister',
     [[TORT, 55], [STRIP, 68], [LET, 15], [TOM, 25], *sauce(22)], ('Твістер Оригінальний', 185, 512.0, 1), one()),
    ('twister-spicy', 'Твистер острый', 'Twister Spicy', 'Tvister acılı', 'твистер twister tvister острый acili',
     [[TORT, 55], [STRIP, 62], [LET, 15], [TOM, 25], *sauce(22)], ('Твістер Гострий', 181, 477.4, 1), one()),
    ('i-twister', 'Ай-твистер', 'i-Twister', 'I-tvister', 'ай твистер i twister мини',
     [[TORT, 35], [STRIP, 34], [LET, 8], [TOM, 15], *sauce(12)], None, one()),
    ('twister-fresher-original', 'Твистер Фрешер оригинальный', 'Original Twister Fresher', 'Tvister Freşer Orijinal', 'твистер фрешер twister fresher freser',
     [[TORT, 55], [STRIP, 68], [LET, 15], [TOM, 25], [CUC, 20], *sauce(20)], None, one()),
    ('twister-fresher-spicy', 'Твистер Фрешер острый', 'Spicy Twister Fresher', 'Tvister Freşer Acılı', 'твистер фрешер twister fresher freser острый acili',
     [[TORT, 55], [STRIP, 62], [LET, 15], [TOM, 25], [CUC, 20], *sauce(20)], None, one()),
    ('boxmaster-original', 'Боксмастер оригинальный', 'Boxmaster Original', 'Boksmaster acısız', 'боксмастер boxmaster boksmaster',
     [[TORT, 60], [STRIP, 100], [HASH, 50], [CHEESE, 15], [TOM, 20], [LET, 12], *sauce(22)], ('Боксмайстер Оригінальний', 279, 736.8, 1), one()),
    ('boxmaster-spicy', 'Боксмастер острый', 'Boxmaster Spicy', 'Boksmaster acılı', 'боксмастер boxmaster boksmaster острый acili',
     [[TORT, 60], [STRIP, 97], [HASH, 50], [CHEESE, 15], [TOM, 20], [LET, 12], *sauce(22)], ('Боксмайстер Гострий', 276, 720.4, 1), one()),
    ('mega-roll-original', 'Мега Ролл оригинальный', 'Mega Roll Original', 'Meqa Roll Orijinal', 'мега ролл mega roll meqa',
     [[TORT, 60], [STRIP, 102], [FRIES, 40], [PICK, 10], [LET, 12], *sauce(25)], None, one()),
    ('mega-roll-spicy', 'Мега Ролл острый', 'Mega Roll Spicy', 'Meqa Roll Acılı', 'мега ролл mega roll meqa острый acili',
     [[TORT, 60], [STRIP, 93], [FRIES, 40], [PICK, 10], [LET, 12], *sauce(25)], None, one()),
    ('veggie-twister', 'Вегетарианский твистер с сыром', 'Vegetarian Twister Cheese', 'Vegeterian Tvister Cheese', 'вегетарианский твистер сыр vegetarian twister cheese',
     [[TORT, 55], [MOZZ, 70], [LET, 15], [TOM, 25], *sauce(20)], None, one()),
    ('longer', 'Лонгер', 'Longer', 'Longer', 'лонгер longer',
     [[BUN, 45], [STRIP, 34], [LET, 8], *sauce(12)], None, one()),
    # Burgers
    ('sanders-original', 'Сандерс бургер оригинальный', 'Sanders Burger Original', 'Sanders Burger acısız', 'сандерс sanders бургер burger',
     [[BUN, 65], [STRIP, 85], [LET, 15], *sauce(20)], ('Сандерс Класік', 185, 470.2, 1), one()),
    ('sanders-spicy', 'Сандерс бургер острый', 'Sanders Burger Spicy', 'Sanders Burger acılı', 'сандерс sanders бургер burger острый acili зингер',
     [[BUN, 65], [STRIP, 82], [LET, 15], *sauce(20)], ('Сандерс Зінгер', 182, 453.8, 1), one()),
    ('big-sanders-original', 'Биг Сандерс бургер оригинальный', 'Big Sanders Burger Original', 'Biq Sanders Burger acısız', 'биг сандерс big sanders biq бургер burger',
     [[BUN, 75], [STRIP, 120], [LET, 18], [TOM, 20], [CHEESE, 15], *sauce(25)], None, one()),
    ('big-sanders-spicy', 'Биг Сандерс бургер острый', 'Big Sanders Burger Spicy', 'Biq Sanders Burger acılı', 'биг сандерс big sanders biq бургер burger острый acili',
     [[BUN, 75], [STRIP, 115], [LET, 18], [TOM, 20], [CHEESE, 15], *sauce(25)], None, one()),
    ('tower-original', 'Тауэр бургер оригинальный', 'Tower Burger Original', 'Tauer Burger acısız', 'тауэр тауер tower tauer бургер burger',
     [[BUN, 65], [STRIP, 85], [HASH, 40], [CHEESE, 15], [LET, 10], *sauce(20)], ('Тауер Бургер Оригінальний', 235, 645.5, 1), one()),
    ('tower-spicy', 'Тауэр бургер острый', 'Tower Burger Spicy', 'Tauer Burger acılı', 'тауэр тауер tower tauer бургер burger острый acili',
     [[BUN, 65], [STRIP, 81], [HASH, 40], [CHEESE, 15], [LET, 10], *sauce(20)], ('Тауер Бургер Гострий', 231, 629.1, 1), one()),
    ('double-crispy-original', 'Дабл Криспи оригинальный', 'Double Crispy Original', 'Dabl Krispi acısız', 'дабл криспи double crispy dabl krispi бургер burger',
     [[BUN, 65], [STRIP, 120], [CHEESE, 15], [LET, 10], *sauce(20)], None, one()),
    ('double-crispy-spicy', 'Дабл Криспи острый', 'Double Crispy Spicy', 'Dabl Krispi acılı', 'дабл криспи double crispy dabl krispi бургер burger острый acili',
     [[BUN, 65], [STRIP, 115], [CHEESE, 15], [LET, 10], *sauce(20)], None, one()),
    ('fresher-burger-original', 'Фрешер бургер оригинальный', 'Original Fresher Burger', 'Freşer Burger Orijinal', 'фрешер фреш fresher fresh freser bürger бургер burger',
     [[BUN, 70], [STRIP, 90], [LET, 15], [TOM, 25], [CUC, 15], *sauce(20)], None, one()),
    ('fresher-burger-spicy', 'Фрешер бургер острый', 'Spicy Fresher Burger', 'Freşer Burger Acılı', 'фрешер фреш fresher fresh freser бургер burger острый acili',
     [[BUN, 70], [STRIP, 86], [LET, 15], [TOM, 25], [CUC, 15], *sauce(20)], None, one()),
    ('bbq-burger-original', 'BBQ бургер оригинальный', 'Original BBQ Burger', 'BBQ Burger Orijinal', 'барбекю bbq barbekü бургер burger',
     [[BUN, 70], [STRIP, 90], [CHEESE, 15], [ONION, 10], [BBQ, 20], [LET, 10]], None, one()),
    ('bbq-burger-spicy', 'BBQ бургер острый', 'Spicy BBQ Burger', 'BBQ Burger Acılı', 'барбекю bbq barbekü бургер burger острый acili',
     [[BUN, 70], [STRIP, 86], [CHEESE, 15], [ONION, 10], [BBQ, 20], [LET, 10]], None, one()),
    ('chefburger-deluxe-original', 'Шефбургер Делюкс оригинальный', 'Chefburger Deluxe Original', 'Şefburger Delüks Orijinal', 'шефбургер делюкс chefburger deluxe şefburger',
     [[BUN, 60], [STRIP, 75], [LET, 15], [TOM, 20], [CHEESE, 15], *sauce(20)], None, one()),
    ('chefburger-deluxe-spicy', 'Шефбургер Делюкс острый', 'Chefburger Deluxe Spicy', 'Şefburger Delüks Acılı', 'шефбургер делюкс chefburger deluxe şefburger острый acili',
     [[BUN, 60], [STRIP, 72], [LET, 15], [TOM, 20], [CHEESE, 15], *sauce(20)], None, one()),
    ('chefburger-buffalo-ranch', 'Шефбургер Buffalo Ranch', 'Buffalo Ranch Chefburger', 'Buffalo Ranch Şefburger', 'шефбургер баффало ранч chefburger buffalo ranch şefburger',
     [[BUN, 60], [STRIP, 72], [LET, 15], *sauce(20), [SRIR, 5]], None, one()),
    ('chefburger-pickle-ranch', 'Шефбургер Pickle Ranch', 'Pickle Ranch Chefburger', 'Pickle Ranch Şefburger', 'шефбургер пикл ранч chefburger pickle ranch şefburger огурец',
     [[BUN, 60], [STRIP, 75], [LET, 15], [PICK, 15], *sauce(20)], None, one()),
    ('zinger', 'Зингер', 'Zinger', 'Zinger', 'зингер zinger бургер burger острый',
     [[BUN, 50], [STRIP, 60], [LET, 10], *sauce(15)], None, one()),
    ('cheeseburger', 'Чизбургер', 'Cheeseburger', 'Çizburger', 'чизбургер cheeseburger çizburger говядина',
     [[BUN, 50], [BEEF, 45], [CHEESE, 12], [PICK, 7], [ONION, 3], [KETCH, 5], [MUST, 2]], ('Чізбургер', 124, 319.3, 1), one()),
    # Chicken
    ('strips-original', 'Стрипсы оригинальные', 'Strips Original', 'Strips Orijinal', 'стрипсы стрипс strips strips ədəd',
     [[STRIP, 34]], ('3 стріпси Оригінальні', 102, 303.65, 3), pieces(3, 6, 9)),
    ('strips-spicy', 'Стрипсы острые', 'Strips Spicy', 'Strips Acılı', 'стрипсы стрипс strips острые acili ədəd',
     [[STRIP, 31]], ('3 стріпси Гострі', 93, 255.2, 3), pieces(3, 6, 9)),
    ('wings-spicy', 'Крылышки острые', 'Spicy Wings', 'Qanad Acılı', 'крылья крылышки wings qanad острые hot spicy ədəd',
     [[WING, 25]], ('3 крила', 75, 214.13, 3), pieces(3, 6, 9)),
    ('drumsticks-spicy', 'Ножки острые', 'Spicy Drumsticks', 'Bud Acılı', 'ножки ножка голень drumstick bud острые ədəd',
     [[DRUM, 84]], ('1 ніжка Оригінальна', 84, 203.87, 1), pieces(1, 2, 3)),
    ('chicken-pieces', 'Куриные кусочки', 'Chicken Pieces', 'Toyuq Tikə', 'куриные кусочки курица на кости chicken pieces toyuq tike ədəd',
     [[BREAST_OR, 37], [THIGH_OR, 37], [DRUM_OR, 36]], None, pieces(1, 3, 6)),
    ('bites', 'Байтсы', 'Bites', 'Bayts', 'байтсы байтс попкорн bites bayts popcorn',
     [[POP, 45]], None, [('S (≈90 г)', 'S (~90 g)', 2), ('M (≈135 г)', 'M (~135 g)', 3), ('L (≈270 г)', 'L (~270 g)', 6)]),
    # Sides
    ('fries', 'Картофель фри', 'French Fries', 'Fri kartofu', 'картофель фри картошка fries fri kartof böyük orta kiçik basket баскет',
     [[FRIES, 60]], ('Картопля Фрі стандарт', 120, 404.64, 2), [('Маленький (≈60 г)', 'Small (~60 g)', 1), ('Средний (≈120 г)', 'Medium (~120 g)', 2), ('Баскет Фри (≈240 г)', 'Basket Fries (~240 g)', 4)]),
    ('wedges', 'Картофель по-деревенски', 'Potato Wedges', 'Kəndsayağı kartof', 'картофель по деревенски дольки wedges kəndsayağı kendsayagi kartof',
     [[FRIES, 45]], ('Картопля по-домашньому', 135, 412.09, 3), [('Маленький (≈90 г)', 'Small (~90 g)', 2), ('Средний (≈135 г)', 'Medium (~135 g)', 3)]),
    ('corn', 'Кукуруза', 'Corn', 'Qarğıdalı', 'кукуруза початок corn qargidali',
     [[CORN, 110]], None, [('1 початок (≈110 г)', '1 cob (~110 g)', 1)]),
    ('coleslaw', 'Салат коул-слоу', 'Coleslaw Salad', 'Koulslou salatı', 'коул слоу коулслоу капустный салат coleslaw koulslou',
     [[SLAW, 100]], None, [('1 порция (≈100 г)', '1 serving (~100 g)', 1)]),
    ('cheese-sticks', 'Сырные стики', 'Cheese Sticks', 'Pendirli stiklər', 'сырные стики палочки cheese sticks pendirli stikler',
     [[MOZZ, 18]], None, pieces(3, 5, 7)),
    ('cheese-pops', 'Сырные шарики', 'Cheese Pops', 'Pendir toplar', 'сырные шарики cheese pops pendir toplar halloween',
     [[MOZZ, 12]], None, pieces(3, 5, 7, 25)),
    # Sauces: KFC Ukraine sells a 40 g cup; the Baku cup may be smaller.
    ('sauce-ketchup', 'Кетчуп', 'Ketchup', 'Ketçup', 'кетчуп ketchup ketcup соус sous',
     [[KETCH, 40]], ('Соус Кетчуп спешл', 40, 34.4, 1), [('1 порция (≈40 г)', '1 cup (~40 g)', 1)]),
    ('sauce-bbq', 'Соус барбекю', 'Barbecue Sauce', 'Barbekü sousu', 'соус барбекю bbq barbecue barbekü sous',
     [[BBQ, 40]], None, [('1 порция (≈40 г)', '1 cup (~40 g)', 1)]),
    ('sauce-garlic', 'Чесночный соус', 'Garlic Sauce', 'Sarımsaqlı sous', 'чесночный соус garlic sauce sarimsaqli sous',
     [[MAYO, 22], [WATER, 16], [GARLIC, 2]], None, [('1 порция (≈40 г)', '1 cup (~40 g)', 1)]),
    ('sauce-cheese', 'Сырный соус', 'Cheese Sauce', 'Pendirli sous', 'сырный соус cheese sauce pendirli sous',
     [[CHSAUCE, 40]], ('Соус сирний спешл', 40, 132.8, 1), [('1 порция (≈40 г)', '1 cup (~40 g)', 1)]),
    ('sauce-ajika', 'Аджика', 'Ajika', 'Acika', 'аджика ajika acika острый соус sous',
     [[KETCH, 28], [PEPPER, 8], [SRIR, 4]], None, [('1 порция (≈40 г)', '1 cup (~40 g)', 1)]),
    ('sauce-truffle', 'Соус трюфель', 'Truffle Sauce', 'Trufel sousu', 'трюфельный соус трюфель truffle trufel sous',
     [[MAYO, 24], [WATER, 14], [MUSH, 2]], None, [('1 порция (≈40 г)', '1 cup (~40 g)', 1)]),
    # Desserts
    ('napoleon-strawberry', 'Наполеон с клубникой', 'Strawberry Napoleon', 'Çiyələkli Napoleon', 'наполеон клубника napoleon strawberry çiyələkli',
     [[NAPOLEON, 85], [STRAW, 15]], None, [('1 шт. (≈100 г)', '1 pc (~100 g)', 1)]),
    ('pie-cherry', 'Вишнёвый пирожок', 'Cherry Pie', 'Albalılı piroq', 'пирожок вишня вишнёвый cherry pie albalili piroq',
     [[CPIE, 95]], None, [('1 шт. (≈95 г)', '1 pc (~95 g)', 1)]),
    ('pie-wildberry', 'Пирожок лесная ягода', 'Wildberry Pie', 'Giləmeyvəli piroq', 'пирожок лесная ягода ягодный wildberry pie giləmeyvəli piroq',
     [[FPIE, 95]], None, [('1 шт. (≈95 г)', '1 pc (~95 g)', 1)]),
    ('muffin-blackcurrant', 'Маффин со смородиной', 'Blackcurrant Muffin', 'Maffin Qara Qarağat', 'маффин смородина muffin blackcurrant qara qarağat',
     [[MUFF_BLUE, 100]], None, [('1 шт. (≈100 г)', '1 pc (~100 g)', 1)]),
    ('muffin-chocolate', 'Шоколадный маффин', 'Chocolate Muffin', 'Maffin Şokolad', 'маффин шоколадный muffin chocolate şokolad',
     [[MUFF_CHOC, 100]], None, [('1 шт. (≈100 г)', '1 pc (~100 g)', 1)]),
    ('cookie-chocolate', 'Шоколадное печенье', 'Chocolate Cookie', 'Şokoladlı Cookie', 'печенье шоколадное cookie chocolate şokoladlı',
     [[COOKIE, 50]], None, [('1 шт. (≈50 г)', '1 pc (~50 g)', 1)]),
    ('cookie-double-chocolate', 'Печенье Двойной шоколад', 'Double Chocolate Cookie', 'Double Şokoladlı Cookie', 'печенье двойной шоколад cookie double chocolate şokoladlı',
     [[COOKIE, 50]], None, [('1 шт. (≈50 г)', '1 pc (~50 g)', 1)]),
    ('cheesecake-strawberry', 'Клубничный чизкейк', 'Strawberry Cheesecake', 'Çiyələkli Çizkeyk', 'чизкейк клубничный cheesecake strawberry çizkeyk',
     [[CHEESECAKE, 100]], None, [('1 шт. (≈100 г)', '1 pc (~100 g)', 1)]),
]

KEYS = ('kcal', 'protein', 'fat', 'carbs', 'fiber')

def model(ingredients, foods):
    grams = sum(g for _, g in ingredients)
    n = {k: 0.0 for k in KEYS}
    for ident, g in ingredients:
        per = foods[ident]['per100g']
        for k in KEYS:
            if per[k] is None:
                if k == 'fiber':
                    continue
                raise ValueError(f'{ident} has no {k}')
            n[k] += per[k] * g / 100
    return grams, n

def food(item, foods):
    ident, ru, en, az, words, ingredients, official, portions = item
    grams, n = model(ingredients, foods)
    names = ', '.join(dict.fromkeys(NAMES[i][0] for i, _ in ingredients))
    names_en = ', '.join(dict.fromkeys(NAMES[i][1] for i, _ in ingredients))
    if official:
        ua_name, ua_grams, ua_kcal, units = official
        unit = ua_grams / units
        k = unit / grams
        protein, carbs, fiber = n['protein'] * k, n['carbs'] * k, n['fiber'] * k
        kcal = ua_kcal / units
        fat = (kcal - 4 * protein - 4 * carbs) / 9
        remainder = fat >= 0
        if not remainder:
            # A lean item lighter than its model (a less sweet ketchup): every nutrient follows the official energy.
            r = kcal / (n['kcal'] * k)
            protein, carbs, fiber, fat = protein * r, carbs * r, fiber * r, n['fat'] * k * r
        grams = unit
        how = 'жиры — остаток энергии по Этуотеру (4/4/9)' if remainder else 'все нутриенты пересчитаны под официальную энергию'
        how_en = 'fat is the Atwater remainder (4/4/9)' if remainder else 'every nutrient is scaled to the official energy'
        source = (f'KFC (Баку, меню kfc.az). KFC Азербайджан не публикует пищевую ценность. Вес и энергия — официальные данные '
                  f'KFC Украины (kfc.ua, «{ua_name}»: {ua_grams:g} г, {ua_kcal:g} ккал). Белки и углеводы — по составу (USDA: {names}), '
                  f'{how}. В Баку вес может отличаться.')
        source_en = (f'KFC (Baku, kfc.az menu). KFC Azerbaijan publishes no nutrition values. Weight and energy are KFC Ukraine\'s '
                     f'official figures (kfc.ua, "{ua_name}": {ua_grams:g} g, {ua_kcal:g} kcal). Protein and carbohydrate come from '
                     f'the composition (USDA: {names_en}); {how_en}. The Baku weight may differ.')
    else:
        kcal, protein, fat, carbs, fiber = (n[k] for k in KEYS)
        source = (f'KFC (Баку, меню kfc.az). KFC Азербайджан не публикует пищевую ценность — оценка по составу из описания '
                  f'на kfc.az (USDA, включая анализ блюд KFC): {names}, {grams:g} г на порцию. Вес в ресторане может отличаться.')
        source_en = (f'KFC (Baku, kfc.az menu). KFC Azerbaijan publishes no nutrition values — estimated from the kfc.az '
                     f'description (USDA, including its analyses of KFC food): {names_en}, {grams:g} g per serving. The restaurant weight may differ.')
    k = 100 / grams
    out_portions = []
    for p_ru, p_en, units in portions:
        g = round(grams * units)
        out_portions.append({'name': p_ru if '≈' in p_ru else f'{p_ru} (≈{g} г)', 'nameEn': p_en if '~' in p_en else f'{p_en} (~{g} g)', 'grams': g})
    return {
        'id': f'local:kfc-{ident}', 'name': f'{ru} · KFC', 'nameEn': f'{en} · KFC',
        'aliases': [f'kfc кфс кфц {words} {az.lower()} {en.lower()}'],
        'per100g': {'kcal': round(kcal * k, 2), 'protein': round(protein * k, 2), 'fat': round(fat * k, 2), 'carbs': round(carbs * k, 2), 'fiber': round(fiber * k, 2)},
        'portions': out_portions, 'density': None, 'source': source, 'sourceEn': source_en,
    }

def build(foods):
    return [food(item, foods) for item in ITEMS]

if __name__ == '__main__':
    foods = {f['id']: f for f in json.loads(CATALOG.read_text(encoding='utf-8'))}
    out = build(foods)
    (HERE / 'kfc-foods.json').write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding='utf-8')
    for f in out:
        p = f['portions'][0]
        print(f"{f['name']:<42} {p['grams']:>4} г  {f['per100g']['kcal'] * p['grams'] / 100:6.0f} ккал  Б {f['per100g']['protein'] * p['grams'] / 100:5.1f}  Ж {f['per100g']['fat'] * p['grams'] / 100:5.1f}  У {f['per100g']['carbs'] * p['grams'] / 100:5.1f}")
