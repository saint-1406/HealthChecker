"""Original motion schemes for every exercise: a stick figure drawn from a few key poses that the app animates.

The drawings are made here from joint angles, so they carry no third-party copyright. A pose gives absolute segment
directions in degrees for a figure facing right: 0 up, 90 forward, 180 down, 270 back. Side view draws the near limbs
(n) dark and the far limbs (f) light; front view (front=True) puts n on the screen's left and f on its right.
Run: python tools/exercises/figures.py  ->  app/src/main/assets/exercises/figures.json and a review sheet.
"""
import json, math, sys
from pathlib import Path

# Segment lengths in body units (standing height 1.63, about 1.9 with the arms up); the app uses the same numbers.
LEN = {'torso': 0.50, 'neck': 0.06, 'head': 0.11, 'upper': 0.30, 'fore': 0.27, 'thigh': 0.43, 'shin': 0.42, 'foot': 0.11}
SHOULDER_HALF, HIP_HALF = 0.12, 0.07

def P(t, an=(180, 180), af=(180, 180), ln=(180, 180), lf=(180, 180), h=None, lift=0.0, front=False, dx=0.0):
    """lift raises the grounded figure (jumps, standing on a step); dx moves it forward (stepping onto a box)."""
    return {'t': t, 'h': t if h is None else h, 'an': list(an), 'af': list(af), 'ln': list(ln), 'lf': list(lf), 'lift': lift, 'front': front, 'dx': dx}

def swap(p):
    """Mirror image of a pose: the near and far limbs change places (alternating sides)."""
    q = dict(p); q['an'], q['af'], q['ln'], q['lf'] = p['af'], p['an'], p['lf'], p['ln']; return q

def mirror(p):
    """Front view seen from the other side: left and right swap and angles reflect."""
    r = lambda pair: [(360 - a) % 360 for a in pair]
    q = dict(p); q['t'] = (360 - p['t']) % 360; q['h'] = (360 - p['h']) % 360
    q['an'], q['af'], q['ln'], q['lf'] = r(p['af']), r(p['an']), r(p['lf']), r(p['ln']); return q

def with_(p, **k):
    q = dict(p)
    for key, v in k.items(): q[key] = list(v) if isinstance(v, tuple) else v
    return q

# ---- Base poses -------------------------------------------------------------------------------------------------
STAND = P(0)
STAND_FWD = P(0, (90, 90), (90, 90))
STAND_UP = P(0, (20, 15), (10, 5))
HANDS_HEAD = ((300, 60), (300, 60))
STAND_HEAD = P(0, *HANDS_HEAD)
SQUAT = P(35, (85, 85), (85, 85), (95, 200), (95, 200))
SQUAT_HANDS_DOWN = P(30, (165, 170), (165, 170), (80, 205), (80, 205))
HALF_SQUAT = P(20, (90, 90), (90, 90), (130, 200), (130, 200))
LUNGE_UP = P(0, ln=(165, 180), lf=(200, 205))
LUNGE_DOWN = P(0, ln=(100, 180), lf=(200, 270))
HINGE = P(80, (180, 180), (180, 180), (170, 185), (170, 185))
PLANK = P(70, (180, 180), (180, 180), (248, 248), (248, 248))
PUSH_DOWN = P(85, (230, 150), (230, 150), (252, 252), (252, 252))
FOREARM_PLANK = P(85, (180, 90), (180, 90), (255, 255), (255, 255))
PIKE = P(135, (135, 135), (135, 135), (225, 225), (225, 225))
QUAD = P(90, (180, 180), (180, 180), (180, 270), (180, 270))
SUPINE_KNEES = P(270, (90, 90), (90, 90), (45, 135), (45, 135))
SUPINE_FLAT = P(270, (90, 90), (90, 90), (90, 90), (90, 90))
PRONE = P(270, (270, 270), (270, 270), (90, 90), (90, 90))
PRONE_UP = P(285, (290, 290), (290, 290), (80, 80), (80, 80))
SEATED = P(0, (90, 90), (90, 90), (90, 90), (90, 90))
BRIDGE_UP = P(250, (80, 80), (80, 80), (80, 170), (80, 170))
WALK = P(0, (160, 175), (200, 200), (160, 180), (200, 200))
RUN = P(8, (140, 50), (220, 160), (115, 200), (195, 220), lift=0.03)
F_STAND = P(0, (180, 180), (180, 180), (180, 180), (180, 180), front=True)
F_JACK_OUT = P(0, (330, 330), (30, 30), (200, 200), (160, 160), lift=0.06, front=True)
F_ARMS_OUT = P(0, (270, 270), (90, 90), (180, 180), (180, 180), front=True)
F_WIDE = P(0, (180, 180), (180, 180), (205, 205), (155, 155), front=True)
F_HALF_SQUAT = P(0, (200, 160), (160, 200), (215, 165), (145, 195), front=True)
F_SIDE_LUNGE = P(0, (180, 180), (180, 180), (240, 175), (138, 138), front=True)
F_PRESS_LOW = P(0, (270, 0), (90, 0), (180, 180), (180, 180), front=True)
F_PRESS_UP = P(0, (350, 350), (10, 10), (180, 180), (180, 180), front=True)

