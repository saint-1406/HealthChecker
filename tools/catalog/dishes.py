"""Home and restaurant dishes usual in Baku: Azerbaijani, Russian and other post-Soviet, Georgian and Turkish food.

USDA has no plov, dolma, borscht or pelmeni, so each dish here is a recipe: raw ingredients from USDA SR Legacy in
grams and the weight of the cooked dish (what is left after frying, baking or simmering, or what the rice and
dough soak up). The catalog build sums the ingredients and divides by that weight, exactly like the sushi models.
Home recipes differ, so every card says it is a recipe estimate and lists what went in.

Run: python tools/catalog/dishes.py  (writes tools/catalog/dish-foods.json, read by build_catalog.py)
"""
import json
from pathlib import Path

# Ingredients: id, Russian, English.
I = {}
def ing(key, ident, ru, en):
    I[key] = (ident, ru, en)

for row in [
    ('lamb', 'usda:172545', 'баранина', 'lamb'), ('lamb_mince', 'usda:174370', 'бараний фарш', 'ground lamb'),
    ('lamb_lean', 'usda:172508', 'баранина для шашлыка', 'lamb for kebab'), ('beef', 'usda:171206', 'говядина', 'beef'),
    ('beef_mince', 'usda:174036', 'говяжий фарш', 'ground beef'), ('pork_mince', 'usda:167902', 'свиной фарш', 'ground pork'),
    ('pork', 'usda:167843', 'свиная лопатка', 'pork shoulder'), ('chicken', 'usda:171447', 'курица с кожей', 'chicken with skin'),
    ('chicken_breast', 'usda:171077', 'куриное филе', 'chicken breast'), ('chicken_dark', 'usda:171067', 'куриное бедро', 'chicken thigh'),
    ('chicken_cooked', 'usda:172388', 'курица жареная', 'roasted chicken'), ('chicken_breast_cooked', 'usda:171477', 'куриная грудка запечённая', 'roasted chicken breast'),
    ('liver_lamb', 'usda:172531', 'баранья печень', 'lamb liver'), ('liver_beef', 'usda:169451', 'говяжья печень', 'beef liver'),
    ('tripe', 'usda:170599', 'рубец', 'tripe'), ('shank', 'usda:169441', 'говяжья голяшка', 'beef shank'), ('tallow', 'usda:171400', 'курдючный жир', 'tail fat'),
    ('sturgeon', 'usda:173701', 'осётр', 'sturgeon'), ('carp', 'usda:171952', 'кутум/сазан', 'kutum or carp'),
    ('herring', 'usda:175118', 'сельдь', 'herring'), ('tuna', 'usda:173709', 'тунец консервированный', 'canned tuna'),
    ('surimi', 'usda:171973', 'крабовые палочки', 'crab sticks'),
    ('ham', 'usda:173864', 'ветчина', 'ham'), ('bologna', 'usda:172013', 'варёная колбаса', 'bologna sausage'),
    ('smoked_sausage', 'usda:172954', 'копчёная колбаса', 'smoked sausage'), ('frank', 'usda:173875', 'сосиски', 'frankfurters'),
    ('rice', 'usda:168877', 'рис', 'rice'), ('rice_cooked', 'usda:168878', 'рис варёный', 'cooked rice'),
    ('buckwheat_cooked', 'usda:170686', 'гречка варёная', 'cooked buckwheat'), ('oats', 'usda:173904', 'овсяные хлопья', 'rolled oats'),
    ('semolina', 'usda:168933', 'манка', 'semolina'), ('millet', 'usda:169702', 'пшено', 'millet'),
    ('pasta_cooked', 'usda:169737', 'макароны варёные', 'cooked pasta'), ('noodles', 'usda:169731', 'яичная лапша', 'egg noodles'),
    ('flour', 'usda:169761', 'мука', 'flour'), ('yeast', 'usda:175042', 'дрожжи', 'yeast'), ('bread', 'usda:174924', 'белый хлеб', 'white bread'),
    ('crumbs', 'usda:174928', 'панировка', 'bread crumbs'), ('croutons', 'usda:172751', 'сухарики', 'croutons'),
    ('puff', 'usda:172790', 'слоёное тесто', 'puff pastry'),
    ('butter', 'usda:173410', 'сливочное масло', 'butter'), ('ghee', 'usda:173412', 'топлёное масло', 'ghee'),
    ('oil', 'usda:171025', 'подсолнечное масло', 'sunflower oil'), ('olive_oil', 'usda:171413', 'оливковое масло', 'olive oil'),
    ('mayo', 'usda:171009', 'майонез', 'mayonnaise'), ('sour_cream', 'usda:171257', 'сметана', 'sour cream'), ('cream', 'usda:170857', 'сливки', 'cream'),
    ('milk', 'usda:171265', 'молоко', 'milk'), ('yogurt', 'usda:171284', 'катык', 'plain yogurt'), ('kefir', 'usda:170904', 'кефир', 'kefir'),
    ('cottage', 'usda:172179', 'творог', 'cottage cheese'), ('feta', 'usda:173420', 'брынза', 'white cheese'),
    ('mozzarella', 'usda:170845', 'сулугуни/моцарелла', 'suluguni or mozzarella'), ('cheddar', 'usda:173414', 'твёрдый сыр', 'hard cheese'),
    ('parmesan', 'usda:171247', 'пармезан', 'parmesan'), ('egg', 'usda:171287', 'яйца', 'eggs'), ('yolk', 'usda:172184', 'желтки', 'egg yolks'),
    ('onion', 'usda:170000', 'лук', 'onion'), ('green_onion', 'usda:170005', 'зелёный лук', 'spring onion'), ('garlic', 'usda:169230', 'чеснок', 'garlic'),
    ('tomato', 'usda:170457', 'помидоры', 'tomatoes'), ('tomato_paste', 'usda:170459', 'томатная паста', 'tomato paste'),
    ('pepper', 'usda:170427', 'сладкий перец', 'bell pepper'), ('chili', 'usda:170106', 'острый перец', 'chili'),
    ('eggplant', 'usda:169228', 'баклажаны', 'eggplant'), ('potato', 'usda:170026', 'картофель', 'potatoes'),
    ('carrot', 'usda:170393', 'морковь', 'carrots'), ('beet', 'usda:169145', 'свёкла', 'beetroot'), ('cabbage', 'usda:169975', 'капуста', 'cabbage'),
    ('sauerkraut', 'usda:169279', 'квашеная капуста', 'sauerkraut'), ('cucumber', 'usda:168409', 'огурцы', 'cucumbers'),
    ('pickles', 'usda:169379', 'солёные огурцы', 'pickles'), ('radish', 'usda:169276', 'редис', 'radish'), ('lettuce', 'usda:169247', 'салат ромэн', 'romaine'),
    ('zucchini', 'usda:169291', 'кабачки', 'zucchini'), ('pumpkin', 'usda:168448', 'тыква', 'pumpkin'), ('mushrooms', 'usda:169251', 'шампиньоны', 'mushrooms'),
    ('peas', 'usda:170104', 'зелёный горошек', 'green peas'), ('corn', 'usda:169214', 'кукуруза', 'sweet corn'), ('olives', 'usda:169096', 'оливки', 'olives'),
    ('spinach', 'usda:168462', 'шпинат', 'spinach'), ('dill', 'usda:172233', 'укроп', 'dill'), ('cilantro', 'usda:169997', 'кинза', 'cilantro'),
    ('parsley', 'usda:170416', 'петрушка', 'parsley'), ('mint', 'usda:173475', 'мята', 'mint'), ('grape_leaves', 'usda:169393', 'виноградные листья', 'grape leaves'),
    ('chickpeas', 'usda:173756', 'нут', 'chickpeas'), ('lentils', 'usda:174284', 'красная чечевица', 'red lentils'),
    ('beans_cooked', 'usda:175194', 'красная фасоль варёная', 'cooked red beans'),
    ('chestnut', 'usda:170190', 'каштаны', 'chestnuts'), ('dried_apricot', 'usda:173941', 'курага', 'dried apricots'),
    ('prunes', 'usda:168162', 'чернослив', 'prunes'), ('raisins', 'usda:168165', 'изюм', 'raisins'), ('plums', 'usda:169949', 'алыча', 'sour plums'),
    ('pomegranate', 'usda:169134', 'гранат', 'pomegranate'), ('pomegranate_juice', 'usda:167787', 'гранатовый сок/наршараб', 'pomegranate juice'),
    ('lemon', 'usda:167747', 'лимонный сок', 'lemon juice'), ('sour_cherry', 'usda:173954', 'вишня', 'sour cherries'), ('apple', 'usda:171688', 'яблоки', 'apples'),
    ('walnut', 'usda:170187', 'грецкий орех', 'walnuts'), ('hazelnut', 'usda:170581', 'фундук', 'hazelnuts'), ('almond', 'usda:170567', 'миндаль', 'almonds'),
    ('sugar', 'usda:169655', 'сахар', 'sugar'), ('powder_sugar', 'usda:169656', 'сахарная пудра', 'powdered sugar'), ('honey', 'usda:169640', 'мёд', 'honey'),
    ('cocoa', 'usda:169593', 'какао', 'cocoa'), ('saffron', 'usda:170934', 'шафран', 'saffron'), ('turmeric', 'usda:172231', 'куркума', 'turmeric'),
    ('cardamom', 'usda:170919', 'кардамон', 'cardamom'), ('fennel', 'usda:171323', 'фенхель', 'fennel seed'),
    ('vinegar', 'usda:172237', 'уксус', 'vinegar'), ('salt', 'usda:173468', 'соль', 'salt'), ('water', 'usda:173647', 'вода', 'water'),
    ('tea', 'usda:173227', 'чай', 'tea'), ('fries', 'usda:170698', 'картофель фри', 'french fries'),
]:
    ing(*row)

