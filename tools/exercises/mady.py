"""Workout videos of the channel "Mady Morrison" (Pilates, yoga, stretching, home workouts).

madymorrison_channel.json keeps, for every workout video of the channel, what its public watch page shows: id, title, length
(from the channel's video list) and the chapters with their source: "author" when the description lists them (whole
seconds), "auto" when YouTube generated them (fractional seconds, rounded here), "none" when there are none. Read on
2026-11-07; nothing was downloaded from the videos and no transcripts were used (YouTube asks to sign in for the player
data). Vlogs, recipes, meditations and short asana tutorials are left out of the snapshot.

A video whose chapters name its moves gets a timeline: every chapter becomes a segment of its catalog exercise ("LOW LUNGE R"
then "LOW LUNGE L" are two sets of one exercise, with the side as a note; "40X JUMPING JACK" notes the 40 reps). Chapters
that are parts of the video, not moves (intro, rest, final breathing), are gaps. A chapter that names what is trained but
not the move ("Flow Runde 2", "Oblique work") counts as the video's kind of session and says so in its note. Her flows run
on without rests, so a chapter's time counts as the move's time; where a chapter runs far longer than the others (the
moves after it carry no chapter) only the usual length is kept and the rest stays unmarked.
A video without usable chapters is one whole session of the kind its title names, so it can still be logged in one tap.
"""
import json, re, statistics
from pathlib import Path

SNAPSHOT = Path(__file__).with_name('madymorrison_channel.json')
CHANNEL = 'Mady Morrison'

# Words the chapters put around a move's name: counters ("next exercise:", "40X"), sides and German side words.
NOISE = re.compile(r'^(first|next|last|final)\s+(exercise|pose|stretch)\s*:?\s*|^\d+\s*x\s*|\((?:r|l|right|left|rechts|links|both)\)|'
                   r'\b(right|left|rechts|links)\s+(side|seite)\b|\s+(r|l|right|left|rechts|links)$|^(left|right)\s+')
TYPOS = {'solat': 'squat', 'lizzard': 'lizard', 'bycicle': 'bicycle', 'doc': 'dog', 'lunger': 'lunge', 'pliet': 'plie', 'downdog': 'down dog',
         'childspose': 'childs pose', 'inch worms': 'inchworm', 'pushups': 'push ups', 'situps': 'sit ups'}

def canon(label):
    """'next exercise: LOW LUNGE QUAD STRETCH R' -> 'low lunge quad stretch'."""
    s = label.lower().strip().replace('’', "'").replace('ß', 'ss').replace('/', ' ')
    s = re.sub(r'kick(r|l)$', 'kick', s)
    for _ in range(2): s = NOISE.sub(' ', s).strip()
    s = re.sub(r"[^a-z0-9&äöü ]", ' ', s)
    s = re.sub(r'\s+', ' ', s).strip()
    for a, b in TYPOS.items(): s = re.sub(r'\b' + a + r'\b', b, s)
    return s

def side(label):
    """The side a chapter works, as a note in both languages, or empty. "KICKR" is "kick R" written together."""
    s = label.strip()
    if re.search(r'(\s|\()(r|right|rechts)\)?$|^right\s', s, re.I) or s.endswith('KICKR'): return ('правая сторона', 'right side')
    if re.search(r'(\s|\()(l|left|links)\)?$|^left\s', s, re.I): return ('левая сторона', 'left side')
    return ('', '')

def reps(label):
    m = re.match(r'\s*(\d+)\s*x\s', label, re.I)
    return (m.group(1) + ' повторений', m.group(1) + ' reps') if m else ('', '')

# Chapters that are parts of the video, not moves: they become gaps of the timeline.
GAP = re.compile(r'^(intro|introduction|einleitung|begrüssung|welcome|outro|end|ende|focus on your breath|rest|pause|'
                 r'f mady morrison|mady morrison summer bootcamp)$')

