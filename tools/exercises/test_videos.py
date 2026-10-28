import json, tempfile, unittest
from pathlib import Path
import videos as v

def v_ex(video,title):
    return next(s['exerciseId'] for s in video['segments'] if s['title']==title)

class VideoLibraryTest(unittest.TestCase):
    def setUp(self):
        self.out=Path(tempfile.mkdtemp())/'library.json';v.build(v.EXERCISES,self.out)
        self.items={i['id']:i for i in json.loads(self.out.read_text(encoding='utf-8'))}
    def test_bundled_file_is_up_to_date(self):
        self.assertEqual(json.loads(v.OUTPUT.read_text(encoding='utf-8')),list(self.items.values()))
    def test_thirty_minute_video_has_every_exercise_on_screen(self):
        s=self.items['yt:eoY6qlkp1xA']['segments']
        # 9 warm-ups of 30 s; lower, upper, abs and cardio of 40 s each, the last ab slot split into plank and twist and a plank hold.
        self.assertEqual(34,len(s))
        self.assertEqual([30]*9,[x['end']-x['start'] for x in s[:9]])
        self.assertEqual(9*30+23*40+2*20,sum(x['end']-x['start'] for x in s))
        self.assertEqual(('Single Arm Circles','arm_circles',17),(s[0]['title'],s[0]['exerciseId'],s[0]['start']))
        self.assertEqual(('Jumping Jack','jumping_jacks',1762,1802),(s[-1]['title'],s[-1]['exerciseId'],s[-1]['start'],s[-1]['end']))
        self.assertEqual(['Разминка','Ноги и ягодицы','Верх тела','Пресс','Кардио'],list(dict.fromkeys(x['section'] for x in s)))
    def test_twenty_five_minute_video_is_three_rounds_of_eight(self):
        s=self.items['yt:jFvQl1boyM0']['segments']
        self.assertEqual(24,len(s));self.assertTrue(all(x['end']-x['start']==40 for x in s))
        self.assertEqual([16,506,996],[s[0]['start'],s[8]['start'],s[16]['start']]);self.assertEqual(1456,s[-1]['end'])
    def test_channel_videos_are_timed_by_the_authors_chapters(self):
        more=[v for v in self.items.values() if v.get('timing')=='chapters']
        self.assertEqual(141,len(more));self.assertEqual(1922,sum(len(v['segments']) for v in self.items.values()))
        # A chapter holds the exercise and the rest next to it, so the work is shorter; rest chapters are gaps.
        for v in more:
            for s in v['segments']:
                work=s.get('work',s['end']-s['start']);self.assertTrue(0<work<=s['end']-s['start'],(v['id'],s))
                self.assertNotRegex(s['title'].lower(),r'^(rest|intro|end)$')
        first=self.items['yt:FLUFKTrdf9s']['segments'][:2]
        self.assertEqual([('MCGILL CRUNCH','mcgill_crunch',0,49,39),('LEG RAISE','leg_raise',49,94,35)],[(s['title'],s['exerciseId'],s['start'],s['end'],s['work']) for s in first])
        # Gym videos stay out; ab and cardio videos are tagged for the filter; the two that allow embedding keep the flag.
        self.assertNotIn('yt:_nL3y09d4tM',self.items)
        self.assertIn('abs',self.items['yt:FLUFKTrdf9s']['tags'])
        self.assertEqual({'yt:AqfSa-r_qQg','yt:cuWtIAJ4Sj0'},{i for i,v in self.items.items() if v['embeddable']})
        # "Heel touch" is lying in an ab video and standing in a cardio one.
        self.assertEqual('heel_touch',v_ex(self.items['yt:FLUFKTrdf9s'],'HEEL TOUCH'))
    def test_chapter_names_fold_typos_and_prefixes(self):
        import channel as c
        for raw,want in (('MONUTAIN CLIMBER','mountain climber'),('single leg daedlift','single leg deadlift'),('20 reps - Side Plank','side plank'),
                         ('core - pank reach','plank reach'),('3 set narrow push up','narrow push up'),('SIDE PALNK UP & DOWN (R)','side plank up & down'),('LEG RAISE','leg raise')):
            self.assertEqual(want,c.canon(raw),raw)
        self.assertEqual('Six Pack Abs Workout 7 Min Routine (Burn Abdominal Fat)',c.clean_title('식스팩 복근 운동 7분 루틴 (복부 지방 태우기) Six Pack Abs Workout 7 Min Routine (Burn Abdominal Fat)'))
    def test_segments_point_at_catalog_exercises_in_order(self):
        catalog={e['id'] for e in json.loads(v.EXERCISES.read_text(encoding='utf-8'))}
        for video in self.items.values():
            self.assertTrue(all(x['exerciseId'] in catalog for x in video['segments']))
            self.assertTrue(all(a['end']<=b['start'] for a,b in zip(video['segments'],video['segments'][1:])))
            self.assertLessEqual(video['segments'][-1]['end'],video['durationSec'])

if __name__=='__main__':unittest.main()
