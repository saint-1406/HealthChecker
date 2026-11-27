"""Workout videos of three more channels: Oliver Sjostrom, growingannanas (Anna Engelschall) and You1stLondon.

creators_channels.json keeps, for every workout video of the three channels, what its public watch page shows: id, title,
length, the chapters ("author" when the description lists them, "auto" when YouTube generated them, "none") and the
description without links. Read on 2026-11-27; nothing was downloaded from the videos (YouTube asks to sign in for the
player, so the videos themselves were not watched) and no transcripts were used. Vlogs, recipes, Q&A and challenges
without a workout are left out of the snapshot.

Each video gets the most detailed timeline its page allows:

- "list": the description lists the moves in order with their interval ("▸ Workout 40 sec on, 20 sec off" then one move per
  line, as growingannanas writes it). Every move becomes a segment of its interval; the work time is the "on" part. The
  author gives no timestamps for single moves, so a move's start in the video is an estimate: the blocks are laid one
  after another from the block's own timestamp when the header has one ("12:30 - 24:30 Round 2 …"), otherwise from the
  end of the intro.
- "sections": the chapters name parts of the workout ("Warm Up", "Circuit 1 · Back & Delts", "Water Break", "Core").
  Each part becomes a segment of its kind (warm-up, the video's main kind, abs and core, stretching); intros, water
  breaks and outros are gaps; a chapter that names a single move ("Skater jumps") is that move.
- "whole": nothing on the page names the parts, so the video is one session of the kind its title names.

Moves are matched to the catalog by their words (RULES, first match wins; for "A + B" the first move leads); in a
dumbbell video the dumbbell version of a move is taken. A move nothing matches keeps the author's name and counts as its
block's kind (warm-up, stretching or the video's main kind).
"""
import json, re
from pathlib import Path

SNAPSHOT = Path(__file__).with_name('creators_channels.json')
CHANNELS = {'OliverSjostrom': 'Oliver Sjostrom', 'growingannanas': 'growingannanas', 'you1stlondon': 'You1stLondon'}

# ── The kind of a whole video, from its title; first match wins. ──
KIND = [
    ('stretch_session', r'stretch|mobility|cool ?down|flexib|recovery'),
    ('yoga_vinyasa', r'\byoga\b'),
    ('pilates', r'pilates|barre'),
    ('warmup_session', r'^(?!.*workout).*warm ?up|\bwarm ?up routine'),
    ('walk_brisk', r'\bwalk(ing)?\b'),
    ('dance_high', r'\bdance\b'),
    ('tabata', r'tabata'),
    ('db_circuit', r'(dumbbell|weights?|kettlebell|\bdb\b).*(hiit|cardio|burn|sweat|circuit|hyrox|emom|amrap|fat)|(hiit|cardio|burn|sweat|circuit|hyrox|emom|amrap|fat).*(dumbbell|weights?|kettlebell|\bdb\b)'),
    ('db_strength_session', r'dumbbell|weights?|kettlebell|\bdb\b'),
    ('hiit_moderate', r'hiit|cardio|hyrox|fat ?(burn|loss)|emom|amrap|metcon|conditioning|jump'),
    ('core_session', r'\b(abs?|core|six ?pack|6 ?pack|belly|oblique|plank)\b'),
    ('bw_strength_session', r'glute|booty|legs?|lower body|upper body|arms?|chest|back|shoulders?|calisthenics|strength|muscle|push ?ups?|tone|sculpt'),
    ('circuit_bw', r'.'),
]
def kind(title):
    for ident, rx in KIND:
        if re.search(rx, title, re.I): return ident
    return 'circuit_bw'

DUMBBELL = re.compile(r'dumbbell|weights?|kettlebell|\bdb\b', re.I)
EQUIPMENT = re.compile(r'equipment[^\n]*(dumbbell|weights?|kettlebell|\d+\s*kg)', re.I)

def with_dumbbells(v):
    """The title says so, or the description's equipment line names dumbbells or weights."""
    return bool(DUMBBELL.search(v['title']) or EQUIPMENT.search(v['description']))