def walking(p=WALK):
    return [p, swap(p)]

# Props: box (x from, x to relative to the hip, height) and wall (x relative to the hip). Heights in body units.
def box(x0, x1, h): return {'type': 'box', 'x0': x0, 'x1': x1, 'h': h}
def wall(x): return {'type': 'wall', 'x': x}

M = {}
def add(ids, frames, props=(), hold=False):
    for i in ids.split():
        M[i] = {'frames': frames, 'props': list(props), 'hold': hold}

# ---- Legs and glutes ---------------------------------------------------------------------------------------------
add('squat bounce_squat db_squat', [STAND_FWD, SQUAT])
# Two steps down and one up: the slow lowering is the point.
add('squat_negative', [STAND_FWD, HALF_SQUAT, SQUAT])
add('prisoner_squat', [STAND_HEAD, P(30, (330, 70), (330, 70), (95, 200), (95, 200))])
add('sumo_squat db_sumo_squat', [P(0, (180, 180), (180, 180), (200, 180), (160, 180), front=True), P(0, (190, 170), (170, 190), (235, 170), (125, 190), front=True)])
add('sit_to_stand', [P(15, (90, 90), (90, 90), (90, 180), (90, 180)), STAND_FWD], [box(-0.42, 0.06, 0.40)])
add('wall_sit', [P(0, ln=(90, 180), lf=(90, 180))], [wall(-0.09)], hold=True)
add('forward_lunge reverse_lunge split_squat walking_lunge db_lunge db_reverse_lunge', [LUNGE_UP, LUNGE_DOWN])
add('side_lunge', [F_WIDE, F_SIDE_LUNGE, F_WIDE, mirror(F_SIDE_LUNGE)])
add('curtsy_lunge', [LUNGE_UP, with_(LUNGE_DOWN, lf=(195, 255))])
add('step_up db_step_up step_up_rhythm stairs stairs_fast stairs_up_down step_aerobics', [P(5, ln=(115, 180), lf=(180, 180)), P(0, ln=(180, 180), lf=(95, 180), lift=0.25, dx=0.42)], [box(0.22, 0.7, 0.25)])
add('bulgarian_split_squat db_bulgarian', [P(0, ln=(160, 180), lf=(210, 300)), P(10, ln=(100, 180), lf=(200, 300))], [box(-0.9, -0.5, 0.42)])
add('pistol_assisted', [P(0, (90, 90), (90, 90), (180, 180), (100, 100)), P(30, (85, 85), (85, 85), (95, 200), (85, 85))])
add('glute_bridge db_glute_bridge', [SUPINE_KNEES, BRIDGE_UP])
add('single_leg_bridge', [with_(SUPINE_KNEES, lf=(45, 45)), with_(BRIDGE_UP, lf=(70, 70))])
add('hip_thrust_sofa', [P(270, (90, 90), (90, 90), (40, 150), (40, 150)), P(275, (90, 90), (90, 90), (75, 175), (75, 175))], [box(-0.95, -0.45, 0.32)])
add('donkey_kick', [QUAD, with_(QUAD, lf=(270, 0))])
add('fire_hydrant', [QUAD, with_(QUAD, lf=(235, 300))])
add('side_leg_raise', [SUPINE_FLAT, with_(SUPINE_FLAT, lf=(55, 55))])
add('calf_raise db_calf_raise', [STAND, with_(STAND, lift=0.07)])
add('single_calf_raise', [P(0, lf=(180, 270)), P(0, lf=(180, 270), lift=0.07)])
add('good_morning', [STAND_HEAD, P(80, (20, 140), (20, 140), (172, 185), (172, 185))])
add('single_leg_rdl', [P(0, lf=(190, 190)), P(85, (180, 180), (180, 180), (175, 185), (265, 265))])
add('db_rdl', [STAND, HINGE])
add('db_deadlift', [P(50, (180, 180), (180, 180), (120, 205), (120, 205)), STAND])
add('jump_squat', [P(35, (220, 220), (220, 220), (95, 200), (95, 200)), with_(STAND_UP, lift=0.22)])
add('jump_lunge', [LUNGE_DOWN, with_(P(0, ln=(150, 190), lf=(190, 210)), lift=0.2), swap(LUNGE_DOWN)])

