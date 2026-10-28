"""Built-in workout videos: the user's YouTube routines with every exercise and the time it runs on screen.

The timings were read off each video's own countdown circle (one frame per second) and the titles are the ones the
video shows; each segment points at the catalog exercise it is. Only the work is listed: the gaps between segments are
the video's rests and transitions. Both videos come from the channel "재호 - Fitness" (Jaeho), whose owner has turned
embedding off, so the app opens them in YouTube or plays the user's own copy of the file.
Run: python tools/exercises/videos.py  ->  app/src/main/assets/videos/library.json
"""
import json, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXERCISES = ROOT / 'app/src/main/assets/exercises/exercises.json'
OUTPUT = ROOT / 'app/src/main/assets/videos/library.json'

# Section names in both languages.
SECTIONS = {
    'warmup': ('Разминка', 'Warm-up'), 'lower': ('Ноги и ягодицы', 'Lower body'), 'upper': ('Верх тела', 'Upper body'),
    'abs': ('Пресс', 'Abs'), 'cardio': ('Кардио', 'Cardio'),
    'round1': ('Круг 1', 'Round 1'), 'round2': ('Круг 2', 'Round 2'), 'round3': ('Последний круг', 'Final round'),
}
PER_ARM = ('по 15 с каждой рукой', '15 s per arm')
PER_LEG = ('20 с одной ногой, 20 с другой', '20 s on each leg')

# Section, then (start second, length in seconds, title on screen, exercise id[, note]).
THIRTY = [
    ('warmup', [(17, 30, 'Single Arm Circles', 'arm_circles', PER_ARM), (52, 30, 'T-Raise', 'standing_t_raise'), (87, 30, 'Arm Raises', 'arm_raises'),
                (122, 30, 'Air Squat', 'squat'), (157, 30, 'Reverse Lunge', 'reverse_lunge'), (192, 30, 'Side Toe Touch', 'cross_toe_touch'),
                (227, 30, 'Side Lunge', 'side_lunge'), (262, 30, 'Arm Walking', 'inchworm'), (297, 30, 'Jumping Jack', 'jumping_jacks')]),
    ('lower', [(352, 40, 'Squat Knee to Elbow', 'squat_knee_elbow'), (412, 40, 'Reverse Lunge Knee Up', 'reverse_lunge_knee_up', PER_LEG),
               (472, 40, 'Side Lunge', 'side_lunge'), (532, 40, 'Bounce Squat', 'bounce_squat'), (592, 40, 'Side to Side Squat', 'side_step_squat'),
               (652, 40, 'Single Leg Bridge', 'single_leg_bridge', PER_LEG)]),
    ('upper', [(722, 40, 'Push Up & Knee Up', 'pushup_knee_up'), (782, 40, 'Prone Pull Up', 'prone_pullup'), (842, 40, 'Push Up & T-Rotation', 'pushup_t_rotation'),
               (902, 40, 'Reverse Snow Angel', 'snow_angel'), (962, 40, 'High Plank & Pike', 'plank_pike'), (1022, 40, 'Back Extension', 'back_extension')]),
    ('abs', [(1092, 40, 'Crunches', 'crunch'), (1152, 40, 'Leg Press', 'lying_leg_press'), (1212, 40, 'Bicycle Crunch', 'bicycle_crunch'),
             (1272, 40, 'T-Rotation', 'lying_knee_rotation'), (1332, 40, 'Mountain Climber', 'mountain_climber'), (1392, 20, 'Plank & Twist', 'plank_hip_twist'),
             (1412, 20, 'Plank Hold', 'plank')]),
    ('cardio', [(1462, 40, 'Squat & Jack', 'squat_and_jack'), (1522, 40, 'Jog on the Spot', 'jog_in_place'), (1582, 40, 'Slow Burpee', 'half_burpee'),
                (1642, 40, 'A-Skip', 'a_skip'), (1702, 40, 'Heel Touch', 'standing_heel_touch'), (1762, 40, 'Jumping Jack', 'jumping_jacks')]),
]

def rounds(first, names):
    """40 s of work and 20 s of rest per exercise, as in the 25-minute video."""
    return [(first + 60 * k, 40, title, ident) for k, (title, ident) in enumerate(names)]

