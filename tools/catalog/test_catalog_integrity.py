"""Check the shipped dataset, independently of the catalog generator."""
import json
import math
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class CatalogIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.foods = json.loads((ROOT / 'app/src/main/assets/catalog/foods.json').read_text(encoding='utf-8'))

    def test_all_shipped_foods_have_valid_masses_and_nutrients(self):
        self.assertEqual(len(self.foods), len({f['id'] for f in self.foods}))
        for food in self.foods:
            with self.subTest(food=food['id']):
                self.assertIsNotNone(food['per100g']['kcal'])
                for key, value in food['per100g'].items():
                    if value is not None:
                        self.assertTrue(math.isfinite(value))
                        self.assertGreaterEqual(value, 0)
                        self.assertLessEqual(value, 1000 if key == 'kcal' else 100)
                for portion in food.get('portions', []):
                    self.assertTrue(math.isfinite(portion['grams']) and 0 < portion['grams'] <= 100000)
                if food.get('density') is not None:
                    self.assertTrue(0 < food['density'] <= 20)

    def test_usda_values_match_the_downloaded_source(self):
        source = ROOT / 'tools/catalog/raw/usda-slim.json'
        if not source.exists():
            self.skipTest('Original USDA source is local and is not committed')
        rows = next(iter(json.loads(source.read_text(encoding='utf-8')).values()))
        shipped = {f['id']: f for f in self.foods}
        self.assertEqual(len(rows), sum(f['id'].startswith('usda:') for f in self.foods))
        for row in rows:
            nutrients = {str(n['nutrient']['id']): n.get('amount') for n in row['foodNutrients']}
            food = shipped['usda:' + str(row['fdcId'])]
            for key, nutrient_id in [('kcal', '1008'), ('protein', '1003'), ('fat', '1004'), ('carbs', '1005'), ('fiber', '1079')]:
                expected = nutrients.get(nutrient_id)
                # No carbohydrate implies zero fibre; otherwise missing values stay unknown.
                if key == 'fiber' and expected is None and nutrients.get('1005') == 0:
                    expected = 0
                self.assertEqual(expected, food['per100g'][key], (food['id'], key))


if __name__ == '__main__':
    unittest.main()