TAGS = [('abs', r'\b(abs?|core|six ?pack|belly|oblique|plank)\b'), ('cardio', r'\b(cardio|hiit|tabata|hyrox|fat ?(burn|loss)|burn|sweat)\b'),
        ('full', r'\bfull ?body\b'), ('legs', r'\b(legs?|glutes?|booty|butt|thighs?|lower body)\b'), ('upper', r'\b(upper body|arms?|shoulders?|chest|back|biceps?|triceps?)\b'),
        ('dumbbells', r'\b(dumbbells?|weights?|kettlebell)\b'), ('nojump', r'\b(no jump\w*|low impact|stretch\w*|yoga|pilates|standing abs|walk\w*)\b'),
        ('warmup', r'\b(warm ?up|mobility|cool ?down)\b'), ('yoga', r'\byoga\b'), ('pilates', r'\bpilates\b'), ('stretch', r'\b(stretch\w*|mobility|flexib\w*)\b')]

# ── Moves: (pattern, bodyweight id, dumbbell id or None). Checked against the lead move, then the whole name. ──
STRETCH_RULES = [
    (r'forward fold|toe touch stretch|hang', 'standing_forward_bend'), (r'child', 'childs_pose'), (r'quad', 'quad_stretch'), (r'hamstring', 'hamstring_stretch'),
    (r'butterfly', 'butterfly_stretch'), (r'tricep', 'triceps_stretch'), (r'chest|upper body opener|opener|shoulder', 'chest_stretch'),
    (r'thread the needle', 'thread_the_needle'), (r'puppy', 'puppy_pose'), (r'cobra|sphinx', 'cobra_pose'), (r'twist', 'supine_twist'),
    (r'deep lunge|low lunge|lunge|runner|hip flexor', 'low_lunge'), (r'glute|figure|pigeon', 'figure_four'), (r'wide leg|straddle|pancake', 'straddle_stretch'),
    (r'calf', 'calf_stretch'), (r'neck', 'neck_stretch'), (r'side|reach', 'side_stretch'), (r'knee hug|knees to chest|hug', 'knees_to_chest'),
    (r'down ?dog|downward', 'downward_dog'), (r'cat|cow', 'cat_cow'), (r'squat', 'yogi_squat'), (r'lizard', 'lizard_pose'),
]
RULES = [
    # jumps and cardio
    (r'superwoman|superman', 'superman', None), (r'commando', 'plank_updown', None), (r'cat cow', 'cat_cow', None),
    (r'standing stars?|^stars?$|x jump', 'star_jump', None), (r'surfer', 'half_burpee', None), (r'bunny hop|warm up hop|pogo|ankle hop', 'ankle_hops', None),
    (r'kick your butt', 'butt_kicks', None), (r'in ?(in )?out (out )?jumps?', 'squat_jacks', None), (r'wrist (circle|rock)', 'wrist_ankle_circles', None),
    (r'elbow circle', 'arm_circles', None), (r'step back|backward step', 'reverse_lunge', None), (r'pli[eé]', 'sumo_squat', 'db_sumo_squat'),
    (r'hip lift', 'glute_bridge', None), (r'legs? (in|out)|tuck in', 'v_sit_in_out', None), (r'pull ?down', 'superman', None), (r'knee hug', 'march_in_place', None),
    (r'reach up|reach.*down', 'squat_reach', None),
    (r'seal jack', 'seal_jacks', None), (r'squat jack|in ?(&|and)? ?out squat', 'squat_jacks', None), (r'plank jack', 'plank_jack', None),
    (r'star jump|star jack', 'star_jump', None), (r'cross jack|criss ?cross', 'cross_jacks', None), (r'jack', 'jumping_jacks', None),
    (r'half burpee|half stand up burpee', 'half_burpee', None), (r'burpee', 'burpee', None),
    (r'(cross|diagonal|spider|twist).*climber|climber.*(cross|twist)', 'cross_mountain_climber', None), (r'climber', 'mountain_climber', None),
    (r'high knee', 'high_knees', None), (r'plank butt kick|butt kick', 'butt_kicks', None), (r'rope|skip', 'skip_imitation', None),
    (r'skater|skaters', 'skater_jump', None), (r'tuck jump', 'tuck_jump', None),
    (r'squat jump|jump squat|pop squat|squat hop', 'jump_squat', None), (r'lunge jump|jump lunge|scissor jump|switch lunge', 'jump_lunge', None),
    (r'fast feet|sprint|run\b|running|jog', 'sprint_in_place', None), (r'shuffle', 'lateral_shuffle', None), (r'march', 'march_in_place', None),
    (r'step touch|side step(?!.*squat)|side to side tap', 'step_touch', None),
    (r'punch|hook|jab|uppercut|boxing|slip', 'video_cardio_hard', None), (r'a ?skip', 'a_skip', None),
    # legs and glutes
    (r'thruster', 'squat_reach', 'db_thruster'), (r'swing', 'db_swing', 'db_swing'), (r'clean', 'squat', 'db_clean_press'),
    (r'goblet', 'squat', 'db_goblet_squat'), (r'sumo', 'sumo_squat', 'db_sumo_squat'), (r'prisoner squat', 'prisoner_squat', None),
    (r'wall sit|squat hold|chair pose', 'wall_sit', None), (r'side squat|lateral squat|squat.*side step|side step squat|squat walk', 'side_step_squat', None),
    (r'bulgarian', 'bulgarian_split_squat', 'db_bulgarian'), (r'split squat', 'split_squat', 'db_lunge'),
    (r'squat', 'squat', 'db_squat'), (r'curtsy', 'curtsy_lunge', None),
    (r'reverse lunge.*(knee|kick)|lunge.*knee (drive|up)', 'reverse_lunge_knee_up', None), (r'reverse lunge|back lunge', 'reverse_lunge', 'db_reverse_lunge'),
    (r'bulgarian', 'bulgarian_split_squat', 'db_bulgarian'), (r'split squat', 'split_squat', 'db_lunge'), (r'side lunge|lateral lunge|cossack', 'side_lunge', None),
    (r'walking lunge', 'walking_lunge', None), (r'lunge', 'forward_lunge', 'db_lunge'),
    (r'single leg (rdl|deadlift)|1 leg (rdl|deadlift)|one leg (rdl|deadlift)', 'single_leg_rdl', 'single_leg_rdl'), (r'deadlift|rdl', 'good_morning', 'db_rdl'),
    (r'good morning', 'good_morning', None),
    (r'(one|1|single) leg (glute )?bridge', 'single_leg_bridge', None), (r'hip thrust|glute bridge|bridge', 'glute_bridge', 'db_glute_bridge'),
    (r'tricep kickback|kickback.*tricep', 'chair_dip', 'db_kickback'), (r'donkey|kickback|glute kick|kick back', 'donkey_kick', None),
    (r'fire hydrant', 'fire_hydrant', None), (r'clam|side leg|leg lift|side lying', 'side_leg_raise', None),
    (r'calf|heel raise', 'calf_raise', 'db_calf_raise'), (r'step up', 'step_up', 'db_step_up'),
    # upper body
    (r'walk ?outs?|inchworm', 'inchworm', None), (r'knee push|push ?up.*knee|knee.*push ?up', 'knee_pushup', None),
    (r'wide push', 'wide_pushup', None), (r'diamond|tricep push|close grip push', 'diamond_pushup', None), (r'pike', 'pike_pushup', None),
    (r'cobra push|hindu|dive bomber', 'hindu_pushup', None), (r'push ?up|press ?up', 'pushup', None),
    (r'renegade|plank row', 'plank_shoulder_tap', 'db_renegade_row'), (r'(one|1|single) arm row', 'doorframe_row', 'db_one_arm_row'),
    (r'row', 'doorframe_row', 'db_bent_row'), (r'hammer', 'arm_circles', 'db_hammer_curl'), (r'curl', 'arm_circles', 'db_biceps_curl'),
    (r'arnold', 'pike_pushup', 'db_arnold_press'), (r'chest press|floor press|bench press', 'pushup', 'db_floor_press'),
    (r'press', 'pike_pushup', 'db_shoulder_press'), (r'lateral raise|side raise', 'arm_raises', 'db_lateral_raise'), (r'front raise', 'arm_raises', 'db_front_raise'),
    (r'rear|reverse fly|bent over fly', 'ytw_raise', 'db_rear_fly'), (r'fly|flye', 'snow_angel', 'db_floor_fly'),
    (r'(chair|tricep|bench) dip|^dips?$', 'chair_dip', 'chair_dip'),
    (r'skull', 'diamond_pushup', 'db_skull_crusher'), (r'tricep|triceps|overhead extension', 'chair_dip', 'db_overhead_triceps'), (r'pullover|pull over', 'superman', 'db_pullover'),
    (r'shrug', 'shoulder_rolls', 'db_shrug'), (r'arm circle', 'arm_circles', None), (r'snow angel', 'snow_angel', None),
    (r'superman|swimmer|back extension', 'superman', None), (r'[ytw] raise|ytw', 'ytw_raise', None), (r'bear', 'bear_crawl', None),
    # core
    (r'bicycle', 'bicycle_crunch', None), (r'reverse crunch', 'reverse_crunch', None), (r'(oblique|side) crunch', 'oblique_crunch', None),
    (r'standing.*crunch|standing knee', 'standing_knee_crunch', None), (r'crunch', 'crunch', None), (r'sit ?up', 'situp', None),
    (r'v ?up|tuck up|jack ?knife|jackknife', 'v_up', None), (r'russian|body twist|seated twist|twist', 'russian_twist', 'db_russian_twist'),
    (r'heel (tap|touch)', 'heel_touch', None), (r'shoulder tap', 'plank_shoulder_tap', None), (r'up ?down plank|plank up ?down', 'plank_updown', None),
    (r'side plank dip|side plank hip', 'side_plank_dip', None), (r'(low )?plank dip|hip dip', 'plank_hip_twist', None),
    (r'side plank|star plank', 'side_plank', None), (r'plank', 'plank', None), (r'hollow|ab hold|boat|v ?sit', 'hollow_hold', None),
    (r'dead ?bug', 'dead_bug', None), (r'bird ?dog', 'bird_dog', None), (r'flutter|scissor', 'flutter_kick', None),
    (r'leg raise|leg drop|leg lower|legs? up', 'leg_raise', None), (r'toe tap|toe touch|toe reach', 'front_toe_touch', None),
    (r'wood ?chop', 'woodchop', 'db_woodchop'), (r'side bend', 'standing_side_bend', 'db_side_bend'), (r'hundred', 'pilates_hundred', None),
    (r'roll ?up', 'pilates_roll_up', None), (r'knee (down|tap)', 'plank', None),
]
COMPILED = [(re.compile(p), bw, db) for p, bw, db in RULES]
STRETCH_COMPILED = [(re.compile(p), i) for p, i in STRETCH_RULES]