# id, Russian, English, search words (Azerbaijani and other spellings), [(ingredient, grams)], cooked weight g,
# [(portion ru, portion en, grams)], short note shown on the card or ''.
D = []
def dish(ident, ru, en, words, parts, cooked, portions, note=''):
    D.append((ident, ru, en, words, parts, cooked, portions, note))

PLATE = lambda g: ('Порция (≈%d г)' % g, 'Portion (~%d g)' % g, g)
BOWL = lambda g: ('Тарелка (≈%d г)' % g, 'Bowl (~%d g)' % g, g)
PIECE = lambda g, ru='1 шт.', en='1 piece': ('%s (≈%d г)' % (ru, g), '%s (~%d g)' % (en, g), g)

# --- Azerbaijani rice and meat dishes ---
dish('shah-plov', 'Шах-плов (Şah plov)', 'Shah plov in lavash crust (Şah plov)', 'şah plov shah plov шах плов плов лаваш',
     [('rice_cooked', 1250), ('ghee', 150), ('lamb', 600), ('onion', 200), ('dried_apricot', 100), ('chestnut', 100), ('raisins', 80), ('flour', 120), ('water', 60), ('saffron', 0.5)],
     2300, [PLATE(350)], 'Корочка — тонкий лаваш на масле.')
dish('plov-qovurma', 'Плов с бараниной и каштанами (Plov, qovurma)', 'Plov with lamb qovurma and chestnuts', 'plov qovurma ət plovu плов баранина каштаны qara',
     [('rice_cooked', 1000), ('ghee', 100), ('lamb', 500), ('onion', 200), ('chestnut', 100), ('dried_apricot', 60), ('raisins', 60), ('butter', 30), ('saffron', 0.5)],
     1850, [PLATE(350)])
dish('plov-toyuq', 'Плов с курицей (Toyuq plovu)', 'Chicken plov (Toyuq plovu)', 'toyuq plovu plov плов курица',
     [('rice_cooked', 1000), ('ghee', 80), ('chicken', 600), ('onion', 150), ('dried_apricot', 60), ('raisins', 60), ('saffron', 0.5)],
     1750, [PLATE(350)])
dish('plov-ag', 'Рис для плова с маслом и шафраном (Süzmə plov)', 'Plov rice with butter and saffron (Süzmə plov)', 'süzmə plov ağ plov düyü плов рис шафран',
     [('rice_cooked', 1000), ('ghee', 80), ('saffron', 0.5)], 1050, [PLATE(250)])
dish('plov-uzbek', 'Плов узбекский', 'Uzbek plov', 'özbək plovu uzbek plov плов узбекский морковь',
     [('rice_cooked', 1100), ('beef', 500), ('carrot', 500), ('onion', 200), ('oil', 150), ('chickpeas', 50), ('garlic', 40)],
     2300, [PLATE(350)])
dish('plov-sebzi', 'Плов с зеленью и рыбой (Sabzi plov)', 'Herb plov with fish', 'sabzi plov səbzi plov балыг плов зелень рыба',
     [('rice_cooked', 1000), ('ghee', 80), ('dill', 60), ('cilantro', 40), ('carp', 400), ('oil', 30)], 1500, [PLATE(350)])
dish('dolma-yarpaq', 'Долма в виноградных листьях (Yarpaq dolması)', 'Grape leaf dolma (Yarpaq dolması)', 'yarpaq dolması dolma долма толма виноградные листья',
     [('grape_leaves', 150), ('lamb_mince', 500), ('rice', 100), ('onion', 150), ('dill', 20), ('cilantro', 20), ('mint', 10), ('butter', 30), ('water', 200)],
     1050, [PIECE(25, '1 долмачка', '1 roll'), PLATE(250)], 'Без катыка: его стоит записать отдельно.')
dish('dolma-kelem', 'Долма в капустных листьях (Kələm dolması)', 'Cabbage dolma (Kələm dolması)', 'kələm dolması kelem dolma долма капуста голубцы',
     [('cabbage', 400), ('beef_mince', 400), ('rice', 80), ('onion', 120), ('tomato_paste', 30), ('sugar', 15), ('chestnut', 50), ('butter', 30), ('water', 150)],
     1150, [PIECE(60), PLATE(300)])
dish('dolma-uc-baci', 'Долма «Три сестры» (Üç bacı dolması)', 'Three sisters dolma: eggplant, tomato, pepper', 'üç bacı dolması uc baci dolma долма три сестры баклажан перец помидор',
     [('eggplant', 300), ('tomato', 300), ('pepper', 300), ('lamb_mince', 500), ('onion', 150), ('rice', 60), ('dill', 20), ('cilantro', 20), ('butter', 40), ('water', 100)],
     1550, [PLATE(350)])
dish('dolma-biber', 'Фаршированный перец (Bibər dolması)', 'Stuffed peppers (Bibər dolması)', 'bibər dolması biber dolma фаршированный перец долма',
     [('pepper', 600), ('beef_mince', 400), ('rice', 80), ('onion', 100), ('tomato_paste', 30), ('water', 150)], 1300, [PIECE(160), PLATE(320)])
dish('lule-kebab', 'Люля-кебаб из баранины (Lülə kabab)', 'Lamb lula kebab (Lülə kabab)', 'lülə kabab lule kebab люля кебаб шашлык фарш',
     [('lamb_mince', 1000), ('onion', 200), ('tallow', 30)], 850, [PIECE(90, '1 шампур', '1 skewer'), PLATE(250)])
dish('lule-kebab-toyuq', 'Люля-кебаб из курицы (Toyuq lülə)', 'Chicken lula kebab', 'toyuq lülə kabab люля кебаб курица',
     [('chicken_dark', 1000), ('onion', 200), ('butter', 30)], 820, [PIECE(90, '1 шампур', '1 skewer')])
