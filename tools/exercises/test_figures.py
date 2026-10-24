import json, tempfile, unittest
from pathlib import Path
import figures as f

ASSETS=Path(__file__).resolve().parents[2]/'app/src/main/assets/exercises'

class FiguresTest(unittest.TestCase):
    def setUp(self):
        self.dir=Path(tempfile.mkdtemp());(self.dir/'exercises.json').write_text((ASSETS/'exercises.json').read_text(encoding='utf-8'),encoding='utf-8')
        f.build(self.dir/'figures.json');self.items=json.loads((self.dir/'figures.json').read_text(encoding='utf-8'))
    def test_every_exercise_has_one_motion_in_catalog_order(self):
        ids=[e['id'] for e in json.loads((ASSETS/'exercises.json').read_text(encoding='utf-8'))]
        self.assertEqual(ids,[m['id'] for m in self.items])
    def test_holds_are_single_poses_and_moves_have_a_few(self):
        for m in self.items:
            self.assertTrue(1<=len(m['frames'])<=6,m['id']);self.assertEqual(m['hold'],len(m['frames'])==1,m['id'])
    def test_every_pose_stands_on_the_ground_unless_lifted(self):
        for m in self.items:
            for p in m['frames']:
                g=f.grounded(p);low=max([y for k,(x,y) in g.items() if k!='head']+[g['head'][1]+f.LEN['head']])
                self.assertAlmostEqual(-p['lift'],low,places=6,msg=m['id'])
    def test_bundled_file_is_up_to_date(self):
        self.assertEqual(json.loads((ASSETS/'figures.json').read_text(encoding='utf-8')),self.items)

if __name__=='__main__':unittest.main()