# ---- Push: chest, shoulders, arms ----------------------------------------------------------------------------------
add('pushup wide_pushup diamond_pushup', [PLANK, PUSH_DOWN])
add('knee_pushup', [P(60, (180, 180), (180, 180), (235, 320), (235, 320)), P(78, (230, 150), (230, 150), (245, 320), (245, 320))])
add('wall_pushup', [P(25, (65, 65), (65, 65), (195, 195), (195, 195)), P(40, (160, 75), (160, 75), (198, 198), (198, 198))], [wall(0.74)])
add('incline_pushup', [P(55, (175, 175), (175, 175), (235, 235), (235, 235)), P(68, (225, 150), (225, 150), (240, 240), (240, 240))], [box(0.45, 0.85, 0.42)])
add('decline_pushup', [P(85, (180, 180), (180, 180), (265, 265), (265, 265)), P(98, (230, 150), (230, 150), (268, 268), (268, 268))], [box(-1.05, -0.65, 0.3)])
add('pike_pushup incline_pike_pushup', [PIKE, P(150, (195, 95), (195, 95), (225, 225), (225, 225))])
add('hindu_pushup', [PIKE, PUSH_DOWN, P(60, (170, 170), (170, 170), (262, 262), (262, 262))])
add('chair_dip', [P(0, (200, 200), (200, 200), (95, 160), (95, 160)), P(5, (250, 175), (250, 175), (70, 170), (70, 170))], [box(-0.5, -0.1, 0.4)])
add('plank_updown', [FOREARM_PLANK, PLANK])
add('plank_shoulder_tap', [PLANK, with_(PLANK, an=(160, 300))])
add('wall_slide', [P(0, (240, 0), (120, 0), front=True), P(0, (330, 330), (30, 30), front=True)])
add('bear_crawl', [P(90, (165, 180), (195, 180), (150, 255), (175, 255)), P(90, (195, 180), (165, 180), (175, 255), (150, 255))])

# ---- Back and posture --------------------------------------------------------------------------------------------
add('superman back_extension', [PRONE, PRONE_UP])
# Arms reach past the head, then the elbows pull down beside the ribs as in a pull-up.
add('prone_pullup', [with_(PRONE_UP, an=(282, 282), af=(282, 282)), with_(PRONE_UP, an=(125, 300), af=(125, 300))])
add('ytw_raise prone_t_raise', [with_(PRONE, an=(200, 200), af=(200, 200)), with_(PRONE_UP, an=(300, 300), af=(300, 300))])
add('snow_angel', [with_(PRONE_UP, an=(95, 95), af=(95, 95)), with_(PRONE_UP, an=(190, 190), af=(190, 190)), with_(PRONE_UP, an=(285, 285), af=(285, 285))])
add('swimmer', [with_(PRONE_UP, an=(300, 300), af=(250, 250), ln=(90, 90), lf=(75, 75)), with_(PRONE_UP, an=(250, 250), af=(300, 300), ln=(75, 75), lf=(90, 90))])
add('cobra_hold', [P(295, (110, 110), (110, 110), (90, 90), (90, 90))], hold=True)
add('doorframe_row', [P(340, (60, 60), (60, 60), (170, 170), (170, 170)), P(355, (130, 30), (130, 30), (170, 170), (170, 170))], [wall(0.62)])
add('bird_dog', [QUAD, with_(QUAD, an=(90, 90), lf=(270, 270))])