dish('tike-kebab', 'Шашлык из баранины (Tikə kabab)', 'Lamb shish kebab (Tikə kabab)', 'tikə kabab tike kebab шашлык баранина antrikot basdırma',
     [('lamb', 1000), ('onion', 60)], 700, [PIECE(120, '1 шампур', '1 skewer'), PLATE(250)])
dish('tike-kebab-lean', 'Шашлык из постной баранины (Basdırma)', 'Lean lamb kebab (Basdırma)', 'basdırma basdirma шашлык бастурма баранина',
     [('lamb_lean', 1000), ('onion', 60)], 700, [PIECE(120, '1 шампур', '1 skewer')])
dish('toyuq-kebab', 'Шашлык из куриного филе (Toyuq kababı)', 'Chicken breast kebab', 'toyuq kababı toyuq kebab шашлык курица филе',
     [('chicken_breast', 1000), ('oil', 20), ('onion', 40)], 740, [PIECE(120, '1 шампур', '1 skewer')])
dish('toyuq-kebab-dark', 'Шашлык из куриных бёдер', 'Chicken thigh kebab', 'toyuq budu kabab шашлык курица бедро',
     [('chicken_dark', 1000), ('oil', 20), ('onion', 40)], 720, [PIECE(120, '1 шампур', '1 skewer')])
dish('shashlyk-pork', 'Шашлык из свинины', 'Pork shish kebab', 'donuz kababı шашлык свинина свиной',
     [('pork', 1000), ('onion', 100), ('vinegar', 20)], 700, [PIECE(130, '1 шампур', '1 skewer')])
dish('ciyer-kebab', 'Шашлык из печени с курдюком (Ciyər kababı)', 'Lamb liver kebab with tail fat', 'ciyər kababı ciyer печень шашлык курдюк',
     [('liver_lamb', 600), ('tallow', 100)], 520, [PIECE(100, '1 шампур', '1 skewer')])
dish('sturgeon-kebab', 'Шашлык из осетрины (Nərə kababı)', 'Sturgeon kebab', 'nərə kababı nere осетрина шашлык рыба',
     [('sturgeon', 1000), ('butter', 30)], 780, [PIECE(150, '1 шампур', '1 skewer')])
dish('tava-kebab', 'Тава-кебаб (Tava kabab)', 'Tava kebab: lamb patties with egg', 'tava kabab тава кебаб',
     [('lamb_mince', 500), ('onion', 100), ('egg', 200), ('butter', 40)], 700, [PLATE(250)])
dish('qovurma', 'Говурма из баранины (Ət qovurma)', 'Fried lamb with onion (Qovurma)', 'qovurma ət qovurması говурма жаркое баранина',
     [('lamb', 1000), ('onion', 300), ('butter', 50)], 900, [PLATE(250)])
dish('tursu-qovurma', 'Туршу-говурма (Turşu qovurma)', 'Sour qovurma with dried fruit and chestnuts', 'turşu qovurma tursu qovurma туршу говурма',
     [('lamb', 600), ('onion', 150), ('dried_apricot', 80), ('chestnut', 80), ('plums', 60), ('butter', 50)], 900, [PLATE(250)])
dish('sebzi-qovurma', 'Сабзи-говурма (Səbzi qovurma)', 'Lamb with herbs (Səbzi qovurma)', 'səbzi qovurma sebzi сабзи говурма зелень',
     [('lamb', 600), ('spinach', 300), ('dill', 100), ('cilantro', 100), ('green_onion', 100), ('onion', 150), ('plums', 50), ('butter', 60)], 1050, [PLATE(250)])
dish('bozartma', 'Бозартма из баранины (Bozartma)', 'Lamb stewed with tomato (Bozartma)', 'bozartma бозартма баранина томат',
     [('lamb', 800), ('onion', 300), ('tomato', 400), ('butter', 50)], 1250, [PLATE(300)])
dish('toyuq-bozartma', 'Бозартма из курицы', 'Chicken bozartma', 'toyuq bozartma бозартма курица',
     [('chicken', 1000), ('onion', 300), ('tomato', 400), ('butter', 40)], 1350, [PLATE(300)])
dish('cigirtma-toyuq', 'Чыгыртма из курицы (Toyuq çığırtma)', 'Chicken chygyrtma with eggs and tomato', 'çığırtma toyuq cigirtma чыгыртма курица яйца',
     [('chicken', 800), ('onion', 200), ('tomato', 300), ('egg', 200), ('butter', 60)], 1300, [PLATE(300)])
dish('pomidor-yumurta', 'Яичница с помидорами (Pomidor-yumurta)', 'Eggs with tomatoes (Pomidor-yumurta)', 'pomidor yumurta pomidorlu yumurta яичница помидоры',
     [('tomato', 400), ('egg', 300), ('butter', 30), ('onion', 50)], 700, [PLATE(250)])
dish('qayganaq', 'Гайганаг — яйца на сливочном масле (Qayğanaq)', 'Eggs scrambled in butter (Qayğanaq)', 'qayğanaq qayganaq гайганаг яичница',
     [('egg', 300), ('butter', 30), ('milk', 50)], 350, [PLATE(180)])
dish('kuku-goyerti', 'Кюкю из зелени (Göyərti kükü)', 'Herb frittata (Kükü)', 'kükü göyərti kükü kuku кюкю зелень яйца',
     [('spinach', 200), ('dill', 100), ('cilantro', 100), ('green_onion', 60), ('egg', 300), ('butter', 40), ('walnut', 30), ('flour', 20)], 650, [PIECE(100, '1 кусок', '1 slice')])
dish('ajabsandal', 'Аджапсандал (Ajabsandal)', 'Vegetable stew with eggplant (Ajabsandal)', 'ajabsandal ajapsandal аджапсандал аджап сандал овощи',
     [('eggplant', 500), ('potato', 300), ('tomato', 300), ('pepper', 200), ('onion', 100), ('oil', 80)], 1250, [PLATE(300)])
dish('sac-ici', 'Садж-ичи с бараниной (Sac içi)', 'Saj with lamb and vegetables (Sac içi)', 'sac içi saj sac садж мясо овощи',
     [('lamb', 500), ('potato', 400), ('eggplant', 300), ('tomato', 200), ('pepper', 200), ('onion', 100), ('oil', 100)], 1400, [PLATE(350)])
dish('sac-toyuq', 'Садж с курицей (Toyuq sacı)', 'Saj with chicken and vegetables', 'toyuq sacı sac saj садж курица',
     [('chicken_dark', 600), ('potato', 400), ('eggplant', 300), ('tomato', 200), ('pepper', 200), ('onion', 100), ('oil', 100)], 1450, [PLATE(350)])
dish('levengi-toyuq', 'Лявянги из курицы (Toyuq ləvəngisi)', 'Chicken stuffed with walnut (Ləvəngi)', 'toyuq ləvəngi levengi лявянги курица орехи',
     [('chicken', 1200), ('walnut', 200), ('onion', 200), ('plums', 60), ('butter', 30)], 1350, [PLATE(300)])
dish('levengi-baliq', 'Лявянги из рыбы (Balıq ləvəngisi)', 'Fish stuffed with walnut (Balıq ləvəngi)', 'balıq ləvəngi baliq levengi лявянги рыба кутум',
     [('carp', 1000), ('walnut', 150), ('onion', 150), ('plums', 50), ('oil', 20)], 1100, [PLATE(300)])
dish('kutum-fried', 'Кутум жареный (Qızardılmış kütüm)', 'Fried kutum fish', 'kütüm qızardılmış kutum кутум рыба жареная',
     [('carp', 1000), ('flour', 40), ('oil', 70)], 830, [PLATE(250)])
dish('badimcan-ikra', 'Баклажанная икра (Badımcan ikrası)', 'Eggplant caviar', 'badımcan ikrası badimcan икра баклажанная',
     [('eggplant', 600), ('pepper', 150), ('tomato', 150), ('onion', 80), ('oil', 50), ('garlic', 10)], 750, [PLATE(100)])
