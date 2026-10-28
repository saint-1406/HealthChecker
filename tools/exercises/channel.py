"""Workout videos of the channel "재호 - Fitness" (Jaeho) timed by the author's own chapters.

Most of the channel's descriptions list one chapter per exercise ("00:07 air squat", "00:35 reverse lunge"). jaeho_channel.json
keeps those chapters as published (id, title, length, chapters), read from the public watch pages on 2026-10-28; nothing was
downloaded from the videos. Here every chapter name is matched to a catalog exercise, so the app can show what is on and log
the workout. A chapter runs until the next one, so it holds the exercise and the rest next to it; the work time is estimated
by taking the usual rest off (see work_seconds). Videos that need gym equipment, or have a chapter we cannot name, are left out.
"""
import json, re
from pathlib import Path

SNAPSHOT = Path(__file__).with_name('jaeho_channel.json')

# Section prefixes and set numbers the channel writes into chapter names.
PREFIX = re.compile(r'^(warm up|cardio|core|power|strength|hip & core|upper abs|lower abs|oblique|side abs|total abs|abs & core|abs)\s*-?\s+(?=\S)')
TYPOS = {'palnk': 'plank', 'pank': 'plank', 'daedlift': 'deadlift', 'monutain': 'mountain', 'moutain': 'mountain', 'mountainclimber': 'mountain climber', 'spilt': 'split',
         'corss': 'cross', 'loblique': 'oblique', 'obilique': 'oblique', 'riase': 'raise', 'singleg': 'single', 'revers': 'reverse', 'dird': 'bird', 'birddog': 'bird dog',
         'crucnhes': 'crunches', 'sqaut': 'squat', 'chari': 'chair', 'explisive': 'explosive', 'donw': 'down', 'reafoot': 'rear foot', 'stoe': 'toe', 'str': 'star',
         'touchs': 'touches', 'goodmorning': 'good morning', 'pullup': 'pull up', 'working': 'walking', 'but': 'butt', 'sh': 'shoulder', 'oh': 'overhead', 'psuh': 'push',
         'burppe': 'burpee', 'trhunk': 'trunk', 'alternaing': 'alternating', 'crossvoer': 'crossover', 'exntensor': 'extensor', 'rotaion': 'rotation', 'flexior': 'flexor',
         'sidestep': 'side step', 'over': 'over'}
PLURAL = {'ups': 'up', 'crunches': 'crunch', 'climbers': 'climber', 'jacks': 'jack', 'knees': 'knee', 'lunges': 'lunge', 'squats': 'squat', 'ropes': 'rope',
          'touches': 'touch', 'elbows': 'elbow', 'kicks': 'kick', 'taps': 'tap', 'angels': 'angel', 'raises': 'raise', 'mornings': 'morning', 'chops': 'chop',
          'dips': 'dip', 'slams': 'slam', 'reaches': 'reach', 'kickbacks': 'kickback', 'extensions': 'extension', 'outs': 'out'}

def canon(label):
    """'05:44 Mountain Climber TWIST (R)' → 'mountain climber twist'; typos and plurals of the channel folded together."""
    s = label.lower().strip()
    s = re.sub(r'\((?:l|r|left|right|both)\)|\b(left|right)\b|\br & l\b', ' ', s)
    s = re.sub(r'[^a-z0-9&+ ]', ' ', s)
    s = ' '.join(PLURAL.get(TYPOS.get(w, w), TYPOS.get(w, w)) for w in s.split())
    s = re.sub(r'^\d+ reps\s*|^(final )?\d+ (round|set)\s*|^\d+set\s*|^\d+ (?=[a-z])|\s+\d+ ?(set|round)$|\s+\d+$', '', s).strip()
    s = PREFIX.sub('', s)
    s = re.sub(r'\bin and out\b|\bin out\b|\bin and outs\b', 'in & out', s)
    s = s.replace('over head', 'overhead').replace(' and ', ' & ').replace('+', '&')
    return re.sub(r'\s+', ' ', s).strip()