# Canonical chapter name -> catalog exercise id.
MAP = {
    # stretching and yoga poses
    'seated side stretch': 'side_stretch', 'side stretch': 'side_stretch', 'side wave stretch': 'side_stretch', 'walk the dog': 'downward_dog',
    'down dog': 'downward_dog', 'down dog waves': 'downward_dog', 'low lunge': 'low_lunge', 'deep lunge': 'low_lunge', 'low lunge arm circles': 'low_lunge',
    'low lunge quad stretch': 'quad_stretch', 'runners stretch': 'hamstring_stretch', 'one leg forward bend': 'hamstring_stretch',
    'one legged forward bend': 'hamstring_stretch', 'lizard pose': 'lizard_pose', 'lizard rotation': 'lizard_pose', 'standing forward bend': 'standing_forward_bend',
    'forward fold': 'standing_forward_bend', 'yogi squat': 'yogi_squat', 'seated forward bend': 'seated_forward_fold', 'cross legged forward bend': 'seated_forward_fold',
    'seated twist': 'seated_spinal_twist', 'cat cow': 'cat_cow', 'seated cat cow': 'cat_cow', 'katze kuh': 'cat_cow', 'calf stretch': 'calf_stretch',
    'extended puppy pose': 'puppy_pose', 'sphinx pose': 'sphinx_pose', 'childs pose': 'childs_pose', 'childs pose twist': 'thread_the_needle',
    'neck stretch': 'neck_stretch', 'neck release': 'neck_stretch', 'triceps stretch': 'triceps_stretch', 'butterfly stretch': 'butterfly_stretch',
    'seated pigeon': 'figure_four', 'needle pose': 'figure_four', 'nadelöhr': 'figure_four', 'straddle pose': 'straddle_stretch',
    'straddle stretch': 'straddle_stretch', 'supported hero pose': 'hero_pose', 'herz für adlerarme': 'eagle_arms', 'eagle arms': 'eagle_arms',
    'liegende drehung': 'supine_twist', 'lying twist': 'supine_twist', 'minipäckchen': 'knees_to_chest', 'cobra updog': 'cobra_pose',
    'baby cobra': 'cobra_pose', 'big cobra': 'cobra_pose',
    # warm-up
    'arm circles backward': 'arm_circles', 'arm circles forward': 'arm_circles', 'arm circles': 'arm_circles', 'shoulder circle': 'shoulder_rolls',
    'hip rotation': 'hip_circles', 'inchworm': 'inchworm', 'inchworms': 'inchworm', 'walking': 'march_in_place', 'lunge & open': 'worlds_greatest',
    # workout
    'jumping jacks': 'jumping_jacks', 'jumping jack': 'jumping_jacks', 'squat & punch': 'squat', 'jump squats': 'jump_squat', 'air squat': 'squat',
    'air squats': 'squat', 'slow cross mountain climbers': 'cross_mountain_climber', 'mountain climbers': 'mountain_climber', 'superman & cactusman': 'superman',
    'superman': 'superman', 'curtsy lunges': 'curtsy_lunge', 'high knees': 'high_knees', 'shoulder tap': 'plank_shoulder_tap',
    'shoulder tap & plank': 'plank_shoulder_tap', 'sumo squat heels lift': 'sumo_squat', 'plie squats': 'sumo_squat', 'bicycle crunch': 'bicycle_crunch',
    'bicycle': 'bicycle_crunch', 'reverse crunch': 'reverse_crunch', 'side plank rotations': 'side_plank', 'side plank': 'side_plank',
    'side plank crunch': 'side_plank', 'straight leg raise': 'leg_raise', 'leg raise': 'leg_raise', 'diamond hip lift': 'glute_bridge', 'frog lift': 'glute_bridge',
    'bridge lift': 'glute_bridge', 'single leg hip lift': 'single_leg_bridge', 'hip thrust': 'single_leg_bridge', 'donkey kick': 'donkey_kick',
    'donkey kicks': 'donkey_kick', 'plank hold': 'plank', 'skaters': 'skater_jump', 'side lunge': 'side_lunge', 'push ups': 'pushup', 'lunges': 'forward_lunge',
    'sit ups': 'situp', 'mini crunch': 'crunch', 'russian twist': 'russian_twist', 'hollow hold': 'hollow_hold', 'hollow position': 'hollow_hold',
    'tabletop balance': 'bird_dog', 'triceps push ups': 'diamond_pushup', 'lunge pulse': 'split_squat',
    # Pilates (YouTube's generated chapters of f-vouCZ5FUg)
    'introduction and toe taps': 'pilates_toe_tap', 'double toe taps': 'pilates_toe_tap', 'single leg stretch': 'pilates_single_leg_stretch',
    'diagonal sit ups': 'twist_crunch', 'roll up and twist': 'pilates_roll_up', 'bicycle reach': 'bicycle_crunch', 'scissors up and down': 'pilates_scissors',
    'straight leg pulls': 'pilates_scissors', 'scissors': 'pilates_scissors', 'plank and cooldown': 'plank',
}

