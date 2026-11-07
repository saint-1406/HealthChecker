"""Which bundled USDA SR Legacy foods the food search shows.

SR Legacy is a reference for American dietitians: it lists ~1 400 near-identical meat cuts (grade, fat trim, lean or
lean-and-fat, imported), baby food, American restaurant menus and Alaska Native foods. Hidden foods stay in the
catalog, so diary entries, favourites and saved meals that use them still resolve, but search does not offer them.
Nothing here changes nutrition values.
"""
import re

HIDDEN_CATEGORIES = {'Baby Foods', 'American Indian/Alaska Native Foods'}

# Chains with restaurants in Baku keep their menu items; other American chains are hidden.
CHAINS_IN_BAKU = ("McDONALD'S", 'KFC', 'BURGER KING', "PAPA JOHN'S", "DOMINO'S", 'PIZZA HUT', 'SUBWAY', 'POPEYES', 'STARBUCKS')

HIDDEN_NAMES = re.compile('|'.join([
    # Exotic game and whole-carcass or by-product entries nobody logs.
    r'^Game meat, (bear|beaver|beefalo|buffalo|caribou|moose|muskrat|opossum|raccoon|squirrel|antelope|boar|elk|bison)',
    r'\b(polar bear|seal|walrus|whale|beluga|oopah|caribou|moose)\b',
    r'\b(brain|spleen|thymus|lungs|pancreas|chitterlings|suet|mechanically (separated|deboned))\b',
    r'\b(external|seam|subcutaneous|kidney and pelvic|intermuscular|separable) fat\b', r'\bfat only\b', r'\bcarcass\b',
    r'\bgiblets\b|\bneck\b|\bbacks?\b(?!.*bacon)', r'\b(capons|stewing|roasting)\b',
    r'(New Zealand|Australian|new zealand), imported', r'\bWagyu\b', r'\bfrozen entree\b',
]), re.I)

MEAT_CATEGORIES = {'Beef Products', 'Pork Products', 'Lamb, Veal, and Game Products'}
GRADE = re.compile(r',? (?:USDA )?(choice|select|prime|all grades)\b', re.I)
TRIM = re.compile(r', trimmed to ([0-9/]+)" fat', re.I)
LEAN = re.compile(r', separable lean (only|and fat)', re.I)


def _rank(name):
    """Lower is the more generic variant: all grades, no fat trim stated or trimmed to 0", lean and fat."""
    grade = GRADE.search(name)
    grade = {'all grades': 0, 'choice': 1, 'select': 2, 'prime': 3}[grade.group(1).lower()] if grade else 0
    trim = TRIM.search(name)
    trim = {'0': 0, '1/8': 1, '1/4': 2}.get(trim.group(1), 3) if trim else 0
    lean = LEAN.search(name)
    lean = 0 if not lean or lean.group(1) == 'and fat' else 1
    # Ground meat: loose crumbles are what home cooking (and a stuffing or sauce) uses, not a meat loaf.
    shape = 1 if re.search(r'\b(loaf|patty|patties)\b', name, re.I) else 0
    return (grade, trim, lean, shape, len(name))


SKIP_PARTS = {'fresh', 'cured', 'domestic', 'variety meats and by-products', 'retail parts', 'whole', 'composite of trimmed retail cuts'}
# Cuts people ask for by name keep their own entry even inside a larger primal (loin, rib, plate…).
NAMED_CUTS = ('tenderloin', 'rib eye', 'ribeye', 'top sirloin', 'brisket', 'flank', 'skirt', 't-bone', 'porterhouse',
              'short ribs', 'spareribs', 'backribs', 'chop', 'bacon', 'ham', 'liver', 'heart', 'kidneys', 'tongue', 'tripe',
              'shank', 'cubed for stew', 'for stew', 'leg', 'shoulder', 'breast', 'rack')


def meat_key(name):
    """Animal, cut (ground meat by fat share) and raw or cooked: one visible entry for each."""
    low = re.sub(r'\s+', ' ', GRADE.sub('', TRIM.sub('', LEAN.sub('', name))).lower())
    parts = [p.strip() for p in low.split(',')]
    animal = parts[0].split()[0]
    state = 'raw' if re.search(r'\braw\b', low) else 'cooked'
    if 'ground' in parts[1:3]:
        fat = re.search(r'(\d+)% lean', low)
        return (animal, 'ground ' + (fat.group(1) if fat else ''), state)
    named = next((c for c in NAMED_CUTS if re.search(r'\b' + c + r'\b', low)), None)
    if named:
        return (animal, named, state)
    cut = next((p for p in parts[1:] if p not in SKIP_PARTS), '')
    return (animal, cut, state)


def hidden_ids(foods, categories):
    """foods: {id: {'nameEn': ...}}, categories: {id: USDA category}. Returns the ids search should not offer."""
    hidden = set()
    for ident, food in foods.items():
        name = food['nameEn'];category = categories.get(ident, '')
        if category in HIDDEN_CATEGORIES or HIDDEN_NAMES.search(name):
            hidden.add(ident)
        elif category in ('Fast Foods', 'Restaurant Foods'):
            brand = name.split(',')[0].strip()
            # "Fast foods, hamburger" and "Restaurant, Chinese, fried rice" are generic; branded menus are not.
            if brand.upper() == brand and not brand.startswith(CHAINS_IN_BAKU):
                hidden.add(ident)
    groups = {}
    for ident, food in foods.items():
        if ident in hidden or categories.get(ident) not in MEAT_CATEGORIES:
            continue
        groups.setdefault(meat_key(food['nameEn']), []).append(ident)
    for members in groups.values():
        keep = min(members, key=lambda i: _rank(foods[i]['nameEn']))
        hidden.update(i for i in members if i != keep)
    return hidden