# Chapters that are parts of the video, not exercises: they become gaps of the timeline.
SECTION = re.compile(r'^(intro|outro|ready|rest|break|finish|end|warm up|cool down|stretch|stretching|round|set|lower body|upper body|full body|abs|core|cardio|final round|'
                     r'next|tabata|workout|start|bonus|abs circuit|abs circuit \d|last core|dumbbell lower body|dumbbell upper body|explosive bodyweight|dynamic warm up|'
                     r'exercise intensity control|bad posture|abdominal|gluteal|hamstring|hip|shoulder|neck|trunk|spine & abdominal|\d+|\d+ round|round \d+|\d+set)$')
# Gym equipment the home catalog has no stand-in for: such videos are not listed.
GYM = re.compile(r'\b(bench|ring|barbell|cable|chin up|gym ball|valslide|box jump|box squat|band|weighted vest|hanging|roll out|slide|farmers carry|incline db|incline dumbbell|pull down)\b|^pull up$')

# Same name, different exercise by the kind of video: lying in ab workouts, standing in the others.
ABS_TITLE = re.compile(r'\b(abs|six ?pack|6 ?pack|core|planks)\b', re.I)
DUMBBELL_TITLE = re.compile(r'\b(dumbbells?|db)\b', re.I)
BY_CONTEXT = {
    'heel touch': ('heel_touch', 'standing_heel_touch'),
    'knee to elbow': ('knee_elbow_crunch', 'standing_cross_crunch'),
    'knee to elbows': ('knee_elbow_crunch', 'standing_cross_crunch'),
}

