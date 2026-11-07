import unittest
from curation import hidden_ids, fresh_name, clean_name, fndds_included
from build_catalog import normalize_record, extra_food
from stores.match import shelf_name, plausible


class CurationTest(unittest.TestCase):
    def test_meat_variants_collapse_to_one_raw_and_one_cooked(self):
        cat = 'Beef Products'
        foods = {
            'a': 'Beef, loin, tenderloin steak, boneless, separable lean and fat, trimmed to 0" fat, all grades, raw',
            'b': 'Beef, loin, tenderloin steak, boneless, separable lean only, trimmed to 0" fat, choice, raw',
            'c': 'Beef, loin, tenderloin steak, boneless, separable lean and fat, trimmed to 1/8" fat, select, raw',
            'd': 'Beef, loin, tenderloin steak, boneless, separable lean and fat, trimmed to 0" fat, all grades, cooked, grilled',
        }
        hidden = hidden_ids({k: {'nameEn': v} for k, v in foods.items()}, {k: cat for k in foods})
        self.assertEqual(hidden, {'b', 'c'})

    def test_baby_food_alaska_and_far_chains_hidden_baku_chains_kept(self):
        foods = {'baby': ('Babyfood, apple', 'Baby Foods'), 'seal': ('Seal, bearded, meat, raw (Alaska Native)', 'American Indian/Alaska Native Foods'),
                 'apple': ("APPLEBEE'S, french fries", 'Restaurant Foods'), 'mc': ("McDONALD'S, Hamburger", 'Fast Foods'),
                 'generic': ('Fast foods, hamburger', 'Fast Foods')}
        hidden = hidden_ids({k: {'nameEn': v[0]} for k, v in foods.items()}, {k: v[1] for k, v in foods.items()})
        self.assertEqual(hidden, {'baby', 'seal', 'apple'})

    def test_raw_fruit_reads_fresh_in_russian(self):
        self.assertEqual(fresh_name('Ананас, сырой', 'Fruits and Fruit Juices'), 'Ананас, свежий')
        self.assertEqual(fresh_name('Говядина, сырая', 'Beef Products'), 'Говядина, сырая')
        self.assertEqual(clean_name("Oranges, raw (Includes foods for USDA's Food Distribution Program)"), 'Oranges, raw')

    def test_fndds_drops_variants_and_baby_food(self):
        self.assertTrue(fndds_included('Coffee, Latte', 'Coffee'))
        self.assertFalse(fndds_included('Coffee, Latte, decaffeinated', 'Coffee'))
        self.assertFalse(fndds_included('Apple, raw', 'Apples'))
        self.assertFalse(fndds_included('Rice cereal', 'Baby food: cereals'))
        self.assertFalse(fndds_included('Peas, canned, cooked, no added fat', 'Peas'))

    def test_fndds_record_keeps_values_and_portions(self):
        r = normalize_record({'fdcId': 7, 'description': 'Coffee, Latte', 'foodNutrients': [{'nutrient': {'id': 1008}, 'amount': 54}],
                              'portions': [{'name': '1 cup', 'grams': 240}]}, 'fndds')
        self.assertEqual(r['id'], 'fndds:7');self.assertEqual(r['per100g']['kcal'], 54)
        self.assertEqual(r['portions'], [{'name': '1 cup', 'grams': 240}]);self.assertIn('FNDDS', r['source'])

    def test_label_values_are_copied(self):
        f = extra_food({'id': 'store:1', 'name': 'X', 'nameEn': 'X', 'source': 's', 'common': False,
                        'per100g': {'kcal': 50, 'protein': 3, 'fat': 2.5, 'carbs': 4, 'fiber': 0}}, {})
        self.assertEqual(f['per100g']['kcal'], 50);self.assertFalse(f['common'])

    def test_store_names_and_label_check(self):
        self.assertEqual(shelf_name('AKTİVİA YOQURT İÇMƏLİ 270 Q'), 'Aktivia Yoqurt İçməli 270 г')
        self.assertEqual(shelf_name('AZERSUD AYRAN 1 L'), 'Azersud Ayran 1 л')
        self.assertTrue(plausible(50, 3, 2.5, 4))
        self.assertFalse(plausible(500, 3, 2.5, 4))


if __name__ == '__main__':
    unittest.main()