# YouTube's own chapters are mostly "key moments" named after a body part ("Schulter", "Rücken"), useless as a timeline.
# This video's generated chapters do name the moves, so they are used.
AUTO_OK = {'f-vouCZ5FUg'}
# Chapters that say what is trained, or name a combination we have no exercise for, but not one move: the video's own kind
# of session (None) or the named kind of work, with a note saying so.
VAGUE = {'abdominal conditioning': None, 'oblique work': None, 'final abdominal exercises': None, 'spine mobilisation': None,
         'heart opener & easy twist': None, 'forward bend to mini wild thing': None, 'schneidersitz': None, 'swing position': None,
         'hund': None, 'dreibeiniger hund': None, 'krähe': None, 'vierfüsslerstand': None, 'aufwärmrunde': None,
         'flow runde 1': None, 'flow runde 2': None, 'flow runde 3': None, 'flow runde 4': None, 'flow runde 5': None, 'flow runde 6': None,
         'standing toe tap': None, 'toe tap': None, 'twist floor tap': None, 'lunge & kick': None, 'in & out squat pulses': None, 'skater lunges': None,
         'side to side punch': None, 'plank side to side tap': None, 'lunge & burpees': None, 'shuffle & high knees': None,
         'mt climbers & push ups': None, 'full body crunch': None, 'roll & jump': None, 'rotation squat': None, 'push up walk': None, 'plank walk': None,
         'diagonal raise': None, 'shoulder stretch': 'stretch_session', 'forward bend': 'stretch_session'}
VAGUE_NOTE = ('глава не называет одно упражнение — записано как занятие видео', 'the chapter names no single move, so it counts as the video\'s session')
LONG_NOTE = ('дальше глава идёт без названий движений', 'the chapter runs on with unnamed moves')

# A video without usable chapters is listed as one session of the kind its title names, first match wins.
KIND = [('pilates', r'\bpilates\b', ('Пилатес: вся тренировка', 'Pilates: the whole workout')),
        ('yoga_power', r'power ?(yoga|vinyasa|flow)|fat ?burning yoga|kraftvoll', ('Силовая йога: всё занятие', 'Power yoga: the whole session')),
        ('sun_salutation', r'sonnengru|sun salutation|surya', ('Приветствие солнцу: всё занятие', 'Sun salutations: the whole session')),
        ('hiit_moderate', r'\bhiit\b|fat ?burn|fett verbrennen', ('Интервальная тренировка целиком', 'Interval workout: the whole video')),
        ('yoga_vinyasa', r'vinyasa|\bflow\b', ('Йога-флоу: всё занятие', 'Yoga flow: the whole session')),
        ('yoga_power', r'^(?!.*(anfänger|beginner)).*yoga.*(workout|straff)', ('Силовая йога: всё занятие', 'Power yoga: the whole session')),
        ('stretch_session', r'stretch|dehn|mobility|cool ?down|beweglich', ('Растяжка: всё занятие', 'Stretching: the whole session')),
        ('circuit_bw', r'workout|booty|toned|training|bootcamp|fitness', ('Тренировка целиком', 'Workout: the whole video')),
        ('yoga_hatha', r'yoga', ('Йога: всё занятие', 'Yoga: the whole session'))]