# Canonical chapter name → catalog exercise id.
MAP = {
    # legs and glutes
    'air squat': 'squat', 'squat': 'squat', 'fast squat': 'squat', 'speed squat': 'squat', 'jumping squat': 'jump_squat', 'jump squat': 'jump_squat',
    'slow squat': 'squat_negative', 'negative squat': 'squat_negative', 'overhead squat': 'prisoner_squat', 'wide squat': 'sumo_squat', 'bounce squat': 'bounce_squat',
    'side squat': 'side_step_squat', 'side step squat': 'side_step_squat', 'side to side squat': 'side_step_squat', 'half squat side step': 'side_step_squat',
    'half squat & step': 'side_step_squat', 'squat jack': 'squat_jacks', 'in & out squat': 'squat_jacks', 'in & out jump squat': 'squat_jacks', 'in & out squat jump': 'squat_jacks',
    'jump squat in & out': 'squat_jacks', 'squat in & out': 'squat_jacks', 'leg in & out jump squat': 'squat_jacks', 'drop squat': 'squat_jacks', 'squat & jack': 'squat_and_jack',
    'squat & jumping jack': 'squat_and_jack', 'squat knee to elbow': 'squat_knee_elbow', 'knee to elbow squat': 'squat_knee_elbow', 'squat & knee to elbow': 'squat_knee_elbow',
    'squat side knee to elbow': 'squat_knee_elbow', 'squat & cross knee up': 'squat_knee_elbow', 'squat knee up': 'squat_knee_elbow', 'squat side knee up': 'squat_knee_elbow',
    'squat calf raise': 'calf_raise', 'calf raise': 'calf_raise', 'goblet squat': 'db_goblet_squat', 'db goblet squat': 'db_goblet_squat', 'db thruster': 'db_thruster',
    'dumbbell thruster': 'db_thruster', 'squat & press': 'db_thruster', 'db sumo deadlift': 'db_sumo_squat',
    'split squat': 'split_squat', 'rear foot elevated split squat': 'bulgarian_split_squat', 'rear foot split squat': 'bulgarian_split_squat',
    'rear elevated split squat': 'bulgarian_split_squat', 'rear foot elevated squat': 'bulgarian_split_squat', 'bulgarian split squat': 'bulgarian_split_squat',
    'split jump squat': 'jump_lunge', 'split squat jump': 'jump_lunge', 'leg split jump squat': 'jump_lunge', 'split jump & squat': 'jump_lunge',
    'reverse lunge': 'reverse_lunge', 'back lunge': 'reverse_lunge', 'reverse lunge twist': 'reverse_lunge', 'reverse lunge with twist': 'reverse_lunge',
    'reverse lunge to toe touch': 'reverse_lunge', 'side reverse lunge': 'curtsy_lunge', 'front lunge': 'forward_lunge', 'round front lunge': 'forward_lunge',
    'front & back lunge': 'forward_lunge', 'front to back lunge': 'forward_lunge', 'the front to back lunge': 'forward_lunge', 'side lunge': 'side_lunge',
    'lateral lunge': 'side_lunge', 'side to side lunge': 'side_lunge', 'side lunge touch': 'side_lunge', 'side lunge toe touch': 'side_lunge',
    'reverse lunge knee up': 'reverse_lunge_knee_up', 'reverse lunge & knee up': 'reverse_lunge_knee_up', 'reverse lunge & knee up twist': 'reverse_lunge_knee_up',
    'single leg deadlift': 'single_leg_rdl', 'single leg dead lift': 'single_leg_rdl', 'good morning': 'good_morning', 'bodyweight good morning': 'good_morning',
    'hip bridge': 'glute_bridge', 'heel bridge': 'glute_bridge', 'hip lift': 'glute_bridge', 'bridge step': 'glute_bridge', 'hip thrust': 'glute_bridge',
    'straight leg bridge': 'glute_bridge', 'single leg bridge': 'single_leg_bridge', 'one leg bridge': 'single_leg_bridge', 'glute kickback': 'donkey_kick',
    'bent knee kick back': 'donkey_kick', 'bent knee glute kickback': 'donkey_kick', 'side leg raise': 'side_leg_raise', 'side to side shuffle': 'lateral_shuffle',
    # push
    'push up': 'pushup', 'standard push up': 'pushup', 'standard': 'pushup', 'negative push up': 'pushup', 'narrow push up': 'diamond_pushup', 'narrow': 'diamond_pushup',
    'knee push up': 'knee_pushup', 'incline push up': 'incline_pushup', 'decline push up': 'decline_pushup', 'pike push up': 'pike_pushup', 'pike': 'pike_pushup',
    'hindu push up': 'hindu_pushup', 'upper hindu push up': 'hindu_pushup', 'side to side push up': 'wide_pushup', 'in & out push up': 'wide_pushup',
    'explosive push up': 'explosive_pushup', 'push up & pike': 'pushup_pike', 'pike & push up': 'pushup_pike', 'push & pike': 'pushup_pike',
    'push up & knee up': 'pushup_knee_up', 'push & knee up': 'pushup_knee_up', 'push up with knee drive': 'pushup_knee_up', 'push up to knee tuck': 'pushup_knee_up',
    'push up & t rotation': 'pushup_t_rotation', 'push up t rotation': 'pushup_t_rotation', 't rotation push up': 'pushup_t_rotation', 'push up & rotation': 'pushup_t_rotation',
    'side push up': 'pushup_t_rotation', 'push up & tap': 'plank_shoulder_tap', 'push up shoulder tap': 'plank_shoulder_tap', 'push up toe touch': 'plank_toe_touch',
    'push up burpee': 'burpee', 'chair dip': 'chair_dip', 'triceps dip': 'chair_dip', 'arm walking push up': 'inchworm',
    # back and posture
    'back extension': 'back_extension', 'prone back extension': 'back_extension', 'superman pull up': 'prone_pullup', 'prone pull up': 'prone_pullup',
    'reverse snow angel': 'snow_angel', 'upper reverse snow angel': 'snow_angel', 'prone snow angel': 'snow_angel', 'prone reverse angel': 'snow_angel', 'reverse angel': 'snow_angel',
    't raise': 'prone_t_raise', 'prone t raise': 'prone_t_raise', 'prone t exercise': 'prone_t_raise', 'prone y exercise': 'ytw_raise', 'bird dog': 'bird_dog',
    'bird dog exercise': 'bird_dog', 'inverted row': 'doorframe_row',
    # dumbbells
    'dumbbell row': 'db_bent_row', 'bent over row': 'db_bent_row', 'db bent over row': 'db_bent_row', 'bend row': 'db_bent_row', 'one arm row': 'db_one_arm_row',
    'quadruped row': 'db_one_arm_row', 'high plank row': 'db_renegade_row', 'deadlift to row': 'db_bent_row', 'good morning & row': 'db_bent_row',
    'dumbbell deadlift': 'db_deadlift', 'db press': 'db_shoulder_press', 'shoulder press': 'db_shoulder_press', 'overhead press': 'db_shoulder_press',
    'one arm press': 'db_shoulder_press', 'dumbbell press': 'db_floor_press', 'db floor press': 'db_floor_press', 'floor seated db press': 'db_shoulder_press',
    'decline floor press': 'db_floor_press', 'decline press': 'db_floor_press', 'chest press': 'db_floor_press', 'dumbbell narrow press': 'db_floor_press', 'side lateral raise': 'db_lateral_raise',
    'fr & la db raise': 'db_lateral_raise', 'reverse fly': 'db_rear_fly', 'db arm curl': 'db_biceps_curl', 'arm curl & press': 'db_biceps_curl', 'db curl to press': 'db_biceps_curl',
    'dumbbell curl to press': 'db_biceps_curl', 'hammer curl': 'db_hammer_curl', 'db triceps extension': 'db_overhead_triceps', 'lying triceps extension': 'db_skull_crusher',
    'triceps kickback': 'db_kickback', 'db side bend': 'db_side_bend', 'weighted side bend': 'db_side_bend', 'dumbbell swing': 'db_swing', 'dumbbell snatch': 'db_clean_press',
    'db crunch': 'crunch', 'weighted crunch': 'crunch', 'db leg raise': 'leg_raise', 'db hold & leg raise': 'leg_raise', 'db hold & leg press': 'lying_leg_press', 'db high knee': 'high_knees',
    # core
    'crunch': 'crunch', 'slow crunch': 'crunch', 'abs crunch': 'crunch', 'mcgill crunch': 'mcgill_crunch', 'mcgill sit up': 'mcgill_crunch', 'extended crunch': 'extended_crunch',
    'abs extended crunch': 'extended_crunch', 'star crunch': 'star_crunch', 'standing star crunch': 'standing_knee_crunch', 'cross crunch': 'knee_elbow_crunch',
    'knee to elbow crunch': 'knee_elbow_crunch', 'elbow to knee crunch': 'knee_elbow_crunch', 'knee up crunch': 'knee_elbow_crunch', 'knee touch crunch': 'knee_elbow_crunch',
    'oblique crunch': 'oblique_crunch', 'side crunch': 'oblique_crunch', 'lateral crunch': 'oblique_crunch', 'twist crunch': 'twist_crunch', 'crunch twist': 'twist_crunch',
    'twisting crunch': 'twist_crunch', 'bicycle crunch': 'bicycle_crunch', 'table top crunch': 'tabletop_crunch', 'tabletop crunch': 'tabletop_crunch',
    'heel touch crunch': 'heel_touch', 'crunch heel touch': 'heel_touch', 'bridge crunch': 'bridge_crunch', 'bridge & crunch': 'bridge_crunch', 'bridge to crunch': 'bridge_crunch',
    'crunch to bridge': 'bridge_crunch', 'leg raise': 'leg_raise', 'seated leg raise': 'leg_raise', 'knee raise': 'reverse_crunch', 'single leg raise': 'single_leg_raise',
    'hip raise': 'hip_raise', 'high plank hip raise': 'plank_pike', 'reverse crunch': 'reverse_crunch', 'seated reverse crunch': 'reverse_crunch', 'flutter kick': 'flutter_kick',
    'seated scissor kick': 'flutter_kick', 'leg press': 'lying_leg_press', 'single leg press': 'lying_leg_press', 'single leg stretch': 'lying_leg_press', 'dead bug': 'dead_bug',
    'l sit up': 'situp', 'l sit up twist': 'situp', 'sprinter sit up': 'situp', 'runner sit up': 'situp', 'sprinter crunch': 'situp', 'side sit up': 'oblique_crunch',
    'v sit up': 'v_up', 'v crunch': 'v_up', 'v sit crunch': 'v_sit_in_out', 'v sit in & out': 'v_sit_in_out', 'v in & out': 'v_sit_in_out', 'seated in & out': 'v_sit_in_out',
    'seated twist': 'russian_twist', 'russian twist': 'russian_twist', 'abs russian twist': 'russian_twist', 'seated rotation': 'russian_twist', 'seated twist & reach': 'russian_twist',
    'straight leg russian twist': 'russian_twist', 'trunk twist': 'russian_twist', 'twist': 'russian_twist', 't rotation': 'lying_knee_rotation', 'supine t rotation': 'lying_knee_rotation',
    'supine trunk rotation': 'lying_knee_rotation', 'plank': 'plank', 'plank hold': 'plank', 'dolphin plank': 'forearm_plank_pike', 'side plank': 'side_plank',
    'side plank hold': 'side_plank', 'side plank arm raise': 'side_plank', 'side plank leg raise': 'side_plank', 'adductor side plank': 'side_plank',
    'side plank twist': 'side_plank', 'side plank knee to elbow': 'side_plank', 'side plank up & down': 'side_plank_dip', 'side plank up down': 'side_plank_dip',
    'side plank hip lifts': 'side_plank_dip', 'plank twist': 'plank_hip_twist', 'plank & twist': 'plank_hip_twist', 'plank hip twist': 'plank_hip_twist',
    'side to side plank': 'plank_hip_twist', 'plank up & down': 'plank_updown', 'up down plank': 'plank_updown', 'body saw & plank up down': 'plank_updown',
    'plank body saw': 'plank', 'plank reach': 'plank_reach', 'plank & reach': 'plank_reach', 'plank arm raise': 'plank_reach', 'plank knee up': 'plank_knee_drive',
    'plank knee to elbow': 'plank_knee_drive', 'high plank knee to elbow': 'plank_knee_drive', 'high plank knee up': 'plank_knee_drive', 'high plank knee touch': 'plank_knee_drive',
    'elbow plank knee drive': 'plank_knee_drive', 'plank pike knee drive': 'plank_knee_drive', 'plank toe in & out': 'plank_toe_in_out', 'plank in & out': 'plank_jack',
    'mountain climber in & out': 'plank_jack', 'plank & pike': 'plank_pike', 'high plank & pike': 'plank_pike', 'plank pike': 'forearm_plank_pike', 'pike toe touch': 'plank_toe_touch',
    'pike & toe touch': 'plank_toe_touch', 'plank toe touch': 'plank_toe_touch', 'high plank toe touch': 'plank_toe_touch', 'plank toe tap': 'climber_toe_tap',
    'plank with toe tap': 'climber_toe_tap', 'mountain climber toe tap': 'climber_toe_tap', 'plank t rotation': 'plank_t_rotation', 'high plank t rotation': 'plank_t_rotation',
    'high plank rotation': 'plank_t_rotation', 'plank & t rotation': 'plank_t_rotation', 'plank shoulder tap': 'plank_shoulder_tap', 'high plank shoulder tap': 'plank_shoulder_tap',
    'shoulder tap': 'plank_shoulder_tap', 'high plank shoulder & knee tap': 'plank_shoulder_tap', 'high plank shoulder & toe touch': 'plank_shoulder_tap',
    'plank kick back': 'donkey_kick', 'plank kickback': 'donkey_kick', 'plank side walk': 'bear_crawl', 'plank knee & up & out': 'high_plank_toe_in_out',
    # cardio
    'jumping jack': 'jumping_jacks', 'full jumping jack': 'jumping_jacks', 'high knee': 'high_knees', 'full high knee': 'high_knees', 'butt kick': 'butt_kicks',
    'full butt kick': 'butt_kicks', 'jog on the spot': 'jog_in_place', 'jogging on the spot': 'jog_in_place', 'jogging in place': 'jog_in_place', 'run in place': 'jog_in_place',
    'running': 'jog_in_place', 'walking': 'march_in_place', 'jump rope': 'skip_imitation', 'scissor hops': 'skip_imitation', 'a skip': 'a_skip', 'burpee': 'burpee',
    'burpee push up': 'burpee', 'burpee jump': 'burpee', 'slow burpee': 'half_burpee', 'slow burpee push up': 'half_burpee', 'slow burpee shoulder tap': 'half_burpee',
    'negative & jump': 'burpee', 'push up & jack': 'burpee', 'mountain climber': 'mountain_climber', 'mountain climber running': 'mountain_climber',
    'alternating mountain climber': 'mountain_climber', 'slow mountain climber': 'mountain_climber', 'jumping mountain climber': 'mountain_climber', 'mountain climber jump': 'mountain_climber',
    'mountain climber split jump': 'mountain_climber', 'split jump mountain climber': 'mountain_climber', 'split mountain climber': 'mountain_climber', 'mountain climber split': 'mountain_climber',
    'abs mountain climber': 'mountain_climber', 'mountain climber twist': 'cross_mountain_climber', 'cross mountain climber': 'cross_mountain_climber',
    'mountain climber oblique': 'cross_mountain_climber', 'side to side mountain climber': 'cross_mountain_climber', 'side jump squat': 'skater_jump', 'side step': 'lateral_shuffle',
    'side to side': 'lateral_shuffle', 'arm walking': 'inchworm', 'squat & jog on the spot': 'jog_in_place', 'squat & heel touch': 'standing_heel_touch',
    # standing core
    'standing crunch': 'standing_knee_crunch', 'standing knee crunch': 'standing_knee_crunch', 'standing knee drive': 'standing_knee_crunch', 'standing knee raise': 'standing_knee_crunch',
    'knee up': 'standing_knee_crunch', 'front & side knee up': 'standing_side_crunch', 'side knee up': 'standing_side_crunch', 'side knee to elbow': 'standing_side_crunch',
    'standing side crunch': 'standing_side_crunch', 'cross knee to elbow': 'standing_cross_crunch', 'cross knee drive': 'standing_cross_crunch', 'standing cross knee up': 'standing_cross_crunch',
    'knee up twist': 'standing_cross_crunch', 'knee up & twist': 'standing_cross_crunch', 'cross knee to elbow & kick': 'standing_cross_crunch', 'back toe touch & knee up': 'standing_heel_touch',
    'standing toe touch': 'front_toe_touch', 'front toe touch': 'front_toe_touch', 'standing leg raise': 'front_toe_touch', 'side toe touch': 'cross_toe_touch',
    'side to side toe touch': 'cross_toe_touch', 'side to side touch': 'cross_toe_touch', 'standing cross toe touch': 'cross_toe_touch', 'opp toe touch': 'cross_toe_touch',
    'crossover toe touch': 'cross_toe_touch', 'rotating toe touch': 'cross_toe_touch', 'side step toe touch': 'cross_toe_touch', 'toe touch stretch': 'hamstring_stretch',
    'standing side bend': 'standing_side_bend', 'woodchop': 'woodchop', 'cross chop': 'woodchop', 'lateral chop': 'woodchop', 'side to side slam': 'woodchop', 'side slam': 'woodchop',
    # warm-up and mobility
    'arm circle': 'arm_circles', 'dynamic stretching arm circles': 'arm_circles', 'knee to chest': 'hip_opener', 'leg circle': 'hip_circles', 'hip mobility': 'hip_circles',
    'hip & t spine rotation': 'worlds_greatest', 'hip & thoracic stretch': 'worlds_greatest', 'shoulder & thoracic mobility': 'arm_swings', 't extension': 'cat_cow',
    'boat pose beginner': 'hollow_hold', 'pec stretch': 'chest_stretch', 'static stretching chest': 'chest_stretch', 'static stretching triceps': 'triceps_stretch',
    'leg stretch': 'hamstring_stretch', 'hip flexor & knee extensor': 'hip_flexor_stretch', 'hip flexor': 'hip_flexor_stretch', 'trunk & triceps & latissimus': 'side_stretch', 'pike single leg': 'single_leg_rdl',
    'single leg deadlift hip extensor & hamstring & calf': 'single_leg_rdl',
}

