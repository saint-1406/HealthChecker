import json, tempfile, unittest
from pathlib import Path
import videos as v

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
    def test_segments_point_at_catalog_exercises_in_order(self):
        catalog={e['id'] for e in json.loads(v.EXERCISES.read_text(encoding='utf-8'))}
        for video in self.items.values():
            self.assertTrue(all(x['exerciseId'] in catalog for x in video['segments']))
            self.assertTrue(all(a['end']<=b['start'] for a,b in zip(video['segments'],video['segments'][1:])))
            self.assertLessEqual(video['segments'][-1]['end'],video['durationSec'])

if __name__=='__main__':unittest.main()
