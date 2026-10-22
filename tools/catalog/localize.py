"""Reviewed common foods plus bilingual category search aliases. No nutrition values are invented."""
import json
from pathlib import Path

foods=next(iter(json.loads(next(Path('tools/catalog/raw/usda').glob('*.json')).read_text()).values()))
by_id={f['fdcId']:f for f in foods}
curated={
171287:('Яйцо куриное, сырое','яйца yumurta'),173424:('Яйцо куриное, варёное','яйца yumurta qaynadılmış'),173423:('Яйцо жареное','яичница yumurta qızardılmış'),
173904:('Овсяные хлопья, сухие','овсянка yulaf'),173905:('Овсяная каша на воде','овсянка yulaf sıyığı'),
171477:('Куриная грудка без кожи, запечённая','курица toyuq döş əti'),171478:('Куриная грудка без кожи, отварная','курица варёная вареная toyuq döşü qaynadılmış'),171474:('Куриная грудка с кожей, сырая','курица toyuq'),
168877:('Рис белый длиннозёрный, сухой','düyü'),168878:('Рис белый длиннозёрный, варёный','düyü'),170685:('Гречневая крупа, сухая','qarabaşaq'),170686:('Гречка варёная','qarabaşaq'),
172816:('Хлеб пита, белый','pita çörək лепёшка'),172688:('Хлеб цельнозерновой','çörək'),174924:('Хлеб белый','çörək'),
173420:('Сыр фета','pendir ağ pendir'),172179:('Творог зернёный со сливками','kəsmik'),171265:('Молоко 3,25%, с витамином D','süd'),
171284:('Йогурт натуральный, цельное молоко','qatıq йогурт аналог'),170886:('Йогурт натуральный, пониженной жирности','qatıq'),170887:('Йогурт натуральный, обезжиренный','qatıq'),
170904:('Кефир нежирный LIFEWAY','kefir'),173944:('Банан свежий','banan'),168409:('Огурец свежий с кожурой','xiyar'),170393:('Морковь свежая','kök'),170000:('Лук репчатый свежий','soğan'),
172420:('Чечевица сухая','mərcimək'),172421:('Чечевица варёная без соли','mərcimək'),173756:('Нут сухой','noxud'),173757:('Нут варёный без соли','noxud'),
170187:('Грецкий орех','qoz орехи'),171413:('Масло оливковое','zeytun yağı'),174370:('Фарш из баранины, сырой','qoyun əti'),172544:('Фарш из баранины, приготовленный','qoyun əti'),
173647:('Вода питьевая','su вода стакан'),
}
rules=[
('Apples, raw, with skin','Яблоко свежее с кожурой','alma'),('Pears, raw','Груша свежая','armud'),('Peaches, raw','Персик свежий','şaftalı'),('Apricots, raw','Абрикос свежий','ərik'),('Plums, raw','Слива свежая','gavalı'),('Pomegranates, raw','Гранат свежий','nar'),('Grapes, red or green','Виноград красный или зелёный','üzüm'),('Oranges, raw, all commercial','Апельсин свежий','portağal'),('Lemons, raw, without peel','Лимон без кожуры','limon'),('Tangerines, (mandarin oranges), raw','Мандарин свежий','naringi'),('Watermelon, raw','Арбуз свежий','qarpız'),('Melons, cantaloupe, raw','Дыня канталупа','yemiş'),('Strawberries, raw','Клубника свежая','çiyələk'),('Cherries, sweet, raw','Черешня свежая','gilas'),('Cherries, sour, red, raw','Вишня свежая','albalı'),('Figs, raw','Инжир свежий','əncir'),('Persimmons, japanese, raw','Хурма свежая','xurma'),('Kiwifruit, green, raw','Киви свежий','kivi'),('Avocados, raw, all commercial','Авокадо свежий','avokado'),
('Tomatoes, red, ripe, raw','Помидор свежий','pomidor'),('Peppers, sweet, red, raw','Перец сладкий красный','bibər'),('Peppers, sweet, green, raw','Перец сладкий зелёный','bibər'),('Eggplant, raw','Баклажан свежий','badımcan'),('Cabbage, raw','Капуста белокочанная','kələm'),('Cauliflower, raw','Цветная капуста','gül kələmi'),('Broccoli, raw','Брокколи свежая','brokoli'),('Spinach, raw','Шпинат свежий','ispanaq'),('Parsley, fresh','Петрушка свежая','cəfəri'),('Coriander (cilantro) leaves, raw','Кинза свежая','keşniş'),('Dill weed, fresh','Укроп свежий','şüyüd'),('Garlic, raw','Чеснок свежий','sarımsaq'),('Radishes, raw','Редис свежий','turp'),('Beets, raw','Свёкла свежая','çuğundur'),('Potatoes, flesh and skin, raw','Картофель сырой с кожурой','kartof'),('Potatoes, boiled, cooked without skin','Картофель варёный без кожуры','kartof'),('Mushrooms, white, raw','Шампиньоны свежие','göbələk'),
('Nuts, almonds','Миндаль','badam'),('Nuts, hazelnuts or filberts','Фундук','fındıq'),('Nuts, pistachio nuts, raw','Фисташки сырые','püstə'),('Nuts, cashew nuts, raw','Кешью сырой','keşyu'),('Peanuts, all types, raw','Арахис сырой','yer fındığı'),('Seeds, sunflower seed kernels, dried','Семечки подсолнечника сушёные','günəbaxan tumu'),('Seeds, pumpkin and squash seed kernels, dried','Тыквенные семечки','balqabaq tumu'),('Seeds, sesame seeds, whole, dried','Кунжут сушёный','küncüt'),
('Butter, salted','Масло сливочное солёное','kərə yağı'),('Butter, without salt','Масло сливочное несолёное','kərə yağı'),('Oil, sunflower, linoleic','Масло подсолнечное','günəbaxan yağı'),('Honey','Мёд','bal'),('Sugars, granulated','Сахар белый','şəkər'),('Salt, table','Соль поваренная','duz'),
('Bulgur, dry','Булгур сухой','bulqur'),('Bulgur, cooked','Булгур варёный','bulqur'),('Millet, raw','Пшено сухое','darı'),('Barley, pearled, raw','Перловая крупа сухая','arpa'),('Quinoa, uncooked','Киноа сухая','kinoa'),('Pasta, dry, enriched','Макароны сухие','makaron'),('Spaghetti, cooked, enriched, without added salt','Спагетти варёные без соли','makaron'),('Wheat flour, white, all-purpose, unenriched','Мука пшеничная','un'),
('Fish, salmon, Atlantic, farmed, raw','Лосось атлантический фермерский, сырой','qızılbalıq'),('Fish, salmon, Atlantic, farmed, cooked, dry heat','Лосось атлантический, запечённый','qızılbalıq'),('Fish, trout, rainbow, farmed, raw','Форель радужная, сырая','alabalıq'),('Fish, tuna, light, canned in water, drained solids','Тунец консервированный в воде, без жидкости','tuna'),
('Dates, deglet noor','Финики Деглет Нур','xurma qurusu'),('Raisins, seedless','Изюм без косточек','kişmiş'),('Apricots, dried, sulfured, uncooked','Курага','quru ərik'),('Figs, dried, uncooked','Инжир сушёный','quru əncir'),('Chocolate, dark, 70-85% cacao solids','Шоколад тёмный 70–85%','şokolad'),('Cocoa, dry powder, unsweetened','Какао-порошок без сахара','kakao'),
('Beverages, tea, black, brewed, prepared with tap water','Чай чёрный без сахара','çay'),('Beverages, coffee, brewed, prepared with tap water','Кофе чёрный без сахара','qəhvə американо americano'),
]
for prefix,ru,az in rules:
    matches=[f for f in foods if f['description'].lower().startswith(prefix.lower())]
    if matches:
        f=min(matches,key=lambda x:len(x['description']));curated[f['fdcId']]=(ru,az)