# ---- Core ---------------------------------------------------------------------------------------------------------
CRUNCH_DOWN = P(270, (330, 250), (330, 250), (45, 135), (45, 135))
CRUNCH_UP = P(300, (0, 280), (0, 280), (45, 135), (45, 135))
add('crunch', [CRUNCH_DOWN, CRUNCH_UP])
add('reverse_crunch', [SUPINE_KNEES, P(270, (90, 90), (90, 90), (330, 60), (330, 60))])
add('heel_touch', [with_(CRUNCH_UP, an=(100, 100), af=(85, 85)), with_(CRUNCH_UP, an=(85, 85), af=(100, 100))])
add('dead_bug', [P(270, (0, 0), (0, 0), (0, 90), (0, 90)), P(270, (280, 280), (0, 0), (0, 90), (75, 75))])
add('plank', [FOREARM_PLANK], hold=True)
add('side_plank', [P(68, (180, 90), (0, 0), (248, 248), (248, 248))], hold=True)
add('side_plank_dip', [P(68, (180, 90), (0, 0), (248, 248), (248, 248)), P(78, (180, 90), (0, 0), (258, 258), (258, 258))])
add('hollow_hold', [P(290, (290, 290), (290, 290), (75, 75), (75, 75))], hold=True)
add('leg_raise', [SUPINE_FLAT, with_(SUPINE_FLAT, ln=(5, 5), lf=(5, 5))])
add('flutter_kick', [P(280, (90, 90), (90, 90), (72, 72), (86, 86)), P(280, (90, 90), (90, 90), (86, 86), (72, 72))])
add('bicycle_crunch knee_elbow_crunch', [P(295, (350, 250), (350, 250), (20, 110), (75, 75)), P(295, (350, 250), (350, 250), (75, 75), (20, 110))])
add('situp', [CRUNCH_DOWN, P(20, (40, 150), (40, 150), (45, 135), (45, 135))])
add('russian_twist db_russian_twist', [P(330, (55, 70), (40, 60), (40, 110), (40, 110)), P(330, (40, 60), (55, 70), (40, 110), (40, 110))])
add('v_up', [P(270, (270, 270), (270, 270), (90, 90), (90, 90)), P(320, (40, 40), (40, 40), (40, 40), (40, 40))])
add('tabletop_crunch', [P(270, (330, 250), (330, 250), (0, 90), (0, 90)), P(300, (0, 280), (0, 280), (0, 90), (0, 90))])
add('bridge_crunch', [SUPINE_KNEES, BRIDGE_UP, SUPINE_KNEES, CRUNCH_UP])
add('plank_pike', [PLANK, PIKE])
add('forearm_plank_pike', [FOREARM_PLANK, P(125, (180, 90), (180, 90), (232, 232), (232, 232))])
add('plank_t_rotation', [PLANK, P(70, (180, 180), (0, 0), (248, 248), (248, 248))])
add('plank_toe_in_out', [FOREARM_PLANK, with_(FOREARM_PLANK, lf=(245, 245))])
add('high_plank_toe_in_out', [PLANK, with_(PLANK, lf=(238, 238))])
# Knees dropped to one side, so the thighs lie lower than in a plain crunch.
add('oblique_crunch', [with_(CRUNCH_DOWN, ln=(70, 150), lf=(62, 142)), with_(CRUNCH_UP, t=308, ln=(70, 150), lf=(62, 142))])
add('pilates_hundred', [P(300, (80, 80), (80, 80), (60, 60), (60, 60)), P(300, (95, 95), (95, 95), (60, 60), (60, 60))])
add('pilates_roll_up', [P(270, (270, 270), (270, 270), (90, 90), (90, 90)), P(10, (60, 60), (60, 60), (90, 90), (90, 90)), P(60, (80, 80), (80, 80), (90, 90), (90, 90))])