def clean(name):
    """'Quad Stretch Right Leg' -> ('quad stretch', side note)."""
    s = name.lower().replace('’', "'").replace('‘', "'")
    s = re.sub(r'[^\w+&\' ]', ' ', s)
    side = ''
    if re.search(r'\b(right|r)\b', s): side = 'right'
    if re.search(r'\b(left|l)\b', s): side = 'left'
    s = re.sub(r'\b(right|left|r|l|side|leg|arm|to the)\b(?=\s*$)', ' ', s)
    s = re.sub(r'\b(to the )?(right|left)\b', ' ', s)
    return re.sub(r'\s+', ' ', s).strip(), side

def move(name, dumbbells, block):
    """Catalog id for one listed move, or None. In a cool-down block the stretches are tried first."""
    s, _ = clean(name)
    lead = s.split('+')[0].strip()
    if block == 'cool':
        for rx, ident in STRETCH_COMPILED:
            if rx.search(lead) or rx.search(s): return ident
    for text in (lead, s):
        for rx, bw, db in COMPILED:
            if rx.search(text): return (db or bw) if dumbbells else bw
    if re.search(r'stretch|pose|opener|fold|breath|inhale|exhale', s):
        for rx, ident in STRETCH_COMPILED:
            if rx.search(s): return ident
        return 'stretch_session'
    return None