dish('badimcan-qizardilmis', 'Баклажаны жареные', 'Fried eggplant slices', 'badımcan qızardılmış баклажаны жареные',
     [('eggplant', 600), ('oil', 90), ('garlic', 10)], 500, [PLATE(150)])
dish('qutab-et', 'Кутабы с мясом (Ət qutabı)', 'Qutab with meat', 'ət qutabı qutab kutab кутаб кутабы мясо',
     [('flour', 300), ('water', 150), ('lamb_mince', 300), ('onion', 100), ('pomegranate', 30), ('butter', 50)], 780, [PIECE(65, '1 кутаб', '1 qutab')])
dish('qutab-goyerti', 'Кутабы с зеленью (Göyərti qutabı)', 'Qutab with greens', 'göyərti qutabı qutab kutab кутаб зелень',
     [('flour', 300), ('water', 150), ('spinach', 300), ('dill', 50), ('cilantro', 50), ('green_onion', 50), ('butter', 60)], 780, [PIECE(65, '1 кутаб', '1 qutab')])
dish('qutab-balqabaq', 'Кутабы с тыквой (Balqabaq qutabı)', 'Qutab with pumpkin', 'balqabaq qutabı qutab kutab кутаб тыква',
     [('flour', 300), ('water', 150), ('pumpkin', 400), ('onion', 50), ('pomegranate', 50), ('sugar', 10), ('butter', 60)], 820, [PIECE(70, '1 кутаб', '1 qutab')])
dish('qutab-pendir', 'Кутабы с сыром', 'Qutab with cheese', 'pendirli qutab qutab кутаб сыр',
     [('flour', 300), ('water', 150), ('feta', 200), ('mozzarella', 100), ('butter', 50)], 760, [PIECE(65, '1 кутаб', '1 qutab')])
dish('qurze', 'Гурзэ — пельмени по-бакински (Qurzə)', 'Qurze dumplings', 'qurzə qurze гурзе гурза пельмени',
     [('flour', 300), ('water', 140), ('egg', 50), ('lamb_mince', 400), ('onion', 120), ('butter', 30)], 1000, [PIECE(25, '1 гурзэ', '1 dumpling'), PLATE(250)])
dish('xengel-qurut', 'Хангял с говурмой и катыком (Xəngəl)', 'Khangal: noodles with qovurma and yogurt', 'xəngəl xengel хангял хингал катык',
     [('flour', 400), ('water', 180), ('egg', 50), ('lamb', 300), ('onion', 200), ('butter', 60), ('yogurt', 150), ('garlic', 10)], 1300, [PLATE(350)])
dish('khash', 'Хаш (Xaş)', 'Khash: tripe and shank soup', 'xaş xas хаш суп',
     [('tripe', 600), ('shank', 300), ('water', 2000), ('garlic', 30)], 2200, [BOWL(450)], 'Наваристость сильно зависит от костей и ножек.')

# --- Azerbaijani soups ---
dish('dusbere', 'Дюшбара — суп с пельменями (Düşbərə)', 'Dushbara dumpling soup (Düşbərə)', 'düşbərə dusbere дюшбара душбара суп пельмени',
     [('flour', 200), ('water', 1590), ('egg', 50), ('lamb_mince', 300), ('onion', 80), ('tallow', 10)], 2100, [BOWL(350)])
dish('piti', 'Пити (Piti)', 'Piti: lamb and chickpea pot soup', 'piti пити шекинский суп баранина нут',
     [('lamb', 500), ('chickpeas', 100), ('potato', 300), ('chestnut', 80), ('tallow', 80), ('onion', 100), ('saffron', 0.3), ('water', 1200)], 1900, [BOWL(450)])
dish('kufte-bozbas', 'Кюфта-бозбаш (Küftə bozbaş)', 'Kufta bozbash: meatball soup', 'küftə bozbaş kufte bozbash кюфта бозбаш',
     [('lamb_mince', 600), ('rice', 60), ('onion', 150), ('chickpeas', 100), ('potato', 400), ('plums', 50), ('butter', 30), ('water', 1800)], 2700, [BOWL(450)])
dish('parca-bozbas', 'Бозбаш с кусками баранины (Parça bozbaş)', 'Bozbash with lamb pieces', 'parça bozbaş bozbash бозбаш баранина нут',
     [('lamb', 600), ('chickpeas', 100), ('potato', 400), ('onion', 100), ('tomato', 150), ('water', 1800)], 2600, [BOWL(450)])
dish('dovga', 'Довга (Dovğa)', 'Dovga: warm yogurt soup with herbs', 'dovğa dovga довга',
     [('yogurt', 1000), ('water', 500), ('rice', 50), ('egg', 50), ('flour', 20), ('spinach', 150), ('dill', 50), ('cilantro', 50), ('mint', 20)], 1800, [BOWL(300)])
dish('mercimek', 'Суп из красной чечевицы (Mərci şorbası)', 'Red lentil soup', 'mərci şorbası mercimek merci суп чечевица',
     [('lentils', 250), ('onion', 100), ('carrot', 100), ('butter', 40), ('flour', 15), ('tomato_paste', 20), ('water', 1800)], 2000, [BOWL(300)])
dish('eriste', 'Суп с домашней лапшой (Əriştə şorbası)', 'Homemade noodle soup (Əriştə)', 'əriştə şorbası eriste суп лапша',
     [('noodles', 150), ('lamb', 200), ('chickpeas', 60), ('onion', 80), ('butter', 20), ('water', 1500)], 1800, [BOWL(350)])
dish('toyuq-sorba', 'Куриный суп с лапшой (Toyuq şorbası)', 'Chicken noodle soup', 'toyuq şorbası куриный суп лапша бульон',
     [('chicken', 500), ('noodles', 100), ('potato', 200), ('carrot', 100), ('onion', 80), ('water', 2000)], 2700, [BOWL(350)])

# --- Russian and other post-Soviet dishes ---
dish('borsch', 'Борщ с говядиной', 'Borscht with beef', 'борщ borsch borscht', [('beef', 400), ('beet', 300), ('cabbage', 300), ('potato', 300), ('carrot', 100), ('onion', 100), ('tomato_paste', 40), ('oil', 30), ('water', 2000)],
     3300, [BOWL(350)], 'Без сметаны: её стоит записать отдельно.')
dish('shchi', 'Щи из свежей капусты', 'Cabbage soup (shchi)', 'щи shchi kələm şorbası', [('beef', 300), ('cabbage', 400), ('potato', 300), ('carrot', 100), ('onion', 80), ('oil', 20), ('water', 2000)], 3000, [BOWL(350)])
dish('solyanka', 'Солянка сборная мясная', 'Mixed meat solyanka', 'солянка solyanka',
     [('beef', 200), ('ham', 150), ('smoked_sausage', 150), ('frank', 100), ('pickles', 150), ('olives', 50), ('onion', 100), ('tomato_paste', 50), ('oil', 30), ('lemon', 20), ('water', 1500)], 2300, [BOWL(350)])
dish('okroshka', 'Окрошка на кефире', 'Okroshka on kefir', 'окрошка okroshka',
     [('potato', 300), ('egg', 200), ('cucumber', 250), ('radish', 150), ('bologna', 200), ('dill', 20), ('green_onion', 30), ('kefir', 1000)], 2100, [BOWL(350)])
dish('ukha', 'Уха', 'Fish soup (ukha)', 'уха balıq şorbası рыбный суп', [('carp', 500), ('potato', 300), ('carrot', 80), ('onion', 80), ('dill', 10), ('water', 1800)], 2600, [BOWL(350)])
dish('gorohovy', 'Гороховый суп с копчёностями', 'Pea soup with smoked meat', 'гороховый суп noxud şorbası',
     [('lentils', 250), ('potato', 300), ('carrot', 80), ('onion', 80), ('smoked_sausage', 200), ('oil', 20), ('water', 2000)], 2600, [BOWL(350)],
     'Колотый горох заменён по составу близкой чечевицей USDA.')
