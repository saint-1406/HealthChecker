import unittest
from build_catalog import normalize_record

class CatalogTest(unittest.TestCase):
    def test_off_missing_is_not_zero(self):
        r=normalize_record({'code':'123','product_name':'Milk','nutriments':{'energy-kcal_100g':42}},'off')
        self.assertEqual(r['per100g']['kcal'],42)
        self.assertIsNone(r['per100g']['protein'])
    def test_kj_converted_not_copied(self):
        r=normalize_record({'code':'123','product_name':'Milk','nutriments':{'energy_100g':418.4,'energy_unit':'kJ'}},'off')
        self.assertAlmostEqual(r['per100g']['kcal'],100)
    def test_invalid_energy_excluded(self):
        self.assertIsNone(normalize_record({'code':'123','product_name':'Milk','nutriments':{'energy-kcal_100g':-1}},'off'))
    def test_usda_preserves_source_values(self):
        r=normalize_record({'fdcId':1,'description':'Egg, raw','foodNutrients':[{'nutrient':{'id':1008},'amount':143},{'nutrient':{'id':1003},'amount':12.56}]},'usda')
        self.assertEqual(r['id'],'usda:1');self.assertEqual(r['per100g']['protein'],12.56)
        self.assertIsNone(r['density'])

if __name__=='__main__': unittest.main()
