"""Shelf categories for the food search: one per catalog food, from the source's own category where it has one.

USDA SR Legacy and FNDDS carry a food category of their own; Bazarstore products carry the shop's product type as the
first part of their name ("Йогурт · Aktivia ..."); the app's own dishes are sorted by their id and name. English
keywords then split the broad source groups (USDA "Beverages" into coffee and tea, alcohol and other drinks; "Baked
Products" into sweets and bread). Nothing here changes nutrition values.
"""
import re

CATEGORIES = ('fastfood', 'dishes', 'coffee', 'drinks', 'alcohol', 'sweets', 'snacks', 'dairy', 'meat', 'fish', 'fruitveg', 'grains', 'ingredients')

ALCOHOL = re.compile(r'\b(alcoholic|beer|ale|lager|wine|liquor|liqueur|vodka|whiske?y|rum|gin|brandy|cognac|tequila|sake|champagne|cocktail|martini|margarita|daiquiri|sangria|cider, (hard|alcoholic)|distilled spirits|mojito|pina colada)\b', re.I)
NOT_ALCOHOL = re.compile(r'\b(non-?alcoholic|wine vinegar|cooking wine|root beer|ginger ale|rum cake|rum raisin|wine sauce)\b', re.I)
COFFEE = re.compile(r'\b(coffee|espresso|cappuccino|latte|mocha|macchiato|americano|tea|chai|matcha|cocoa|hot chocolate)\b', re.I)
NOT_COFFEE = re.compile(r'\b(tea ?cake|teaspoon|coffee ?cake|tea biscuit|iced tea mix)\b', re.I)
SWEET_BAKED = re.compile(r'\b(cake|cookie|brownie|pie|pastry|pastries|doughnut|donut|danish|croissant|sweet roll|cinnamon roll|eclair|cream puff|strudel|tart|muffin|cupcake|waffle|wafer|baklava|cheesecake)\b', re.I)
SOUP = re.compile(r'\b(soup|chowder|broth|bouillon|stew|chili)\b', re.I)
JUICE = re.compile(r'\b(juice|nectar|drink|beverage|smoothie)\b', re.I)

USDA = {
    'Beef Products': 'meat', 'Lamb, Veal, and Game Products': 'meat', 'Pork Products': 'meat', 'Poultry Products': 'meat',
    'Sausages and Luncheon Meats': 'meat', 'Finfish and Shellfish Products': 'fish', 'Vegetables and Vegetable Products': 'fruitveg',
    'Fruits and Fruit Juices': 'fruitveg', 'Dairy and Egg Products': 'dairy', 'Legumes and Legume Products': 'ingredients',
    'Fats and Oils': 'ingredients', 'Spices and Herbs': 'ingredients', 'Breakfast Cereals': 'grains', 'Cereal Grains and Pasta': 'grains',
    'Baked Products': 'grains', 'Sweets': 'sweets', 'Snacks': 'snacks', 'Nut and Seed Products': 'snacks', 'Fast Foods': 'fastfood',
    'Restaurant Foods': 'dishes', 'Meals, Entrees, and Side Dishes': 'dishes', 'Soups, Sauces, and Gravies': 'ingredients',
    'Beverages': 'drinks', 'Baby Foods': 'dishes', 'American Indian/Alaska Native Foods': 'dishes',
}

