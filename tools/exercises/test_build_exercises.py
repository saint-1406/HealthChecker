import json, tempfile, unittest
from pathlib import Path
import build_exercises as b

class ExerciseCatalogTest(unittest.TestCase):
    def setUp(self):
        self.out=Path(tempfile.mkdtemp())/'exercises.json';b.build(self.out)
        self.items=json.loads(self.out.read_text(encoding='utf-8'))
    def test_every_met_comes_from_its_compendium_code(self):
        for i in self.items:self.assertEqual(b.COMPENDIUM[i['metCode']][0],i['met'],i['id'])
    def test_home_coverage_for_every_muscle_group_with_and_without_dumbbells(self):
        for group in ('LEGS','GLUTES','CHEST','BACK','SHOULDERS','ARMS','CORE'):
            strength=[i for i in self.items if i['kind']=='STRENGTH' and i['muscles'][0]==group]
            self.assertTrue(any(i['equipment']=='NONE' and i['level']==1 for i in strength) or group=='ARMS',group)
            self.assertTrue(any(i['equipment']=='DUMBBELLS' for i in strength),group)
    def test_beginner_cardio_and_mobility_exist(self):
        for kind in ('CARDIO','MOBILITY'):self.assertTrue(any(i['kind']==kind and i['level']==1 and i['equipment']=='NONE' for i in self.items))
    def test_repetition_exercises_have_tempo(self):
        for i in self.items:self.assertEqual(i['measure']=='REPS',i['secondsPerRep'] is not None,i['id'])

if __name__=='__main__':unittest.main()
