"""Prepare USDA FNDDS 2021-2023 (Survey Foods, FoodData Central release 2024-10-31) for the catalog.

Download https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_json_2024-10-31.zip into
tools/catalog/raw/fndds/ and unzip it, then run `python tools/catalog/fndds.py`. It writes the compact
raw/fndds-slim.json the catalog build reads, and lists English texts that still need a Russian translation in
raw/fndds-todo-names.json and raw/fndds-todo-portions.json. Nutrition values are copied unchanged.
"""
import json
from pathlib import Path

from curation import fndds_included

HERE = Path(__file__).parent
SOURCE = HERE / 'raw/fndds/surveyDownload.json'
KEEP = (1008, 1003, 1004, 1005, 1079)
# Portion labels that are not a household measure a person would pick.
SKIP_PORTIONS = ('Quantity not specified', 'cubic inch', 'surface inch', 'oz yields', '100 calorie package')


def portions(record):
    out = []
    for p in sorted(record.get('foodPortions', []), key=lambda p: p.get('sequenceNumber') or 0):
        label = (p.get('portionDescription') or '').strip()
        grams = p.get('gramWeight') or 0
        if grams > 0 and label and not any(s in label for s in SKIP_PORTIONS):
            out.append({'name': label, 'grams': grams})
    return out


def main():
    foods = next(iter(json.loads(SOURCE.read_text(encoding='utf-8')).values()))
    slim = []
    for f in foods:
        category = f.get('wweiaFoodCategory', {}).get('wweiaFoodCategoryDescription', '')
        if not fndds_included(f['description'], category):
            continue
        nutrients = [{'nutrient': {'id': n['nutrient']['id']}, 'amount': n.get('amount')}
                     for n in f['foodNutrients'] if n['nutrient']['id'] in KEEP]
        slim.append({'fdcId': f['fdcId'], 'foodCode': f.get('foodCode'), 'description': f['description'],
                     'foodCategory': category, 'foodNutrients': nutrients, 'portions': portions(f)})
    (HERE / 'raw/fndds-slim.json').write_text(json.dumps({'SurveyFoods': slim}), encoding='utf-8')
    names = json.loads((HERE / 'i18n/fndds-names-ru.json').read_text(encoding='utf-8')) if (HERE / 'i18n/fndds-names-ru.json').exists() else {}
    labels = json.loads((HERE / 'i18n/portion-names-ru.json').read_text(encoding='utf-8'))
    todo = {'fndds:%d' % f['fdcId']: f['description'] for f in slim if 'fndds:%d' % f['fdcId'] not in names}
    todo_portions = sorted({p['name'] for f in slim for p in f['portions']} - set(labels))
    (HERE / 'raw/fndds-todo-names.json').write_text(json.dumps(todo, ensure_ascii=False, indent=0), encoding='utf-8')
    (HERE / 'raw/fndds-todo-portions.json').write_text(json.dumps(todo_portions, ensure_ascii=False, indent=0), encoding='utf-8')
    print(f'FNDDS foods kept: {len(slim)} of {len(foods)}; names to translate: {len(todo)}; portion labels to translate: {len(todo_portions)}')


if __name__ == '__main__':
    main()