def exercise_for(name, title):
    if name in BY_CONTEXT:
        lying, standing = BY_CONTEXT[name]
        return lying if ABS_TITLE.search(title) else standing
    if name in ('deadlift', 'dumbbell deadlift'): return 'db_deadlift' if DUMBBELL_TITLE.search(title) else 'good_morning'
    if name in ('kick back',): return 'db_kickback' if DUMBBELL_TITLE.search(title) else 'donkey_kick'
    return MAP.get(name)

FOREIGN = '[가-힣ㄱ-ㆎऀ-ॿ]'

def clean_title(title):
    """The English title: the channel repeats it in Korean or Hindi, so brackets and words in those scripts are dropped."""
    s = re.sub(r'\([^()]*' + FOREIGN + r'[^()]*\)', ' ', title)
    s = re.sub(r'\S*' + FOREIGN + r'\S*', ' ', s)
    s = re.sub(r'\s+', ' ', s)
    s = re.sub(r'(\s*\|\s*\d+)+\s*$', '', s)
    s = re.sub(r'^[\s|&,.:-]+|[\s|&,.:-]+$', '', s)
    s = re.sub(r'(\|\s*)+\|', '|', s)
    return s if re.search('[A-Za-z]', s) else title.strip()

def work_seconds(length):
    """A chapter holds the exercise and the rest next to it: about 20 s of rest in a minute-long block, 10 s in a shorter one."""
    return length - (20 if length >= 50 else 10 if length >= 30 else 0)