TWENTY_FIVE = [
    ('round1', rounds(16, [('OH Squat', 'prisoner_squat'), ('High Plank SH Tap', 'plank_shoulder_tap'), ('Knee to Elbow Crunch', 'knee_elbow_crunch'),
                           ('Jumping Jack', 'jumping_jacks'), ('Split Squat', 'split_squat'), ('High Plank & Pike', 'plank_pike'),
                           ('Table Top Crunches', 'tabletop_crunch'), ('Jog on the Spot', 'jog_in_place')])),
    ('round2', rounds(506, [('Squat Knee to Elbow', 'squat_knee_elbow'), ('Back Extension', 'back_extension'), ('Bridge Crunches', 'bridge_crunch'),
                            ('Heel Touch', 'standing_heel_touch'), ('Air Squat', 'squat'), ('Plank T Rotation', 'plank_t_rotation'),
                            ('Plank Toe In & Out', 'plank_toe_in_out'), ('Jump Rope', 'skip_imitation')])),
    ('round3', rounds(996, [('Wide Squat', 'sumo_squat'), ('Plank Pike', 'forearm_plank_pike'), ('T-Raise', 'prone_t_raise'), ('Jog on the Spot', 'jog_in_place'),
                            ('Bounce Squat', 'bounce_squat'), ('Mountain Climber Toe Taps', 'climber_toe_tap'), ('Jump Rope', 'skip_imitation'),
                            ('Heel Touch', 'standing_heel_touch')])),
]

VIDEOS = [
    {'id': 'yt:eoY6qlkp1xA', 'youtubeId': 'eoY6qlkp1xA', 'durationSec': 1812, 'embeddable': False, 'channel': '재호 - Fitness (Jaeho)',
     'title': '30 MIN FULL BODY WORKOUT with WARM UP | No Equipment | Fat Burn', 'about': ('Разминка 5 мин (9 × 30 с), затем ноги, верх тела, пресс и кардио: по 6 упражнений, 40 с работы и 20 с отдыха.',
     'A 5-minute warm-up (9 × 30 s), then lower body, upper body, abs and cardio: 6 exercises each, 40 s of work and 20 s of rest.'), 'sections': THIRTY},
    {'id': 'yt:jFvQl1boyM0', 'youtubeId': 'jFvQl1boyM0', 'durationSec': 1465, 'embeddable': False, 'channel': '재호 - Fitness (Jaeho)',
     'title': '25 MIN FULL BODY WORKOUT AT HOME | Fat Burn', 'about': ('Три круга по 8 упражнений: 40 с работы, 20 с отдыха, 30 с между кругами.',
     'Three rounds of 8 exercises: 40 s of work, 20 s of rest, 30 s between rounds.'), 'sections': TWENTY_FIVE},
]

# The 30-minute video calls its standing warm-up raise "T-Raise", the title the 25-minute video uses for the prone one.
SHARED_TITLES = {('T-Raise', 'standing_t_raise')}

def build(exercises=EXERCISES, output=OUTPUT):
    catalog = {e['id']: e for e in json.loads(Path(exercises).read_text(encoding='utf-8'))}
    out = []
    for v in VIDEOS:
        segments = []
        for key, items in v['sections']:
            name, name_en = SECTIONS[key]
            for item in items:
                start, length, title, ident = item[:4]
                note, note_en = item[4] if len(item) > 4 else ('', '')
                assert ident in catalog, ident
                assert title in catalog[ident]['videoNames'] or (title, ident) in SHARED_TITLES, (title, ident)
                segments.append({'start': start, 'end': start + length, 'exerciseId': ident, 'title': title, 'section': name, 'sectionEn': name_en, 'note': note, 'noteEn': note_en})
        for a, b in zip(segments, segments[1:]): assert a['end'] <= b['start'], (v['id'], a, b)
        assert segments[0]['start'] >= 0 and segments[-1]['end'] <= v['durationSec'], v['id']
        about, about_en = v['about']
        out.append({'id': v['id'], 'title': v['title'], 'channel': v['channel'], 'youtubeId': v['youtubeId'], 'durationSec': v['durationSec'],
                    'embeddable': v['embeddable'], 'about': about, 'aboutEn': about_en, 'segments': segments})
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    Path(output).write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding='utf-8')
    return {v['id']: len(v['segments']) for v in out}

if __name__ == '__main__':
    print(build(*sys.argv[1:]))