dish('pelmeni', 'Пельмени (говядина и свинина)', 'Pelmeni dumplings, beef and pork', 'пельмени pelmeni pelmen',
     [('flour', 300), ('water', 140), ('egg', 50), ('beef_mince', 200), ('pork_mince', 200), ('onion', 100)], 1000, [PIECE(12, '1 пельмень', '1 dumpling'), PLATE(250)])
dish('pelmeni-beef', 'Пельмени говяжьи', 'Beef pelmeni', 'пельмени говяжьи pelmeni mal əti',
     [('flour', 300), ('water', 140), ('egg', 50), ('beef_mince', 400), ('onion', 100)], 1000, [PIECE(12, '1 пельмень', '1 dumpling'), PLATE(250)])
dish('pelmeni-chicken', 'Пельмени куриные', 'Chicken pelmeni', 'пельмени куриные pelmeni toyuq',
     [('flour', 300), ('water', 140), ('egg', 50), ('chicken_dark', 400), ('onion', 100)], 1000, [PIECE(12, '1 пельмень', '1 dumpling'), PLATE(250)])
dish('vareniki-potato', 'Вареники с картошкой', 'Vareniki with potato', 'вареники картошка vareniki',
     [('flour', 300), ('water', 150), ('potato', 500), ('onion', 100), ('oil', 40)], 1000, [PIECE(25, '1 вареник', '1 dumpling'), PLATE(250)])
dish('vareniki-tvorog', 'Вареники с творогом', 'Vareniki with cottage cheese', 'вареники творог vareniki kəsmik',
     [('flour', 300), ('water', 150), ('egg', 100), ('cottage', 500), ('sugar', 40)], 1080, [PIECE(25, '1 вареник', '1 dumpling'), PLATE(250)])
dish('vareniki-cherry', 'Вареники с вишней', 'Vareniki with cherries', 'вареники вишня vareniki albalı',
     [('flour', 300), ('water', 150), ('egg', 50), ('sour_cherry', 450), ('sugar', 80)], 1000, [PIECE(25, '1 вареник', '1 dumpling'), PLATE(250)])
dish('manty', 'Манты с бараниной', 'Manti with lamb', 'манты manti mantı',
     [('flour', 400), ('water', 180), ('lamb_mince', 500), ('onion', 300), ('tallow', 50)], 1350, [PIECE(50, '1 манта', '1 manti'), PLATE(300)])
dish('chebureki', 'Чебуреки с мясом', 'Chebureki: fried meat pasties', 'чебурек чебуреки çebürek cheburek',
     [('flour', 400), ('water', 200), ('beef_mince', 300), ('onion', 100), ('oil', 110)], 1080, [PIECE(120, '1 чебурек', '1 pasty')], 'В масле учтено ≈80 г, впитавшихся при жарке.')
dish('belyashi', 'Беляши', 'Belyashi: fried meat buns', 'беляши беляш belyash',
     [('flour', 400), ('milk', 200), ('yeast', 10), ('sugar', 10), ('beef_mince', 350), ('onion', 100), ('oil', 100)], 1100, [PIECE(100, '1 беляш', '1 bun')])
dish('pirozhki-potato', 'Пирожки жареные с картошкой', 'Fried pirozhki with potato', 'пирожки пирожок картошка piroq kartoflu',
     [('flour', 400), ('milk', 200), ('yeast', 10), ('sugar', 15), ('egg', 50), ('potato', 500), ('onion', 80), ('oil', 110)], 1250, [PIECE(80, '1 пирожок', '1 pirozhok')])
dish('pirozhki-cabbage', 'Пирожки печёные с капустой', 'Baked pirozhki with cabbage', 'пирожки пирожок капуста piroq kələmli',
     [('flour', 400), ('milk', 200), ('yeast', 10), ('sugar', 15), ('egg', 100), ('cabbage', 600), ('onion', 80), ('oil', 50)], 1250, [PIECE(80, '1 пирожок', '1 pirozhok')])
dish('pirozhki-meat', 'Пирожки печёные с мясом', 'Baked pirozhki with meat', 'пирожки пирожок мясо piroq ətli',
     [('flour', 400), ('milk', 200), ('yeast', 10), ('sugar', 15), ('egg', 100), ('beef_mince', 400), ('onion', 100), ('oil', 40)], 1150, [PIECE(80, '1 пирожок', '1 pirozhok')])
dish('blini', 'Блины на молоке', 'Thin pancakes (blini)', 'блины блин blini pancake',
     [('flour', 250), ('milk', 500), ('egg', 100), ('sugar', 25), ('oil', 30), ('butter', 20)], 850, [PIECE(45, '1 блин', '1 pancake')])
dish('blini-meat', 'Блинчики с мясом', 'Blini filled with meat', 'блинчики с мясом блины мясо',
     [('flour', 250), ('milk', 500), ('egg', 100), ('oil', 50), ('beef_mince', 500), ('onion', 100)], 1300, [PIECE(90, '1 блинчик', '1 pancake')])
dish('blini-tvorog', 'Блинчики с творогом', 'Blini filled with cottage cheese', 'блинчики с творогом блины творог',
     [('flour', 250), ('milk', 500), ('egg', 150), ('oil', 40), ('cottage', 500), ('sugar', 60)], 1350, [PIECE(90, '1 блинчик', '1 pancake')])
dish('oladi', 'Оладьи на кефире', 'Kefir fritters (oladyi)', 'оладьи оладушки oladi',
     [('flour', 250), ('kefir', 500), ('egg', 50), ('sugar', 30), ('oil', 60)], 800, [PIECE(40, '1 оладья', '1 fritter')])
dish('syrniki', 'Сырники', 'Syrniki: cottage cheese pancakes', 'сырники syrniki kəsmik',
     [('cottage', 500), ('egg', 50), ('flour', 60), ('sugar', 40), ('oil', 30)], 620, [PIECE(50, '1 сырник', '1 pancake')])
dish('zapekanka', 'Творожная запеканка', 'Baked cottage cheese pudding', 'запеканка творожная kəsmik',
     [('cottage', 500), ('egg', 100), ('semolina', 50), ('sugar', 60), ('sour_cream', 50), ('butter', 10)], 700, [PIECE(150, '1 кусок', '1 slice')])
dish('kotlety', 'Котлеты домашние (говядина и свинина)', 'Homemade meat patties (kotlety)', 'котлеты котлета kotlet kotlet',
     [('beef_mince', 300), ('pork_mince', 200), ('bread', 80), ('milk', 80), ('onion', 80), ('egg', 50), ('crumbs', 30), ('oil', 30)], 700, [PIECE(80, '1 котлета', '1 patty')])
dish('kotlety-beef', 'Котлеты говяжьи', 'Beef patties', 'котлеты говяжьи kotlet mal əti',
     [('beef_mince', 500), ('bread', 80), ('milk', 80), ('onion', 80), ('egg', 50), ('crumbs', 30), ('oil', 30)], 700, [PIECE(80, '1 котлета', '1 patty')])
dish('kotlety-chicken', 'Котлеты куриные', 'Chicken patties', 'котлеты куриные toyuq kotleti',
     [('chicken_breast', 500), ('bread', 60), ('milk', 60), ('onion', 60), ('egg', 50), ('oil', 30)], 640, [PIECE(75, '1 котлета', '1 patty')])
dish('tefteli', 'Тефтели в томатном соусе', 'Meatballs in tomato sauce', 'тефтели tefteli küftə',
     [('beef_mince', 400), ('rice', 60), ('onion', 100), ('egg', 50), ('tomato_paste', 50), ('sour_cream', 50), ('flour', 20), ('water', 300)], 1000, [PLATE(250)])