# ---- Cardio --------------------------------------------------------------------------------------------------------
add('jumping_jacks', [F_STAND, F_JACK_OUT])
add('step_jacks', [F_STAND, P(0, (330, 330), (30, 30), (200, 200), (180, 180), front=True), F_STAND, P(0, (330, 330), (30, 30), (180, 180), (160, 160), front=True)])
add('cross_jacks', [P(0, (300, 300), (60, 60), (205, 205), (155, 155), lift=0.05, front=True), P(0, (150, 150), (210, 210), (165, 165), (195, 195), front=True)])
add('seal_jacks', [P(0, (270, 270), (90, 90), (205, 205), (155, 155), lift=0.05, front=True), P(0, (190, 150), (170, 210), (180, 180), (180, 180), front=True)])
add('squat_jacks', [P(0, (200, 120), (160, 240), (195, 165), (165, 195), front=True), P(0, (200, 120), (160, 240), (225, 165), (135, 195), lift=0.04, front=True)])
add('jog_in_place march_in_place jogging_outdoor sprint_in_place', [RUN, swap(RUN)])
add('high_knees', [P(5, (120, 30), (220, 160), (60, 180), (180, 180), lift=0.05), P(5, (220, 160), (120, 30), (180, 180), (60, 180), lift=0.05)])
add('butt_kicks', [P(5, (150, 60), (210, 150), (185, 330), (175, 175), lift=0.04), P(5, (210, 150), (150, 60), (175, 175), (185, 330), lift=0.04)])
add('skip_imitation ankle_hops', [P(0, (185, 130), (185, 130)), P(0, (185, 130), (185, 130), lift=0.08)])
add('lateral_shuffle', [F_HALF_SQUAT, P(0, (200, 160), (160, 200), (230, 170), (130, 190), front=True)])
add('skater_jump', [P(10, (220, 120), (140, 60), (205, 160), (200, 215), front=True), P(0, (200, 160), (160, 200), (190, 180), (170, 180), lift=0.18, front=True), P(350, (300, 240), (140, 200), (160, 145), (155, 200), front=True)])
add('mountain_climber', [with_(PLANK, ln=(120, 225)), with_(PLANK, lf=(120, 225))])
add('climber_toe_tap', [PLANK, with_(PLANK, ln=(150, 235))])
add('plank_jack', [PLANK, with_(PLANK, lift=0.05)])
add('half_burpee', [SQUAT_HANDS_DOWN, PLANK])
add('squat_thrust', [STAND, SQUAT_HANDS_DOWN, PLANK, SQUAT_HANDS_DOWN])
add('burpee tabata', [STAND, SQUAT_HANDS_DOWN, PLANK, PUSH_DOWN, SQUAT_HANDS_DOWN, with_(STAND_UP, lift=0.2)])
add('inchworm', [STAND, P(125), PIKE, PLANK])
add('star_jump', [P(0, (200, 160), (160, 200), (215, 165), (145, 195), front=True), P(0, (320, 320), (40, 40), (210, 210), (150, 150), lift=0.25, front=True)])
add('tuck_jump', [STAND, P(0, (120, 60), (120, 60), (40, 170), (40, 170), lift=0.3)])
add('standing_cross_crunch', [STAND_HEAD, P(12, (300, 105), (300, 60), (35, 180), (180, 180)), STAND_HEAD, P(12, (300, 60), (300, 105), (180, 180), (35, 180))])
# Leaning in, the opposite hand meets the toes of the leg swung up to hip height.
add('front_toe_touch', [STAND, P(35, (200, 200), (115, 115), (80, 80), (180, 180)), STAND, P(35, (115, 115), (200, 200), (180, 180), (80, 80))])
add('squat_knee_elbow', [STAND_HEAD, P(30, (330, 70), (330, 70), (95, 200), (95, 200)), P(25, (300, 100), (300, 60), (40, 180), (180, 180))])
add('standing_heel_touch', [P(0, (215, 175), (180, 180), (185, 300), (180, 180), front=True), P(0, (180, 180), (145, 185), (180, 180), (175, 60), front=True)])
add('step_touch grapevine v_step box_step dance_low zumba_video video_cardio', [P(0, (200, 120), (160, 240), (195, 195), (180, 180), front=True), P(0, (160, 60), (200, 300), (180, 180), (165, 165), front=True)])
add('dance_high video_cardio_hard hiit_moderate', [F_STAND, F_JACK_OUT, F_HALF_SQUAT])
add('knee_lift_clap', [STAND, P(0, (130, 150), (130, 150), (75, 180), (180, 180))])
add('walk_moderate walk_brisk heel_toe_walk', walking())
add('circuit_bw endurance_circuit', [STAND_FWD, SQUAT, F_STAND, F_JACK_OUT])