SIDE_NOTE = {'right': ('правая сторона', 'right side'), 'left': ('левая сторона', 'left side'), '': ('', '')}

# ── "list": interval headers and the moves under them. ──
HEADER = re.compile(r'(?:(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})\s*)?(.{0,40}?)\b(\d{1,3})\s*(?:sec|s|seconds)\.?\s*(?:on|work)\b(?:\s*[,/&+]?\s*(?:(\d{1,3})\s*(?:sec|s|seconds)\.?\s*(?:off|rest)|no rest))?', re.I)
STOP = re.compile(r'please remember|d i s c|http|join my|subscribe|music|license|sign up|newsletter|discount|amazon|my fitness|gear|camera|instagram|facebook|contact|'
                  r'^♡|^💖|^👉|^#|^»|^✉|@|equipment|^time:|^level|^muscles|^intensity|^duration|^focus|^calorie', re.I)

def seconds(stamp):
    m, s = stamp.split(':'); return int(m) * 60 + int(s)

def blocks(description):
    """[(label, start or None, on, off, [move names])] in the order the description lists them."""
    out, current = [], None
    for raw in description.split('\n'):
        line = raw.strip().lstrip('▸•-–*').strip()
        h = HEADER.search(line) if len(line) < 90 else None
        if h and not re.search(r'\bx\s*\d|\d+\s*(reps|rounds? of)\b', line, re.I):
            start = seconds(h.group(1)) if h.group(1) else None
            current = [h.group(3).strip(' :-–🔥'), start, int(h.group(4)), int(h.group(5) or 0), []]
            out.append(current); continue
        if current is None: continue
        if not line:
            if current[4]: current = None if len(current[4]) >= 3 else current
            continue
        if re.search(r':$|\bsec\b|challenge|^with weights$|^warm ?up$|exercises', line, re.I): continue
        if STOP.search(line) or len(line) > 50 or raw.strip().startswith('▸'):
            if current[4]: current = None
            continue
        current[4].append(line)
    return [tuple(b) for b in out if b[4]]