dish('golubtsy', 'Голубцы', 'Cabbage rolls (golubtsy)', 'голубцы golubtsy',
     [('cabbage', 600), ('beef_mince', 300), ('rice', 80), ('onion', 100), ('carrot', 80), ('tomato_paste', 40), ('sour_cream', 50), ('water', 300)], 1400, [PIECE(130, '1 голубец', '1 roll')])
dish('zharkoe', 'Жаркое: картофель с говядиной', 'Potato and beef stew (zharkoye)', 'жаркое картошка с мясом',
     [('beef', 500), ('potato', 800), ('onion', 150), ('carrot', 100), ('oil', 40), ('water', 200)], 1600, [PLATE(350)])
dish('gulyash', 'Гуляш из говядины', 'Beef goulash', 'гуляш gulyas goulash',
     [('beef', 600), ('onion', 200), ('tomato_paste', 50), ('flour', 20), ('oil', 30), ('water', 400)], 1000, [PLATE(200)])
dish('befstroganov', 'Бефстроганов', 'Beef stroganoff', 'бефстроганов stroganoff',
     [('beef', 600), ('onion', 150), ('sour_cream', 200), ('flour', 15), ('butter', 30)], 850, [PLATE(200)])
dish('kartoshka-fried', 'Картошка жареная с луком', 'Pan-fried potatoes with onion', 'жареная картошка qızardılmış kartof',
     [('potato', 1000), ('oil', 80), ('onion', 100)], 700, [PLATE(250)])
dish('pyure', 'Картофельное пюре на молоке с маслом', 'Mashed potatoes with milk and butter', 'пюре картофельное kartof püresi',
     [('potato', 1000), ('milk', 150), ('butter', 50)], 1100, [PLATE(200)])
dish('kartoshka-boiled-dill', 'Картофель отварной с маслом и укропом', 'Boiled potatoes with butter and dill', 'картошка отварная qaynadılmış kartof',
     [('potato', 1000), ('butter', 30), ('dill', 10)], 1000, [PLATE(200)])
dish('grechka-butter', 'Гречка с маслом', 'Buckwheat with butter', 'гречка с маслом qarabaşaq',
     [('buckwheat_cooked', 500), ('butter', 15)], 515, [PLATE(200)])
dish('grechka-kupecheski', 'Гречка по-купечески', 'Buckwheat with meat and vegetables', 'гречка по-купечески',
     [('buckwheat_cooked', 600), ('pork_mince', 300), ('onion', 100), ('carrot', 100), ('oil', 30), ('tomato_paste', 20)], 1050, [PLATE(250)])
dish('makarony-flotski', 'Макароны по-флотски', 'Navy-style pasta with ground beef', 'макароны по-флотски makaron',
     [('pasta_cooked', 600), ('beef_mince', 300), ('onion', 100), ('oil', 20)], 950, [PLATE(300)])
dish('makarony-cheese', 'Макароны с сыром и маслом', 'Pasta with cheese and butter', 'макароны с сыром makaron pendirli',
     [('pasta_cooked', 500), ('cheddar', 60), ('butter', 20)], 580, [PLATE(250)])
dish('ovsyanka-milk', 'Овсяная каша на молоке', 'Oatmeal porridge with milk', 'овсянка овсяная каша на молоке yulaf sıyığı',
     [('oats', 50), ('milk', 250)], 280, [PLATE(280)])
dish('ovsyanka-milk-sugar', 'Овсяная каша на молоке с сахаром и маслом', 'Oatmeal with milk, sugar and butter', 'овсянка с сахаром маслом yulaf sıyığı',
     [('oats', 50), ('milk', 250), ('sugar', 10), ('butter', 5)], 295, [PLATE(295)])
dish('manka', 'Манная каша на молоке', 'Semolina porridge with milk', 'манная каша манка irmik sıyığı',
     [('semolina', 50), ('milk', 400), ('sugar', 15), ('butter', 10)], 450, [PLATE(250)])
dish('rice-porridge', 'Рисовая каша на молоке', 'Rice porridge with milk', 'рисовая каша на молоке südlü düyü sıyığı şirin plov',
     [('rice', 80), ('milk', 500), ('water', 100), ('sugar', 20), ('butter', 10)], 650, [PLATE(250)])
dish('millet-porridge', 'Пшённая каша на молоке', 'Millet porridge with milk', 'пшённая каша пшенка darı sıyığı',
     [('millet', 100), ('milk', 400), ('water', 200), ('butter', 10), ('sugar', 10)], 650, [PLATE(250)])
dish('omlet', 'Омлет на молоке', 'Omelette with milk', 'омлет omlet',
     [('egg', 200), ('milk', 60), ('butter', 10)], 250, [PLATE(150)])
dish('yaichnica', 'Яичница из 2 яиц на масле', 'Two fried eggs', 'яичница глазунья qayğanaq yumurta',
     [('egg', 100), ('butter', 7)], 100, [('2 яйца (≈100 г)', '2 eggs (~100 g)', 100)])
dish('lagman', 'Лагман', 'Lagman noodle stew', 'лагман lagman',
     [('beef', 400), ('pasta_cooked', 750), ('pepper', 200), ('tomato', 200), ('onion', 150), ('carrot', 100), ('garlic', 20), ('oil', 60), ('water', 800)], 2500, [BOWL(450)])
dish('kurica-zapechennaya', 'Курица запечённая с картофелем', 'Roast chicken with potatoes', 'курица с картошкой в духовке',
     [('chicken', 1000), ('potato', 800), ('onion', 100), ('oil', 30), ('mayo', 30)], 1500, [PLATE(350)])

# --- Salads ---
dish('coban', 'Салат «Чобан» (Çoban salatı)', 'Shepherd salad (Çoban salatı)', 'çoban salatı coban чобан салат помидоры огурцы',
     [('tomato', 300), ('cucumber', 300), ('pepper', 100), ('onion', 50), ('parsley', 30), ('oil', 30), ('lemon', 15)], 815, [PLATE(200)])
dish('tomato-cucumber-sour-cream', 'Салат из помидоров и огурцов со сметаной', 'Tomato and cucumber salad with sour cream', 'салат помидоры огурцы сметана',
     [('tomato', 300), ('cucumber', 300), ('onion', 30), ('dill', 15), ('sour_cream', 100)], 745, [PLATE(200)])
dish('paytaxt', 'Салат «Пайтахт» / Оливье с курицей (Paytaxt salatı)', 'Capital salad / Olivier with chicken (Paytaxt)', 'paytaxt salatı paytaxt пайтахт столичный оливье курица',
     [('potato', 400), ('chicken_breast_cooked', 300), ('egg', 200), ('pickles', 150), ('peas', 150), ('carrot', 100), ('mayo', 200)], 1450, [PLATE(200)])
dish('olivier', 'Салат «Оливье» с колбасой', 'Olivier salad with bologna', 'оливье olivye salatı olivier',
     [('potato', 400), ('bologna', 300), ('egg', 200), ('pickles', 150), ('peas', 150), ('carrot', 100), ('mayo', 200)], 1450, [PLATE(200)])
dish('vinegret', 'Винегрет', 'Vinegret beet salad', 'винегрет vineqret',
     [('beet', 300), ('potato', 300), ('carrot', 150), ('pickles', 150), ('sauerkraut', 150), ('peas', 100), ('oil', 50), ('onion', 50)], 1200, [PLATE(200)])
dish('shuba', 'Сельдь под шубой', 'Herring under a fur coat', 'шуба сельдь под шубой şuba',
     [('herring', 300), ('potato', 300), ('carrot', 200), ('beet', 300), ('egg', 150), ('mayo', 200), ('onion', 50)], 1450, [PLATE(200)])