# FNDDS (WWEIA) categories, matched by the start of their name; the first match wins.
FNDDS = [
    (('Burgers', 'Pizza', 'French fries', 'Chicken patties, nuggets', 'Frankfurter sandwiches', 'Chicken fillet sandwiches', 'Nachos',
      'Burritos and tacos', 'Egg/breakfast sandwiches', 'Deli and cured meat sandwiches', 'Seafood sandwiches', 'Meat and BBQ sandwiches',
      'Cheese sandwiches', 'Vegetable sandwiches', 'Peanut butter and jelly sandwiches'), 'fastfood'),
    (('Coffee', 'Tea'), 'coffee'),
    (('Liquor and cocktails', 'Wine', 'Beer'), 'alcohol'),
    (('Fruit drinks', 'Soft drinks', 'Sport and energy drinks', 'Diet sport', 'Other diet drinks', 'Flavored or carbonated water', 'Tap water',
      'Bottled water', 'Enhanced water', 'Citrus juice', 'Apple juice', 'Other fruit juice', 'Vegetable juice', 'Smoothies', 'Nutritional beverages',
      'Plant-based milk', 'Milk shakes'), 'drinks'),
    (('Cookies', 'Cakes and pies', 'Doughnuts', 'Ice cream', 'Candy', 'Pudding', 'Gelatins', 'Jams, syrups', 'Sugars and honey'), 'sweets'),
    (('Crackers', 'Saltine', 'Pretzels', 'Tortilla, corn, other chips', 'Potato chips', 'Popcorn', 'Nuts and seeds', 'Cereal bars', 'Nutrition bars'), 'snacks'),
    (('Cheese', 'Milk', 'Flavored milk', 'Yogurt', 'Cream', 'Cottage', 'Eggs and omelets', 'Plant-based yogurt'), 'dairy'),
    (('Fish', 'Shellfish'), 'fish'),
    (('Beef', 'Pork', 'Lamb', 'Ground beef', 'Chicken, whole pieces', 'Turkey', 'Bacon', 'Cold cuts', 'Sausages', 'Frankfurters', 'Liver'), 'meat'),
    (('Yeast breads', 'Rolls and buns', 'Bagels', 'Biscuits, muffins', 'Tortillas', 'Rice', 'Pasta, noodles', 'Ready-to-eat cereal', 'Oatmeal',
      'Grits', 'Turnovers'), 'grains'),
    (('Dips, gravies', 'Salad dressings', 'Mustard', 'Tomato-based condiments', 'Mayonnaise', 'Soy-based condiments', 'Butter', 'Margarine',
      'Pasta sauces', 'Sugar substitutes', 'Beans, peas, legumes', 'Soy and meat-alternative', 'Protein and nutritional powders'), 'ingredients'),
    (('Apples', 'Bananas', 'Blueberries', 'Citrus fruits', 'Dried fruits', 'Mango', 'Melons', 'Other fruits', 'Peaches', 'Pears', 'Pineapple',
      'Strawberries', 'Broccoli', 'Cabbage', 'Carrots', 'Corn', 'Onions', 'Other dark green', 'Other red and orange', 'Other starchy', 'Other vegetables',
      'Spinach', 'String beans', 'Tomatoes', 'White potatoes', 'Olives, pickles'), 'fruitveg'),
    (('Meat mixed', 'Poultry mixed', 'Seafood mixed', 'Pasta mixed', 'Rice mixed', 'Other Mexican', 'Soups', 'Ramen', 'Fried rice', 'Egg rolls',
      'Stir-fry', 'Macaroni and cheese', 'Mashed potatoes', 'Vegetable dishes', 'Bean, pea, legume dishes', 'Coleslaw', 'Lettuce', 'Fried vegetables',
      'Pancakes, waffles'), 'dishes'),
]

# Bazarstore product types (the part of the name before " · ").
STORE = {
    'Шоколад': 'sweets', 'Снеки и сладости': 'sweets', 'Печенье': 'sweets', 'Вафли': 'sweets', 'Мороженое': 'sweets', 'Пирожное': 'sweets',
    'Шоколадный батончик': 'sweets', 'Мармелад': 'sweets', 'Конфеты': 'sweets', 'Кекс': 'sweets', 'Варенье': 'sweets', 'Выпечка': 'sweets',
    'Торт': 'sweets', 'Продукт для диабетиков': 'sweets', 'Сырок': 'dairy', 'Сыр': 'dairy', 'Йогурт': 'dairy', 'Молоко': 'dairy',
    'Молочное и завтрак': 'dairy', 'Творог': 'dairy', 'Сметана': 'dairy', 'Сливочное масло': 'dairy', 'Кефир': 'dairy', 'Айран': 'dairy',
    'Чипсы': 'snacks', 'Хлебцы': 'snacks', 'Сухарики': 'snacks', 'Орехи': 'snacks', 'Батончик': 'snacks', 'Диетический продукт': 'snacks',
    'Газированный напиток': 'drinks', 'Напиток': 'drinks', 'Сок': 'drinks', 'Энергетик': 'drinks', 'Кофе': 'coffee', 'Какао': 'coffee', 'Чай': 'coffee',
    'Макароны': 'grains', 'Рис': 'grains', 'Овсяные хлопья': 'grains', 'Хлеб': 'grains', 'Гречка': 'grains', 'Сухой завтрак': 'grains',
    'Мясо, птица, рыба': 'meat', 'Сосиски': 'meat', 'Колбаса': 'meat', 'Варёная колбаса': 'meat', 'Морепродукты': 'fish', 'Рыбные консервы': 'fish',
    'Тунец': 'fish', 'Замороженные морепродукты': 'fish', 'Замороженные овощи': 'fruitveg', 'Фрукты и овощи': 'fruitveg', 'Замороженные фрукты': 'fruitveg',
    'Оливки': 'fruitveg', 'Консервы': 'dishes', 'Замороженные полуфабрикаты': 'dishes', 'Бакалея': 'ingredients', 'Соус': 'ingredients',
    'Масло': 'ingredients', 'Мука': 'ingredients', 'Сахар': 'ingredients', 'Чечевица': 'ingredients', 'Кетчуп': 'ingredients',
    'Оливковое масло': 'ingredients', 'Для выпечки': 'ingredients',
}