FNDDS_SKIP_CATEGORY = re.compile(r'^(Baby|Formula|Human milk)', re.I)
# FNDDS lists each dish once per added fat, milk or sweetener; the generic variant ("fat added", "NS as to …") stays.
FNDDS_SKIP = re.compile('|'.join([
    r'\bschool\b', r'\bWIC\b', r'\binfant\b|\btoddler\b', r'\bpuerto rican\b', r'\bmeatless\b',
    r'(made|cooked|prepared|fried) (with|in) (butter|margarine|oil|olive oil|vegetable oil|canola oil|corn oil|animal fat|lard|shortening|cooking spray|coconut oil)',
    r'with (butter|margarine|oil|cooking spray|animal fat) (added|$)', r'fat added in cooking, (butter|margarine|oil)',
    r'\b(light|reduced fat|fat free|low fat|nonfat) (butter|margarine)\b',
    r'(made with|from) (fat free|low fat|reduced fat|light) (mayonnaise|dressing|cream cheese|sour cream|milk|cheese)',
    r'\bmade with (whole|reduced fat|low ?fat|fat free|nonfat|skim|2%|1%) milk\b',
    r'\bsugar free\b|\bdiet\b|artificial sweetener|\blow calorie\b|reduced sodium|low sodium|no salt added',
    r'\bNS as to (type of milk|form)\b',
    # Raw single foods are already in SR Legacy under their usual names.
    r', raw\b',
    # One variant is enough: fresh (not canned or frozen) vegetables, dishes "including" carrots and greens,
    # sandwiches on white bread, untoasted bread, beans from cans, steak with its fat.
    r'excluding carrots, broccoli,? and(/or)? dark-green leafy', r'^[^,]+, (canned|frozen), cooked', r', toasted\b',
    r'\bon (whole )?wheat( bun| bread| roll)?\b', r'\bfrom dried\b', r'\b(lean only|NS as to fat) eaten\b',
    r'\bfor use (on|with|in)\b', r'\bas ingredient in recipes\b', r'\bfrozen meal\b',
    r'^(Armadillo|Bear|Beaver|Opossum|Raccoon|Squirrel|Bison|Frog legs|Turtle|Alligator|Caribou|Moose|Venison|Elk)\b',
    # Chicken pieces: keep home-cooked (from raw) and fast food, with or without skin; drop the rest.
    r'\bfrom (pre-?cooked|other sources)\b', r'\bNS as to cooking method\b', r'skin / coating not eaten',
    r'prepared skinless, coating eaten', r'\bwith marinade\b', r'excluding carrorts', r'\bNS as to (part|skin eaten)\b',
    # Decaffeinated coffee has the nutrients of regular coffee; dry instant coffee is logged as the drink.
    r'\bdecaffeinated\b', r'^Coffee,.*\bnot reconstituted\b',
    # "NS as to fat" (unknown whether fat was added) duplicates "fat added"/"no added fat"; egg substitute is a US product.
    r'\bNS as to fat(?! type)\b', r'^Egg substitute\b', r'^Fluid replacement\b', r'^Frozen dinner\b',
    r'^Pasta, whole grain, with\b', r'\bready-to-heat\b',
    # Sushi pieces are modelled for Baku sushi bars in sushi.py; per-100 g American rolls would only crowd them.
    r'^Sushi\b',
]), re.I)


def fndds_included(description, category):
    """FNDDS (foods "as eaten") adds cooked dishes, drinks and desserts; baby food and near-duplicate variants stay out."""
    return not FNDDS_SKIP_CATEGORY.search(category or '') and not FNDDS_SKIP.search(description)


FRESH_CATEGORIES ={'Fruits and Fruit Juices', 'Vegetables and Vegetable Products', 'Spices and Herbs'}
RAW_RU = re.compile(r'\b(сыр(ой|ая|ое|ые))\b')
FRESH_RU = {'сырой': 'свежий', 'сырая': 'свежая', 'сырое': 'свежее', 'сырые': 'свежие'}


PROGRAM_NOTE = re.compile(r"\s*\((?:включа\w+ продукты (?:для )?[Пп]рограммы распределения продовольствия USDA|Includes foods for USDA's Food Distribution Program)\)")


def clean_name(name):
    """Drop the note about the US Food Distribution Program: it says nothing about the food."""
    return PROGRAM_NOTE.sub('', name).strip()


def fresh_name(name_ru, category):
    """For fruit, vegetables and herbs USDA "raw" means fresh: "Ананас, сырой" reads as "Ананас, свежий"."""
    name_ru = clean_name(name_ru)
    if category not in FRESH_CATEGORIES:
        return name_ru
    return RAW_RU.sub(lambda m: FRESH_RU[m.group(1)], name_ru)