# Everyday single foods people log most; USDA "stewed" chicken is simmered in liquid, i.e. boiled.
curated.update({
171077:('Куриное филе без кожи, сырое','курица куриная грудка филе toyuq döş'),
171891:('Кофе эспрессо','espresso эспрессо qəhvə'),172389:('Куриное бедро без кожи, отварное','курица варёная вареная toyuq budu qaynadılmış'),
171496:('Индейка, грудка без кожи, запечённая','hinduşka döş'),172182:('Творог зернёный 2%','kəsmik'),171257:('Сметана','xama'),
171267:('Молоко 2%, с витаминами A и D','süd'),172013:('Колбаса варёная, говядина и свинина','kolbasa'),
168555:('Картофельное пюре с молоком и маслом','kartof püresi'),170698:('Картофель фри (фастфуд)','kartof fri'),
171917:('Чай зелёный без сахара','yaşıl çay'),174852:('Кола','kola'),169098:('Апельсиновый сок свежевыжатый','portağal şirəsi'),
168746:('Пиво светлое','pivə'),173190:('Вино красное сухое','şərab'),173414:('Сыр чеддер','pendir'),170845:('Сыр моцарелла','pendir'),
170190:('Каштаны жареные','şabalıd'),
})
aliases={}
for ident,(name,alias) in curated.items():
    if ident in by_id:aliases['usda:'+str(ident)]={'name':name,'aliases':[alias]}
# Source weights: large egg 50g; water glass is an explicitly editable household estimate.
for ident in (171287,173424):
    aliases['usda:'+str(ident)]['portions']=[{'name':'Яйцо (примерно)','nameEn':'Egg (approx.)','grams':50}]
aliases['usda:173647'].update(density=1.0,isWater=True,portions=[{'name':'Стакан (250 мл)','nameEn':'Glass (250 ml)','grams':250}])
# A single espresso shot is about 30 ml; coffee is close enough to water density for millilitres.
aliases['usda:171891'].update(density=1.0,portions=[{'name':'Эспрессо (30 мл)','nameEn':'Espresso shot (30 ml)','grams':30}])
Path('tools/catalog/aliases.json').write_text(json.dumps(aliases,ensure_ascii=False,indent=2),encoding='utf-8')
# Keep compact raw inputs for rebuilding without parsing the 210 MB source each time.
slim=[]
for f in foods:
    slim.append({k:f[k] for k in ('fdcId','description','foodNutrients','foodPortions') if k in f})
for f in slim:
    f['foodNutrients']=[{'nutrient':{'id':n['nutrient']['id']},'amount':n.get('amount')} for n in f['foodNutrients'] if n['nutrient']['id'] in (1008,1003,1004,1005,1079)]
Path('tools/catalog/raw/usda-slim.json').write_text(json.dumps({'SRLegacyFoods':slim}),encoding='utf-8')
print('Localized:',len(aliases))
