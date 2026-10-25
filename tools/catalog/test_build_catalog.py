import unittest
from build_catalog import normalize_record, extra_food

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
    def test_fiber_kept_and_zero_only_without_carbs(self):
        def usda(carbs,fiber):
            n=[{'nutrient':{'id':1008},'amount':100},{'nutrient':{'id':1005},'amount':carbs}]+([{'nutrient':{'id':1079},'amount':fiber}] if fiber is not None else [])
            return normalize_record({'fdcId':1,'description':'X','foodNutrients':n},'usda')['per100g']['fiber']
        self.assertEqual(usda(20,2.5),2.5)
        self.assertEqual(usda(0,None),0.0)
        self.assertIsNone(usda(20,None))
    def test_extra_food_from_ingredients(self):
        foods={'usda:1':{'per100g':{'carbs':10.0,'fiber':2.0}},'usda:2':{'per100g':{'carbs':0.0,'fiber':0.0}}}
        f=extra_food({'id':'local:x','name':'Х','nameEn':'X','source':'s','basis':{'grams':200,'kcal':400,'protein':20,'fat':10,'carbs':{'ingredients':[['usda:1',100],['usda:2',100]]}}},foods)
        self.assertEqual(f['per100g'],{'kcal':200.0,'protein':10.0,'fat':5.0,'carbs':5.0,'fiber':1.0})
        self.assertTrue(f['common'])
    def test_extra_food_atwater_remainder(self):
        foods={'usda:flour':{'per100g':{'carbs':76.3,'fiber':2.7}}}
        f=extra_food({'id':'local:p','name':'П','nameEn':'P','source':'s','basis':{'grams':100,'kcal':280,'protein':18,'fat':16,'carbs':{'atwater':True,'fiberRatioOf':'usda:flour'}}},foods)
        # 280 − 4·18 − 9·16 = 64 kcal → 16 g available carbohydrate, plus fibre in flour's proportion.
        self.assertAlmostEqual(f['per100g']['fiber'],16*2.7/73.6,places=2)
        self.assertAlmostEqual(f['per100g']['carbs'],16+16*2.7/73.6,places=2)
    def test_extra_food_from_a_full_ingredient_model(self):
        foods={'usda:rice':{'per100g':{'kcal':130.0,'protein':2.0,'fat':0.2,'carbs':28.0,'fiber':0.3}},'usda:fish':{'per100g':{'kcal':200.0,'protein':20.0,'fat':13.0,'carbs':0.0,'fiber':0.0}}}
        f=extra_food({'id':'local:n','name':'Н','nameEn':'N','source':'s','basis':{'ingredients':[['usda:rice',20],['usda:fish',10]]}},foods)
        # 30 g piece: 26 + 20 kcal, every nutrient summed and scaled to 100 g.
        self.assertAlmostEqual(f['per100g']['kcal'],(26+20)/0.3,places=1);self.assertAlmostEqual(f['per100g']['fiber'],0.06/0.3,places=2)
        g=extra_food({'id':'local:g','name':'Г','nameEn':'G','source':'s','basis':{'ingredients':[['usda:rice',20],['usda:fish',10]],'grams':30,'kcal':57.5,'protein':2.5,'carbs':6,'fat':'remainder','fiber':'ingredients'}},foods)
        self.assertAlmostEqual(g['per100g']['kcal'],57.5/0.3,places=1);self.assertAlmostEqual(g['per100g']['fat'],(57.5-10-24)/9/0.3,places=1)
        with self.assertRaises(ValueError):
            extra_food({'id':'local:x','name':'Х','nameEn':'X','source':'s','basis':{'ingredients':[['usda:rice',20]],'kcal':10,'protein':5,'carbs':5,'fat':'remainder'}},foods)
    def test_extra_food_rejects_impossible_energy(self):
        with self.assertRaises(ValueError):
            extra_food({'id':'local:z','name':'З','nameEn':'Z','source':'s','basis':{'grams':100,'kcal':100,'protein':20,'fat':10,'carbs':{'atwater':True,'fiber':0}}},{})

if __name__=='__main__': unittest.main()