TAGS = [('abs', r'\b(abs|core|bauch|six ?pack|plank)'), ('cardio', r'\b(cardio|hiit|fat ?burn\w*|fett)'), ('full', r'\b(full body|ganzkörper)'),
        ('legs', r'\b(legs?|booty|butt|thighs?|beine|po|lower body)\b'), ('upper', r'\b(arms?|shoulders?|upper back|arme|schultern?)\b'),
        ('nojump', r'\b(stretch\w*|yoga|pilates|dehn\w*)'), ('warmup', r'\b(mobility|cool ?down|morning stretch|morgenroutine)'),
        ('yoga', r'\byoga'), ('pilates', r'\bpilates\b'), ('stretch', r'\b(stretch\w*|dehn\w*|mobility|flexib\w*)')]

def kind(title):
    for ident, rx, names in KIND:
        if re.search(rx, title, re.I): return ident, names
    raise ValueError(title)

def videos(catalog, snapshot=SNAPSHOT):
    """The channel's videos as library entries; returns (videos, why a video is listed as a whole session)."""
    out, whole = [], {}
    for v in json.loads(Path(snapshot).read_text(encoding='utf-8')):
        segments, why = timeline(v, catalog)
        tags = [t for t, rx in TAGS if re.search(rx, v['title'], re.I)]
        entry = {'id': 'yt:' + v['id'], 'title': v['title'], 'channel': CHANNEL, 'youtubeId': v['id'], 'durationSec': v['lengthSeconds'], 'embeddable': False}
        if segments:
            out.append(entry | {'segments': segments, 'timing': 'chapters', 'tags': tags}); continue
        whole[v['id']] = why
        ident, (title, title_en) = kind(v['title'])
        assert ident in catalog, (v['id'], ident)
        out.append(entry | {'about': 'Глав с упражнениями в видео нет, поэтому оно записывается одним занятием на всю длину. ' + title + '.',
                            'aboutEn': 'The video has no chapters naming its moves, so it is logged as one session for its whole length. ' + title_en + '.',
                            'segments': [{'start': 0, 'end': v['lengthSeconds'], 'exerciseId': ident, 'title': title_en}], 'timing': 'whole', 'tags': tags})
    return out, whole

def timeline(v, catalog):
    """The video's moves from its chapters, or ([], why there are none)."""
    if v['source'] == 'none': return [], 'no chapters'
    if v['source'] == 'auto' and v['id'] not in AUTO_OK: return [], 'only YouTube key moments'
    chapters = sorted((int(sec), raw) for sec, raw in v['chapters'])
    ends = [b for (b, _) in chapters[1:]] + [v['lengthSeconds']]
    usual = statistics.median(b - a for (a, _), b in zip(chapters, ends))
    cap = max(90, 2 * usual)
    segments, unknown = [], []
    for (sec, raw), end in zip(chapters, ends):
        c = canon(raw)
        if not c or GAP.match(c): continue
        if c in VAGUE:
            ident, (note, note_en) = VAGUE[c] or kind(v['title'])[0], VAGUE_NOTE
        elif c in MAP:
            ident, (note, note_en) = MAP[c], side(raw)
            r, r_en = reps(raw)
            if r: note, note_en = r, r_en
        else:
            unknown.append(raw); continue
        if end - sec > cap:
            end = sec + round(usual); note, note_en = '; '.join(x for x in (note, LONG_NOTE[0]) if x), '; '.join(x for x in (note_en, LONG_NOTE[1]) if x)
        assert ident in catalog, (v['id'], raw, ident)
        if end <= sec: continue
        segments.append({'start': sec, 'end': end, 'exerciseId': ident, 'title': raw.strip(), 'note': note, 'noteEn': note_en})
    if unknown: return [], 'unknown: ' + ', '.join(unknown)
    if len(segments) < 3: return [], 'too few moves'
    return segments, ''