# ---- Warm-up, mobility, stretching, yoga --------------------------------------------------------------------------
add('arm_circles', [P(0, (250, 250), (110, 110), front=True), P(0, (290, 290), (70, 70), front=True)])
add('hip_circles', [P(10, (200, 135), (160, 225), front=True), P(350, (200, 135), (160, 225), front=True)])
add('cat_cow', [with_(QUAD, t=85, h=125), with_(QUAD, t=95, h=55)])
add('worlds_greatest', [with_(LUNGE_DOWN, t=60, an=(170, 175), af=(140, 150)), with_(LUNGE_DOWN, t=60, an=(170, 175), af=(10, 10))])
add('hamstring_stretch seated_forward_fold stretch_session', [P(65, (80, 80), (80, 80), (90, 90), (90, 90))], hold=True)
add('quad_stretch', [P(0, (90, 90), (200, 300), (180, 180), (180, 10))], hold=True)
add('chest_stretch', [P(10, (270, 0), (180, 180))], [wall(-0.2)], hold=True)
add('childs_pose', [P(115, (110, 100), (110, 100), (100, 270), (100, 270))], hold=True)
add('pilates yoga_hatha', [P(300, (80, 80), (80, 80), (60, 60), (60, 60)), P(300, (95, 95), (95, 95), (60, 60), (60, 60))])
add('sun_salutation yoga_vinyasa', [STAND_UP, P(125), PLANK, P(60, (170, 170), (170, 170), (262, 262), (262, 262)), PIKE])
add('yoga_power', [P(25, (10, 10), (10, 10), (110, 205), (110, 205)), P(0, (270, 270), (90, 90), (225, 175), (140, 140), front=True)])
add('downward_dog', [PIKE], hold=True)
add('warrior_two', [P(0, (270, 270), (90, 90), (225, 175), (140, 140), front=True)], hold=True)
add('tree_pose', [P(0, (350, 0), (10, 0), (180, 180), (120, 215), front=True)], hold=True)
add('single_leg_stand', [P(0, (90, 90), (270, 270), (180, 180), (95, 180))], hold=True)
add('neck_mobility neck_stretch', [P(0, h=335, front=True), P(0, h=25, front=True)])
add('shoulder_rolls', [P(0, (172, 180), (172, 180)), P(0, (188, 180), (188, 180))])
add('wrist_ankle_circles', [P(0, (90, 70), (90, 70), (180, 180), (180, 240)), P(0, (90, 110), (90, 110), (180, 180), (180, 210))])
# Wide stance, arms out; folding down to one side, a hand reaches the opposite foot and the other points up.
WIDE_ARMS_OUT = with_(F_WIDE, an=(270, 270), af=(90, 90))
TOE_TOUCH = P(140, (175, 175), (330, 330), (205, 205), (155, 155), front=True)
add('cross_toe_touch', [WIDE_ARMS_OUT, TOE_TOUCH, WIDE_ARMS_OUT, mirror(TOE_TOUCH)])
add('torso_twist', [P(0, (230, 100), (150, 100), front=True), P(0, (210, 260), (130, 260), front=True)])
add('standing_side_bend side_stretch', [P(15, (30, 60), (160, 170), (185, 185), (175, 175), front=True), P(345, (200, 190), (330, 300), (185, 185), (175, 175), front=True)])
add('leg_swings', [P(0, ln=(125, 125)), P(0, ln=(230, 230))])
add('arm_swings', [F_ARMS_OUT, P(0, (130, 130), (230, 230), front=True)])
add('hip_opener', [F_STAND, P(0, lf=(95, 180), front=True)])
add('squat_reach', [P(30, (160, 165), (160, 165), (95, 200), (95, 200)), STAND_UP])
add('calf_stretch', [P(30, (55, 55), (55, 55), (160, 180), (210, 210))], [wall(0.75)], hold=True)
add('figure_four', [P(270, (45, 100), (45, 100), (15, 115), (30, 90))], hold=True)
add('triceps_stretch', [P(0, (15, 195), (330, 40))], hold=True)
add('hip_flexor_stretch', [P(350, (15, 10), (5, 0), (100, 180), (210, 270))], hold=True)
add('supine_twist', [P(270, (90, 90), (90, 90), (60, 150), (40, 130))], hold=True)
add('butterfly_stretch', [P(0, (200, 175), (160, 185), (240, 120), (120, 240), front=True)], hold=True)