# The app's own dishes and typical products, by id.
LOCAL = {
    'espresso-double': 'coffee', 'toyuq-popkorn': 'fastfood', 'sosiska-assorti': 'meat', 'puste-antep': 'snacks',
    'dish-shaurma-chicken': 'fastfood', 'dish-shaurma-chicken-fries': 'fastfood', 'dish-doner-beef': 'fastfood', 'dish-doner-bread': 'fastfood',
    'dish-lahmacun': 'fastfood', 'dish-burger-home': 'fastfood', 'dish-hot-dog': 'fastfood', 'dish-lavash': 'grains', 'dish-tendir': 'grains',
    'dish-corek': 'grains', 'dish-paxlava': 'sweets', 'dish-sekerbura': 'sweets', 'dish-badambura': 'sweets', 'dish-qogal': 'sweets',
    'dish-fesali': 'sweets', 'dish-un-halvasi': 'sweets', 'dish-seker-corek': 'sweets', 'dish-napoleon': 'sweets', 'dish-medovik': 'sweets',
    'dish-ponchiki': 'sweets', 'dish-sharlotka': 'sweets', 'dish-ayran': 'dairy', 'dish-tea-sugar': 'coffee', 'dish-kompot-dried': 'drinks',
    'dish-kompot-cherry': 'drinks', 'dish-serbet': 'drinks',
    'typical-lavash-store': 'grains', 'typical-baton': 'grains', 'typical-sushki': 'grains', 'typical-pelmeni-store': 'dishes',
    'typical-vareniki-store': 'dishes', 'typical-instant-noodles': 'dishes', 'typical-sosiski-store': 'meat', 'typical-servelat': 'meat',
    'typical-halva': 'sweets', 'typical-sguschenka': 'sweets', 'typical-plombir': 'sweets', 'typical-pryanik': 'sweets', 'typical-zefir': 'sweets',
    'typical-vafli': 'sweets', 'typical-milk-chocolate': 'sweets', 'typical-kvass': 'drinks', 'typical-mayo-67': 'ingredients',
    'typical-mayo-light': 'ingredients', 'typical-ketchup': 'ingredients', 'typical-semechki': 'snacks', 'typical-chips-store': 'snacks',
}

def source_category(category, name):
    """The shelf for a USDA or FNDDS food from its source category and English name."""
    if category in USDA:
        shelf = USDA[category]
        if category == 'Beverages':
            if ALCOHOL.search(name) and not NOT_ALCOHOL.search(name): return 'alcohol'
            if COFFEE.search(name) and not NOT_COFFEE.search(name): return 'coffee'
            if re.search(r'\bmilk\b', name, re.I) and not JUICE.search(name): return 'dairy'
            return 'drinks'
        if category == 'Fruits and Fruit Juices' and JUICE.search(name): return 'drinks'
        if category == 'Baked Products' and SWEET_BAKED.search(name): return 'sweets'
        if category == 'Soups, Sauces, and Gravies' and SOUP.search(name): return 'dishes'
        if category == 'Dairy and Egg Products' and re.search(r'\b(ice cream|frozen yogurt|dessert)\b', name, re.I): return 'sweets'
        return shelf
    for starts, shelf in FNDDS:
        if category.startswith(starts): return shelf
    # "Not included in a food category" and anything new: decide by the name.
    if ALCOHOL.search(name) and not NOT_ALCOHOL.search(name): return 'alcohol'
    if COFFEE.search(name) and not NOT_COFFEE.search(name): return 'coffee'
    if SWEET_BAKED.search(name): return 'sweets'
    return 'ingredients'

def local_category(ident, name_en):
    """The shelf for a store product or one of the app's own foods."""
    if ident.startswith('local:kfc-'): return 'fastfood'
    if ident.startswith('local:sushi-'): return 'dishes'
    key = ident.split(':', 1)[1]
    if key in LOCAL: return LOCAL[key]
    if ident.startswith('local:typical-'):
        return 'dairy'  # the remaining typical products are kefir, milk, tvorog, sour cream, cheeses and yoghurt drinks
    return 'dishes'

# The shop files cocoa biscuits and coffee-flavoured cookies under "Какао" and "Кофе".
BAKED_IN_DRINK_TYPES = re.compile(r'biskv|biskivi|pe[cç]en|baton|mozaik|lokma|dankek|\bkek\b|cookie|tort|vafli|wafer|milly magic', re.I)

def store_category(name):
    kind = name.split(' · ')[0]
    if kind in ('Какао', 'Кофе') and BAKED_IN_DRINK_TYPES.search(name): return 'sweets'
    if kind == 'Чай' and re.search(r'pendir', name, re.I): return 'dairy'  # a Göyçay-brand cheese filed under tea
    return STORE.get(kind, 'ingredients')
