import json, unittest
from pathlib import Path
import mady as m

HERE = Path(__file__).parent
CATALOG = {e['id']: e for e in json.loads((HERE.parents[1] / 'app/src/main/assets/exercises/exercises.json').read_text(encoding='utf-8'))}

class MadyMorrisonTest(unittest.TestCase):
    def setUp(self):
        self.snapshot = json.loads(m.SNAPSHOT.read_text(encoding='utf-8'))
        self.videos, self.whole = m.videos(CATALOG)
        self.by = {v['youtubeId']: v for v in self.videos}

    def test_every_workout_video_of_the_snapshot_is_listed_once(self):
        self.assertEqual([v['id'] for v in self.snapshot], [v['youtubeId'] for v in self.videos])
        self.assertTrue(all(not v['embeddable'] and v['channel'] == 'Mady Morrison' for v in self.videos))

    def test_every_author_chapter_is_named(self):
        # No video with the author's chapters falls back to a whole session because of a chapter we cannot name.
        self.assertEqual({}, {i: why for i, why in self.whole.items() if why.startswith('unknown')})

    def test_chapters_become_moves_with_sides(self):
        s = self.by['4snu7NxD4nM']['segments']
        self.assertEqual('chapters', self.by['4snu7NxD4nM']['timing'])
        self.assertEqual((76, 116, 'side_stretch', 'right side'), (s[0]['start'], s[0]['end'], s[0]['exerciseId'], s[0]['noteEn']))
        self.assertEqual('left side', s[1]['noteEn'])
        # The intro is a gap; the last chapter runs on to the end of the video with the outro, so only its usual 40 s count.
        self.assertEqual((761, 801, 'the chapter runs on with unnamed moves'), (s[-1]['start'], s[-1]['end'], s[-1]['noteEn']))
        for v in self.videos:
            self.assertTrue(all(a['end'] <= b['start'] for a, b in zip(v['segments'], v['segments'][1:])), v['id'])
            self.assertTrue(all(x['exerciseId'] in CATALOG for x in v['segments']), v['id'])

    def test_videos_without_chapters_are_one_whole_session(self):
        for v in self.videos:
            if v['timing'] != 'whole': continue
            self.assertEqual(1, len(v['segments']), v['id'])
            self.assertEqual((0, v['durationSec']), (v['segments'][0]['start'], v['segments'][0]['end']))
            self.assertIn(CATALOG[v['segments'][0]['exerciseId']]['measure'], ('MINUTES',), v['id'])
        self.assertEqual('pilates', self.by['VW4d7Lqoz8Q']['segments'][0]['exerciseId'])

    def test_chapter_names_fold_counters_and_sides(self):
        for raw, want in (('next exercise: LOW LUNGE QUAD STRETCH R', 'low lunge quad stretch'), ('first exercise: CHILDS POSE', 'childs pose'),
                          ('DONKEY KICKR', 'donkey kick'), ('SEATED TWIST L', 'seated twist'), ('Katze Kuh', 'katze kuh')):
            self.assertEqual(want, m.canon(raw), raw)
        self.assertEqual(('правая сторона', 'right side'), m.side('LUNGE & KICKR'))
        self.assertEqual(('', ''), m.side('CAT COW'))

    def test_progress_file_matches_the_library(self):
        p = json.loads((HERE / 'madymorrison_progress.json').read_text(encoding='utf-8'))
        self.assertEqual(len(self.snapshot), p['listed'])
        self.assertEqual(sum(v['timing'] == 'chapters' for v in self.videos), p['timedByChapters'])
        self.assertEqual(sorted(self.whole), sorted(p['wholeSession']))

if __name__ == '__main__': unittest.main()
