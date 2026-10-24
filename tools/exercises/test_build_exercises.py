import json, tempfile, unittest
from pathlib import Path
import build_exercises as b
import additions

class ExerciseCatalogTest(unittest.TestCase):
    def setUp(self):
        self.out=Path(tempfile.mkdtemp())/'exercises.json';b.build(self.out)
        self.items=json.loads(self.out.read_text(encoding='utf-8'))
    def test_every_met_comes_from_its_compendium_code(self):
        codes={**b.COMPENDIUM,**additions.COMPENDIUM_EXTRA}
        for i in self.items:self.assertEqual(codes[i['metCode']][0],i['met'],i['id'])
    def test_home_coverage_for_every_muscle_group_with_and_without_dumbbells(self):
        for group in ('LEGS','GLUTES','CHEST','BACK','SHOULDERS','ARMS','CORE'):
            strength=[i for i in self.items if i['kind']=='STRENGTH' and i['muscles'][0]==group]
            self.assertTrue(any(i['equipment']=='NONE' and i['level']==1 for i in strength) or group=='ARMS',group)
            self.assertTrue(any(i['equipment']=='DUMBBELLS' for i in strength),group)
    def test_beginner_cardio_and_mobility_exist(self):
        for kind in ('CARDIO','MOBILITY'):self.assertTrue(any(i['kind']==kind and i['level']==1 and i['equipment']=='NONE' for i in self.items))
    def test_every_exercise_has_english_text(self):
        for i in self.items:self.assertTrue(i['nameEn'] and i['cueEn'],i['id'])
    def test_repetition_exercises_have_tempo(self):
        for i in self.items:self.assertEqual(i['measure']=='REPS',i['secondsPerRep'] is not None,i['id'])

    def test_exercises_asked_for_after_v7_exist_with_their_titles(self):
        by={i['id']:i for i in self.items}
        titles={'oblique_crunch':'Oblique Crunch','inchworm':'Arm Walking','standing_cross_crunch':'Cross Knee to Elbow','cross_toe_touch':'Cross Toe Touches',
                'front_toe_touch':'Front Toe Touch','squat_negative':'Negative Squat','prone_pullup':'Prone Pull-up','high_plank_toe_in_out':'High Plank Toe In & Out'}
        for i,t in titles.items():self.assertIn(t,by[i]['videoNames'],i)
        # Warm-ups are timed mobility moves, so the recommender can pick them for the start of a session.
        for i in ('cross_toe_touch','front_toe_touch'):self.assertEqual(('WARMUP','MOBILITY','SECONDS'),(by[i]['category'],by[i]['kind'],by[i]['measure']),i)
        self.assertEqual(202,len(self.items))

    def test_user_video_routine_is_findable_by_its_titles(self):
        titles=['OH Squat','High Plank SH Tap','Knee to Elbow Crunch','Jumping Jack','Split Squat','High Plank & Pike','Table Top Crunches',
                'Jog on the Spot','Squat Knee to Elbow','Back Extension','Bridge Crunches','Heel Touch','Air Squat','Plank T Rotation',
                'Plank Toe In & Out','Jump Rope','Wide Squat','Plank Pike','T-Raise','Bounce Squat','Mountain Climber Toe Taps']
        named={t for i in self.items for t in i['videoNames']}
        self.assertEqual([],[t for t in titles if t not in named])
    def test_every_direction_has_beginner_home_exercises(self):
        for cat in ('WARMUP','STRENGTH','AEROBIC','ENDURANCE','HIIT','STRETCH','YOGA','BALANCE'):
            self.assertTrue(any(i['category']==cat and i['equipment']=='NONE' for i in self.items),cat)
        self.assertGreaterEqual(sum(i['category']=='WARMUP' for i in self.items),12)
    def test_names_are_unique_in_each_language(self):
        for key in ('name','nameEn'):
            names=[i[key].lower() for i in self.items];self.assertEqual(len(names),len(set(names)),key)

if __name__=='__main__':unittest.main()