def block_kind(label):
    l = label.lower()
    if re.search(r'cool|stretch', l): return 'cool'
    if re.search(r'warm', l): return 'warm'
    return 'main'

INTRO = re.compile(r'^(intro|introduction|welcome|workout introduction)', re.I)

def list_timeline(v, catalog):
    found = blocks(v['description'])
    if not found or sum(len(b[4]) for b in found) < 5: return []
    dumbbells = with_dumbbells(v)
    main = kind(v['title'])
    t = 0
    if v['chapters'] and INTRO.match(v['chapters'][0][1]) and len(v['chapters']) > 1: t = round(v['chapters'][1][0])
    segments = []
    for label, start, on, off, names in found:
        if start is not None and start >= t: t = start
        bk = block_kind(label)
        section = {'warm': ('Разминка', 'Warm-up'), 'cool': ('Заминка', 'Cool-down'), 'main': (label or 'Тренировка', label or 'Workout')}[bk]
        for name in names:
            # Warm-ups and cool-downs are done without the weights unless the move names them.
            ident = move(name, (dumbbells and bk == 'main') or bool(DUMBBELL.search(name)), bk) or {'warm': 'warmup_session', 'cool': 'stretch_session', 'main': main}[bk]
            assert ident in catalog, (v['id'], name, ident)
            note, note_en = SIDE_NOTE[clean(name)[1]]
            seg = {'start': t, 'end': t + on + off, 'exerciseId': ident, 'title': name, 'section': section[0], 'sectionEn': section[1], 'note': note, 'noteEn': note_en}
            if off: seg['work'] = on
            segments.append(seg)
            t += on + off
    if segments[-1]['end'] > v['lengthSeconds'] + 30: return []   # the list does not fit the video: something was misread
    if segments[-1]['end'] > v['lengthSeconds']:
        over = segments[-1]['end'] - v['lengthSeconds']
        segments[-1]['end'] -= over
        if segments[-1]['end'] - segments[-1]['start'] < 10: segments.pop()
        elif 'work' in segments[-1]: segments[-1]['work'] = min(segments[-1]['work'], segments[-1]['end'] - segments[-1]['start'])
    return segments

# ── "sections": chapters naming parts of the workout. ──
GAP = re.compile(r'intro|outro|water break|^rest|rest (and|&) recovery|rest break|break$|conclusion|wrap|app|explanation|training systems|thank|end of|'
                 r'workout completion|completion|^welcome|^done|equipment needed|^start|^ready', re.I)

def section_kind(label, main, dumbbells):
    l = label.lower()
    if GAP.search(l): return None
    if re.search(r'cool|stretch|recovery', l): return 'stretch_session'
    if re.search(r'warm', l): return 'warmup_session'
    if re.search(r'\b(abs?|core|abdominal|oblique|six ?pack)\b', l): return 'core_session'
    if re.search(r'finisher|metabolic|burnout|burn out|hiit|cardio|tabata|interval|sprint', l): return 'tabata' if 'tabata' in l else 'hiit_moderate'
    if len(l.split()) <= 4:
        m = move(label, dumbbells, 'main')
        if m and m not in ('stretch_session',): return m
    return main

def sections_timeline(v, catalog):
    chapters = sorted((round(s), t) for s, t in v['chapters'])
    if len(chapters) < 3: return []
    ends = [b for b, _ in chapters[1:]] + [v['lengthSeconds']]
    dumbbells = with_dumbbells(v); main = kind(v['title'])
    segments = []
    for (start, label), end in zip(chapters, ends):
        if end - start < 10: continue
        ident = section_kind(label, main, dumbbells)
        if ident is None: continue
        assert ident in catalog, (v['id'], label, ident)
        segments.append({'start': start, 'end': end, 'exerciseId': ident, 'title': label.strip()})
    return segments if segments else []