dish('mimoza', 'Салат «Мимоза»', 'Mimosa salad', 'мимоза mimoza',
     [('tuna', 300), ('egg', 250), ('potato', 300), ('carrot', 150), ('mayo', 150), ('onion', 50), ('cheddar', 100)], 1250, [PLATE(200)])
dish('krabovy', 'Крабовый салат', 'Crab stick salad', 'крабовый салат krab salatı',
     [('surimi', 250), ('corn', 300), ('egg', 200), ('rice_cooked', 200), ('cucumber', 150), ('mayo', 150)], 1250, [PLATE(200)])
dish('caesar', 'Салат «Цезарь» с курицей', 'Caesar salad with chicken', 'цезарь sezar caesar',
     [('lettuce', 200), ('chicken_breast_cooked', 200), ('croutons', 60), ('parmesan', 30), ('mayo', 60), ('lemon', 10), ('tomato', 80)], 640, [PLATE(250)])
dish('greek', 'Греческий салат', 'Greek salad', 'греческий салат yunan salatı',
     [('tomato', 250), ('cucumber', 250), ('pepper', 120), ('onion', 60), ('olives', 60), ('feta', 150), ('olive_oil', 40)], 930, [PLATE(250)])
dish('mangal-salad', 'Мангал-салат (Mangal salatı)', 'Grilled vegetable salad (Mangal salatı)', 'mangal salatı mangal манга салат печёные овощи',
     [('eggplant', 300), ('tomato', 250), ('pepper', 200), ('onion', 60), ('oil', 30), ('garlic', 10), ('cilantro', 20)], 650, [PLATE(200)])
dish('gavurdag', 'Салат «Гавурдаг» (Gavurdağı salatı)', 'Gavurdag salad with walnuts and pomegranate', 'gavurdağı gavurdag гавурдаг салат',
     [('tomato', 300), ('onion', 60), ('pepper', 80), ('walnut', 60), ('pomegranate_juice', 30), ('olive_oil', 30), ('parsley', 30)], 590, [PLATE(200)])
dish('cabbage-salad', 'Салат из капусты с морковью', 'Cabbage and carrot slaw', 'салат из капусты kələm salatı витаминный',
     [('cabbage', 400), ('carrot', 100), ('oil', 30), ('vinegar', 10), ('sugar', 5)], 545, [PLATE(150)])
dish('beet-garlic', 'Свёкла с чесноком и майонезом', 'Beet salad with garlic and mayonnaise', 'свекла с чесноком çuğundur salatı',
     [('beet', 500), ('garlic', 10), ('mayo', 60), ('walnut', 30)], 560, [PLATE(150)])
dish('lobio', 'Лобио', 'Lobio: red beans with walnuts', 'лобио lobio',
     [('beans_cooked', 600), ('onion', 150), ('walnut', 60), ('cilantro', 40), ('oil', 30), ('garlic', 10)], 860, [PLATE(200)])

# --- Street food and Georgian, Turkish dishes ---
dish('lavash', 'Лаваш тонкий', 'Thin lavash bread', 'lavaş lavash лаваш', [('flour', 600), ('water', 330), ('salt', 10)], 800, [PIECE(60, '1 лист', '1 sheet')])
dish('tendir', 'Тандырный хлеб (Təndir çörəyi)', 'Tandir bread (Təndir çörəyi)', 'təndir çörəyi tendir тандыр хлеб чурек',
     [('flour', 1000), ('water', 650), ('yeast', 10), ('salt', 18)], 1450, [PIECE(350, '1 хлеб', '1 loaf'), ('Ломоть (≈50 г)', 'Slice (~50 g)', 50)])
dish('corek', 'Хлеб домашний (Çörək)', 'Homemade bread (Çörək)', 'çörək corek хлеб домашний', [('flour', 1000), ('water', 600), ('yeast', 15), ('salt', 18), ('oil', 30)], 1450,
     [('Ломоть (≈40 г)', 'Slice (~40 g)', 40)])
dish('shaurma-chicken', 'Шаурма с курицей в лаваше', 'Chicken shawarma in lavash', 'şaurma shaurma шаурма шаверма курица дюрум',
     [('local:dish-lavash', 120), ('chicken_cooked', 150), ('cabbage', 50), ('tomato', 40), ('cucumber', 40), ('mayo', 25), ('yogurt', 25)], 450, [PIECE(450, '1 шаурма', '1 wrap')])
dish('shaurma-chicken-fries', 'Шаурма с курицей и картофелем фри', 'Chicken shawarma with fries', 'şaurma kartof shaurma шаурма фри курица',
     [('local:dish-lavash', 120), ('chicken_cooked', 130), ('fries', 60), ('cabbage', 40), ('tomato', 40), ('cucumber', 40), ('mayo', 30), ('yogurt', 20)], 480, [PIECE(480, '1 шаурма', '1 wrap')])
dish('doner-beef', 'Донер с говядиной в лаваше', 'Beef doner in lavash', 'dönər doner донер дёнер говядина',
     [('local:dish-lavash', 120), ('beef_mince', 160), ('cabbage', 50), ('tomato', 40), ('onion', 20), ('mayo', 25), ('yogurt', 25)], 420, [PIECE(420, '1 донер', '1 wrap')], 'Мясо донера взято как жареный говяжий фарш 80/20.')
dish('doner-bread', 'Донер с курицей в тандырном хлебе', 'Chicken doner in tandir bread', 'dönər çörəkdə doner çörək донер хлеб',
     [('local:dish-tendir', 150), ('chicken_cooked', 150), ('cabbage', 40), ('tomato', 40), ('cucumber', 30), ('mayo', 25)], 435, [PIECE(435, '1 донер', '1 doner')])
dish('lahmacun', 'Лахмаджун (Lahmacun)', 'Lahmacun flatbread with minced meat', 'lahmacun lahmajun лахмаджун',
     [('flour', 200), ('water', 120), ('beef_mince', 150), ('tomato', 100), ('pepper', 50), ('onion', 50), ('parsley', 20), ('oil', 15)], 580, [PIECE(145, '1 лахмаджун', '1 piece')])
dish('pide-cheese', 'Пиде с сыром', 'Cheese pide', 'pide pendirli pide пиде сыр',
     [('flour', 250), ('water', 150), ('mozzarella', 150), ('egg', 50), ('butter', 20)], 560, [PIECE(280, '1 пиде', '1 pide')])
dish('pide-meat', 'Пиде с мясом', 'Meat pide', 'pide ətli pide пиде мясо',
     [('flour', 250), ('water', 150), ('beef_mince', 200), ('onion', 50), ('tomato', 60), ('butter', 20)], 620, [PIECE(310, '1 пиде', '1 pide')])
dish('khachapuri-adjarian', 'Хачапури по-аджарски', 'Adjarian khachapuri', 'хачапури аджарский xaçapuri acarski',
     [('flour', 250), ('milk', 150), ('yeast', 5), ('oil', 15), ('mozzarella', 200), ('feta', 100), ('egg', 50), ('butter', 30)], 760, [PIECE(380, '1 лодочка', '1 boat')])
dish('khachapuri-imeruli', 'Хачапури по-имеретински', 'Imeretian khachapuri', 'хачапури имерули xaçapuri',
     [('flour', 400), ('yogurt', 250), ('egg', 50), ('butter', 30), ('mozzarella', 300), ('feta', 200)], 1180, [PIECE(150, '1 кусок (1/8)', '1 slice (1/8)')])
dish('chakhokhbili', 'Чахохбили', 'Chakhokhbili chicken stew', 'чахохбили chaxoxbili',
     [('chicken', 1000), ('tomato', 600), ('onion', 300), ('butter', 40), ('cilantro', 30), ('garlic', 15)], 1600, [PLATE(300)])