# ---- Dumbbells (the app draws dumbbells in the hands for every dumbbell exercise) --------------------------------
add('db_goblet_squat', [P(0, (155, 20), (155, 20)), P(35, (130, 10), (130, 10), (95, 200), (95, 200))])
add('db_swing', [P(70, (200, 200), (200, 200), (150, 190), (150, 190)), P(0, (90, 90), (90, 90))])
add('db_floor_press', [P(270, (150, 0), (150, 0), (45, 135), (45, 135)), P(270, (0, 0), (0, 0), (45, 135), (45, 135))])
add('db_floor_fly', [P(270, (300, 330), (60, 30), (45, 135), (45, 135)), P(270, (0, 0), (0, 0), (45, 135), (45, 135))])
add('db_shoulder_press', [F_PRESS_LOW, F_PRESS_UP])
add('db_arnold_press', [P(0, (160, 10), (160, 10)), P(0, (18, 12), (14, 8))])
add('db_lateral_raise', [P(0, (190, 190), (170, 170), front=True), P(0, (275, 275), (85, 85), front=True)])
add('db_front_raise', [STAND, P(0, (90, 90), (90, 90))])
add('db_rear_fly', [with_(HINGE, t=70), with_(HINGE, t=70, an=(260, 260), af=(260, 260))])
add('db_bent_row', [with_(HINGE, t=60), with_(HINGE, t=60, an=(245, 175), af=(245, 175))])
add('db_one_arm_row', [P(80, (180, 180), (180, 180), (170, 185), (200, 190)), P(80, (250, 175), (180, 180), (170, 185), (200, 190))], [box(0.45, 0.95, 0.42)])
add('db_renegade_row', [PLANK, with_(PLANK, af=(245, 160))])
add('db_pullover', [P(270, (270, 270), (270, 270), (45, 135), (45, 135)), P(270, (0, 0), (0, 0), (45, 135), (45, 135))])
add('db_shrug', [STAND, with_(STAND, t=2, an=(176, 180), af=(176, 180))])
add('db_biceps_curl db_hammer_curl', [STAND, P(0, (180, 20), (180, 20))])
add('db_overhead_triceps', [P(0, (18, 200), (14, 196)), P(0, (18, 14), (14, 10))])
add('db_kickback', [P(60, (250, 175), (250, 175), (170, 185), (170, 185)), P(60, (250, 250), (250, 250), (170, 185), (170, 185))])
add('db_skull_crusher', [P(270, (0, 0), (0, 0), (45, 135), (45, 135)), P(270, (0, 270), (0, 270), (45, 135), (45, 135))])
add('db_side_bend', [P(12, (190, 190), (170, 170), front=True), P(348, (190, 190), (170, 170), front=True)])
add('db_woodchop', [P(0, (320, 320), (330, 330), (195, 195), (165, 165), front=True), P(0, (160, 160), (150, 150), (195, 195), (165, 165), front=True)])
add('db_farmer_hold', walking(P(0, (180, 180), (180, 180), (165, 180), (195, 195))))
add('db_thruster db_strength_session db_circuit', [P(35, (150, 10), (150, 10), (95, 200), (95, 200)), P(0, (18, 12), (14, 8))])
add('db_clean_press', [with_(HINGE, t=30, ln=(150, 195), lf=(150, 195)), P(0, (150, 10), (150, 10)), P(0, (18, 12), (14, 8))])

# ---- Geometry shared with the app ---------------------------------------------------------------------------------
def vec(a, length):
    r = math.radians(a); return (length * math.sin(r), -length * math.cos(r))

def joints(p):
    """Points of a pose before grounding: hip at (0,0), y grows downwards."""
    add_ = lambda a, b: (a[0] + b[0], a[1] + b[1])
    hip = (0.0, 0.0); neck = vec(p['t'], LEN['torso'])
    head = add_(neck, vec(p['h'], LEN['neck'] + LEN['head']))
    if p['front']:
        sn, sf = add_(neck, vec(p['t'] - 90, SHOULDER_HALF)), add_(neck, vec(p['t'] + 90, SHOULDER_HALF))
        hn, hf = add_(hip, vec(p['t'] - 90, HIP_HALF)), add_(hip, vec(p['t'] + 90, HIP_HALF))
    else: sn = sf = neck; hn = hf = hip
    out = {'hip': hip, 'neck': neck, 'head': head, 'sn': sn, 'sf': sf, 'hn': hn, 'hf': hf}
    for side, s, h in (('n', sn, hn), ('f', sf, hf)):
        u, f = p['a' + side]; th, sh = p['l' + side]
        out['e' + side] = add_(s, vec(u, LEN['upper'])); out['w' + side] = add_(out['e' + side], vec(f, LEN['fore']))
        out['k' + side] = add_(h, vec(th, LEN['thigh'])); out['a' + side] = add_(out['k' + side], vec(sh, LEN['shin']))
        if not p['front']: out['t' + side] = add_(out['a' + side], vec(sh - 90, LEN['foot']))
    return out