ABOUT = {
    'list': ('Разметка — по списку упражнений и интервалам из описания автора. Время работы и отдыха точное; где именно в видео начинается каждое упражнение — оценка, поэтому переход к упражнению может немного промахнуться.',
             "The timeline follows the author's list of moves and intervals in the description. Work and rest times are exact; where each move starts in the video is an estimate, so jumping to a move may land a little off."),
    'sections_author': ('Главы автора называют части тренировки, а не отдельные упражнения: каждая часть записывается занятием своего типа (разминка, основная часть, пресс, растяжка), перерывы не считаются.',
                        "The author's chapters name parts of the workout, not single moves: each part is logged as a session of its kind (warm-up, main part, abs, stretching), breaks are not counted."),
    'sections_auto': ('Главы созданы YouTube автоматически и называют части тренировки: каждая часть записывается занятием своего типа, перерывы и вступление не считаются. Названия глав могут быть неточными.',
                      'YouTube generated the chapters, and they name parts of the workout: each part is logged as a session of its kind, breaks and the intro are not counted. Chapter names may be imprecise.'),
}
WHOLE = {'stretch_session': ('Растяжка: всё занятие', 'Stretching: the whole session'), 'yoga_vinyasa': ('Йога: всё занятие', 'Yoga: the whole session'),
         'pilates': ('Пилатес: вся тренировка', 'Pilates: the whole workout'), 'warmup_session': ('Разминка: вся', 'Warm-up: the whole video'),
         'walk_brisk': ('Ходьба: вся тренировка', 'Walking: the whole workout'), 'dance_high': ('Танцевальное кардио: всё занятие', 'Dance cardio: the whole session'),
         'tabata': ('Табата: вся тренировка', 'Tabata: the whole workout'), 'db_circuit': ('Круговая с гантелями: вся тренировка', 'Dumbbell circuit: the whole workout'),
         'db_strength_session': ('Силовая с гантелями: вся тренировка', 'Dumbbell strength: the whole workout'),
         'hiit_moderate': ('Интервальная тренировка целиком', 'Interval workout: the whole video'), 'core_session': ('Пресс и кор: вся тренировка', 'Abs and core: the whole workout'),
         'bw_strength_session': ('Силовая с весом тела: вся тренировка', 'Bodyweight strength: the whole workout'), 'circuit_bw': ('Тренировка целиком', 'Workout: the whole video')}

def videos(catalog, snapshot=SNAPSHOT):
    """Library entries of the three channels; returns (videos, {timing: count})."""
    data = json.loads(Path(snapshot).read_text(encoding='utf-8'))
    out, counts = [], {}
    for handle, name in CHANNELS.items():
        for v in data[handle]:
            entry = {'id': 'yt:' + v['id'], 'title': v['title'], 'channel': name, 'youtubeId': v['id'], 'durationSec': v['lengthSeconds'], 'embeddable': False,
                     'tags': [t for t, rx in TAGS if re.search(rx, v['title'], re.I)]}
            segs = list_timeline(v, catalog)
            if segs:
                timing, about = 'list', ABOUT['list']
            else:
                segs = sections_timeline(v, catalog) if v['chapterSource'] != 'none' else []
                if segs: timing, about = 'sections', ABOUT['sections_' + v['chapterSource']]
            if not segs:
                ident = kind(v['title']); title, title_en = WHOLE[ident]
                assert ident in catalog, (v['id'], ident)
                timing = 'whole'
                about = ('Ни глав, ни списка упражнений у видео нет, поэтому оно записывается одним занятием на всю длину. ' + title + '.',
                         'The video has neither chapters nor a list of moves, so it is logged as one session for its whole length. ' + title_en + '.')
                segs = [{'start': 0, 'end': v['lengthSeconds'], 'exerciseId': ident, 'title': title_en}]
            if with_dumbbells(v) and 'dumbbells' not in entry['tags']: entry['tags'].append('dumbbells')
            counts[timing] = counts.get(timing, 0) + 1
            out.append(entry | {'about': about[0], 'aboutEn': about[1], 'segments': segs, 'timing': timing})
    return out, counts