dish('chkmeruli', 'Чкмерули', 'Chkmeruli: chicken in garlic cream', 'чкмерули shkmeruli',
     [('chicken', 1000), ('cream', 300), ('garlic', 40), ('butter', 40)], 1150, [PLATE(300)])
dish('burger-home', 'Бургер с говяжьей котлетой', 'Beef burger', 'burger бургер гамбургер',
     [('bread', 80), ('beef_mince', 150), ('cheddar', 20), ('lettuce', 15), ('tomato', 30), ('onion', 10), ('mayo', 15)], 290, [PIECE(290, '1 бургер', '1 burger')])
dish('hot-dog', 'Хот-дог', 'Hot dog', 'hot dog хот-дог сосиска в булке',
     [('bread', 60), ('frank', 50), ('mayo', 10), ('pickles', 15)], 135, [PIECE(135, '1 хот-дог', '1 hot dog')])

# --- Pastry and sweets ---
dish('paxlava', 'Пахлава бакинская (Bakı paxlavası)', 'Baku pakhlava', 'paxlava pakhlava baklava пахлава пахлава бакинская',
     [('flour', 500), ('milk', 200), ('egg', 50), ('butter', 350), ('hazelnut', 500), ('sugar', 700), ('honey', 300), ('water', 200), ('cardamom', 5), ('saffron', 0.5)],
     2600, [PIECE(45, '1 ромбик', '1 piece')])
dish('sekerbura', 'Шекербура (Şəkərbura)', 'Shekerbura almond pastry', 'şəkərbura sekerbura шекербура',
     [('flour', 500), ('butter', 200), ('sour_cream', 100), ('yolk', 40), ('milk', 100), ('almond', 400), ('powder_sugar', 400), ('cardamom', 5)], 1600, [PIECE(40, '1 шекербура', '1 piece')])
dish('badambura', 'Бадамбура (Badambura)', 'Badambura almond roll', 'badambura бадамбура',
     [('flour', 500), ('butter', 250), ('sour_cream', 150), ('yeast', 10), ('almond', 300), ('sugar', 300)], 1450, [PIECE(40, '1 шт.', '1 piece')])
dish('qogal', 'Гогал (Qoğal)', 'Gogal: flaky Novruz bun', 'qoğal qogal гогал',
     [('flour', 500), ('butter', 250), ('yogurt', 150), ('egg', 50), ('yeast', 10), ('turmeric', 2), ('fennel', 5)], 900, [PIECE(70, '1 гогал', '1 bun')])
dish('fesali', 'Фясали (Fəsəli)', 'Fesali flaky flatbread', 'fəsəli fesali фясали',
     [('flour', 500), ('water', 250), ('butter', 200)], 850, [PIECE(170, '1 фясали', '1 flatbread')])
dish('un-halvasi', 'Халва мучная (Un halvası)', 'Flour halva', 'un halvası halva халва мучная',
     [('flour', 300), ('butter', 200), ('sugar', 250), ('water', 200), ('saffron', 0.3)], 800, [PLATE(60)])
dish('seker-corek', 'Шекер-чурек (Şəkər çörəyi)', 'Sugar shortbread (Şəkər çörəyi)', 'şəkər çörəyi seker corek шекер чурек печенье',
     [('flour', 500), ('butter', 250), ('powder_sugar', 200), ('yolk', 40)], 950, [PIECE(30, '1 печенье', '1 cookie')])
dish('napoleon', 'Торт «Наполеон»', 'Napoleon cake', 'наполеон napoleon tort',
     [('puff', 600), ('milk', 1000), ('sugar', 200), ('egg', 100), ('flour', 60), ('butter', 150)], 1950, [PIECE(120, '1 кусок', '1 slice')])
dish('medovik', 'Торт «Медовик»', 'Honey cake (medovik)', 'медовик medovik ballı tort',
     [('flour', 450), ('honey', 100), ('sugar', 350), ('butter', 100), ('egg', 150), ('sour_cream', 800)], 1850, [PIECE(120, '1 кусок', '1 slice')])
dish('ponchiki', 'Пончики жареные', 'Fried doughnuts (ponchiki)', 'пончики пышки ponçik',
     [('flour', 500), ('milk', 250), ('yeast', 10), ('sugar', 50), ('egg', 50), ('oil', 120)], 950, [PIECE(50, '1 пончик', '1 doughnut')])
dish('sharlotka', 'Пирог-шарлотка с яблоками', 'Apple sharlotka cake', 'шарлотка alma piroqu',
     [('flour', 160), ('sugar', 200), ('egg', 200), ('apple', 500)], 950, [PIECE(110, '1 кусок', '1 slice')])

# --- Drinks ---
dish('ayran', 'Айран домашний (Ayran)', 'Homemade ayran', 'ayran айран тан', [('yogurt', 500), ('water', 500), ('salt', 5)], 1005,
     [('Стакан (250 мл)', 'Glass (250 ml)', 250)])
dish('tea-sugar', 'Чай чёрный с 1 ч. л. сахара', 'Black tea with 1 teaspoon of sugar', 'çay şəkərli чай с сахаром', [('tea', 200), ('sugar', 5)], 205,
     [('Стакан армуду (≈100 мл)', 'Armudu glass (~100 ml)', 100), ('Чашка (≈205 мл)', 'Cup (~205 ml)', 205)])
dish('kompot-dried', 'Компот из сухофруктов', 'Dried fruit compote', 'компот kompot quru meyvə',
     [('dried_apricot', 100), ('prunes', 50), ('raisins', 50), ('sugar', 100), ('water', 2000)], 2100, [('Стакан (250 мл)', 'Glass (250 ml)', 250)])
dish('kompot-cherry', 'Компот вишнёвый', 'Sour cherry compote', 'компот вишня albalı kompotu',
     [('sour_cherry', 400), ('sugar', 200), ('water', 2000)], 2400, [('Стакан (250 мл)', 'Glass (250 ml)', 250)])
dish('serbet', 'Шербет (Şərbət)', 'Sherbet drink', 'şərbət serbet шербет',
     [('sugar', 150), ('water', 1000), ('lemon', 30), ('saffron', 0.2)], 1180, [('Стакан (250 мл)', 'Glass (250 ml)', 250)])

def build():
    out = []
    for ident, ru, en, words, parts, cooked, portions, note in D:
        ingredients = []
        for key, grams in parts:
            ref = key if key.startswith('local:') else I[key][0]
            ingredients.append([ref, grams])
        def label(key, lang):
            if key.startswith('local:'):
                return {'local:dish-lavash': ('лаваш', 'lavash'), 'local:dish-tendir': ('тандырный хлеб', 'tandir bread')}[key][lang]
            return I[key][1 + lang]
        def fmt(g):
            return ('%g' % g).replace('.', ',')
        listing_ru = ', '.join(f'{label(k, 0)} {fmt(g)} г' for k, g in parts)
        listing_en = ', '.join(f'{label(k, 1)} {g:g} g' for k, g in parts)
        out.append({
            'id': 'local:dish-' + ident, 'name': ru, 'nameEn': en, 'aliases': [words],
            'basis': {'ingredients': ingredients, 'grams': cooked},
            'portions': [{'name': p[0], 'nameEn': p[1], 'grams': p[2]} for p in portions] + [{'name': 'Весь рецепт (≈%d г)' % cooked, 'nameEn': 'Whole recipe (~%d g)' % cooked, 'grams': cooked}],
            'source': f'Расчёт по рецепту из ингредиентов USDA SR Legacy: {listing_ru}; выход готового блюда ≈{cooked} г. {note}'.strip(),
            'sourceEn': f'Recipe estimate from USDA SR Legacy ingredients: {listing_en}; cooked weight ~{cooked} g.',
        })
    return out


if __name__ == '__main__':
    items = build()
    assert len({i['id'] for i in items}) == len(items)
    Path(__file__).with_name('dish-foods.json').write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding='utf-8')
    print(len(items), 'dishes')