TAGS = [('abs', r'\b(abs|six ?pack|6 ?pack|core|planks)\b'), ('cardio', r'\b(cardio|hiit|tabata)\b'), ('full', r'\bfull body\b'),
        ('legs', r'\b(leg|legs|lower body|squat)\b'), ('upper', r'\b(chest|shoulders?|arms?|upper body|back|push ?ups?|triceps)\b'),
        ('dumbbells', r'\b(dumbbells?|db)\b'), ('nojump', r'\b(no jump|low impact|standing)\b'), ('warmup', r'\b(warm up|stretch\w*)\b')]

def videos(catalog, skip=()):
    """The channel's videos as library entries; returns (videos, why each left-out video was left out)."""
    snapshot = json.loads(SNAPSHOT.read_text(encoding='utf-8'))
    out, left_out = [], {}
    for v in snapshot:
        if v['id'] in skip: continue
        chapters = sorted(v['chapters'])
        named = [(sec, raw, canon(raw)) for sec, raw in chapters]
        gym = [raw for sec, raw, c in named if GYM.search(c)]
        if gym: left_out[v['id']] = 'gym: ' + ', '.join(gym); continue
        segments, unknown = [], []
        for i, (sec, raw, c) in enumerate(named):
            if not c or SECTION.match(c): continue
            ident = exercise_for(c, v['title'])
            if ident is None or ident not in catalog: unknown.append(raw); continue
            end = named[i + 1][0] if i + 1 < len(named) else min(v['lengthSeconds'], sec + 60)
            if end <= sec: continue
            segments.append({'start': sec, 'end': end, 'exerciseId': ident, 'title': raw.strip(), 'section': '', 'sectionEn': '', 'note': '', 'noteEn': '',
                             'work': work_seconds(end - sec)})
        if unknown: left_out[v['id']] = 'unknown: ' + ', '.join(unknown); continue
        if len(segments) < 6: left_out[v['id']] = 'too few exercises'; continue
        tags = [t for t, rx in TAGS if re.search(rx, v['title'], re.I)]
        out.append({'id': 'yt:' + v['id'], 'title': clean_title(v['title']), 'channel': '재호 - Fitness (Jaeho)', 'youtubeId': v['id'],
                    'durationSec': v['lengthSeconds'], 'embeddable': v['embeddable'], 'about': '', 'aboutEn': '', 'segments': segments, 'timing': 'chapters', 'tags': tags})
    return out, left_out