def grounded(p):
    j = joints(p)
    low = max([y for k, (x, y) in j.items() if k != 'head'] + [j['head'][1] + LEN['head']])
    return {k: (x + p.get('dx', 0.0), y - low - p['lift']) for k, (x, y) in j.items()}

def svg_figure(frames, props, size=150):
    pts = [grounded(f) for f in frames]
    xs = [x for g in pts for x, _ in g.values()] + [0.0]; ys = [y for g in pts for _, y in g.values()]
    for pr in props:
        xs += [pr['x0'], pr['x1']] if pr['type'] == 'box' else [pr['x']]
    minx, maxx = min(xs) - 0.2, max(xs) + 0.2; miny, maxy = min(min(ys) - LEN['head'], -1.95) - 0.05, 0.1
    scale = (size - 10) / max(maxx - minx, maxy - miny)
    T = lambda x, y: ((x - minx) * scale + 5, (y - miny) * scale + 5)
    out = []
    for i, g in enumerate(pts):
        parts = [f'<svg width="{size}" height="{size}" style="background:#f6f7f2;margin:2px">']
        gx0, gy = T(minx, 0); gx1, _ = T(maxx, 0)
        parts.append(f'<line x1="{gx0}" y1="{gy}" x2="{gx1}" y2="{gy}" stroke="#bbb" stroke-width="2"/>')
        for pr in props:
            if pr['type'] == 'box':
                x0, y0 = T(pr['x0'], -pr['h']); x1, y1 = T(pr['x1'], 0)
                parts.append(f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" fill="#d9e3dc"/>')
            else:
                x, y0 = T(pr['x'], -2.0); _, y1 = T(pr['x'], 0)
                parts.append(f'<line x1="{x}" y1="{y0}" x2="{x}" y2="{y1}" stroke="#9aa" stroke-width="4"/>')
        def seg(a, b, color):
            x0, y0 = T(*g[a]); x1, y1 = T(*g[b])
            parts.append(f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}" stroke="{color}" stroke-width="{0.07 * scale}" stroke-linecap="round"/>')
        far, near = '#9fc7b4', '#28715c'
        for s, c in (('f', far), ('n', near)):
            seg('h' + s, 'k' + s, c); seg('k' + s, 'a' + s, c)
            if 't' + s in g: seg('a' + s, 't' + s, c)
        seg('hip', 'neck', near)
        if frames[i]['front']: seg('sn', 'sf', near); seg('hn', 'hf', near)
        for s, c in (('f', far), ('n', near)):
            seg('s' + s, 'e' + s, c); seg('e' + s, 'w' + s, c)
        hx, hy = T(*g['head']); parts.append(f'<circle cx="{hx}" cy="{hy}" r="{LEN["head"] * scale}" fill="#28715c"/>')
        parts.append('</svg>'); out.append(''.join(parts))
    return ''.join(out)

def build(output, sheet=None):
    exercises = json.loads(Path(output).with_name('exercises.json').read_text(encoding='utf-8'))
    ids = [e['id'] for e in exercises]
    missing = [i for i in ids if i not in M]; unknown = [i for i in M if i not in ids]
    assert not missing and not unknown, (missing, unknown)
    for i, m in M.items():
        assert 1 <= len(m['frames']) <= 6, i
        assert m['hold'] == (len(m['frames']) == 1), i
    data = [{'id': i, **M[i]} for i in ids]
    Path(output).write_text(json.dumps(data, separators=(',', ':')), encoding='utf-8')
    if sheet:
        names = {e['id']: e['name'] + ' · ' + e['nameEn'] for e in exercises}
        rows = ''.join(f'<div style="display:inline-block;vertical-align:top;margin:3px;border:1px solid #ddd;font-size:10px"><b>{i}</b><br>{svg_figure(M[i]["frames"], M[i]["props"], 112)}</div>' for i in ids)
        Path(sheet).write_text(f'<html><meta charset="utf-8"><body style="font-family:sans-serif">{rows}</body></html>', encoding='utf-8')
    return {'motions': len(data), 'frames': sum(len(d['frames']) for d in data)}

if __name__ == '__main__':
    print(build(Path('app/src/main/assets/exercises/figures.json'), sys.argv[1] if len(sys.argv) > 1 else None))
