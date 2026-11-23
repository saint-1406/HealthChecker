"""Shelves of the food search: every shipped food has one, and the usual suspects land where people look for them."""
import json
import unittest
from pathlib import Path

from categories import CATEGORIES, source_category, store_category

ROOT = Path(__file__).resolve().parents[2]


class CategoriesTest(unittest.TestCase):
    def test_source_groups_are_split_by_name(self):
        self.assertEqual('alcohol', source_category('Beverages', 'Alcoholic beverage, wine, table, red'))
        self.assertEqual('coffee', source_category('Beverages', 'Beverages, coffee, brewed, prepared with tap water'))
        self.assertEqual('coffee', source_category('Beverages', 'Beverages, tea, black, brewed, prepared with distilled water'))
        self.assertEqual('drinks', source_category('Beverages', 'Beverages, carbonated, cola, regular'))
        self.assertEqual('drinks', source_category('Beverages', 'Beverages, ginger ale'))
        self.assertEqual('sweets', source_category('Baked Products', 'Cake, chocolate, commercially prepared'))
        self.assertEqual('grains', source_category('Baked Products', 'Bread, wheat'))
        self.assertEqual('drinks', source_category('Fruits and Fruit Juices', 'Orange juice, raw'))
        self.assertEqual('dishes', source_category('Soups, Sauces, and Gravies', 'Soup, chicken noodle, canned'))
        self.assertEqual('fastfood', source_category('Burgers', 'Cheeseburger, from fast food'))
        self.assertEqual('alcohol', source_category('Liquor and cocktails', 'Vodka'))

    def test_store_types_and_misfiled_products(self):
        self.assertEqual('coffee', store_category('Кофе · Nescafe Original 240 мл Soyuq Kofe'))
        self.assertEqual('sweets', store_category('Какао · Gullon Chip Kakaolu Biskivi 125 г'))
        self.assertEqual('dairy', store_category('Йогурт · Aktivia'))

    def test_every_shipped_food_has_a_known_shelf(self):
        foods = json.loads((ROOT / 'app/src/main/assets/catalog/foods.json').read_text(encoding='utf-8'))
        self.assertTrue(all(f.get('category') in CATEGORIES for f in foods))
        shelf = {f['id']: f['category'] for f in foods}
        self.assertEqual('fastfood', shelf['local:kfc-twister-original'])
        self.assertEqual('coffee', shelf['local:espresso-double'])
        self.assertEqual('dishes', shelf['local:dish-shah-plov'])
        self.assertEqual('sweets', shelf['local:dish-paxlava'])


if __name__ == '__main__':
    unittest.main()
