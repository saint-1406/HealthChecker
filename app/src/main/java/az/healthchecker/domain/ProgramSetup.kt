package az.healthchecker.domain
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * "Program setup": a questionnaire about the body, limitations, equipment, goal and the time the user has, and the plan
 * built from it — daily calories, macros, water and a weekly home program from the bundled exercise catalog.
 * Everything here is general guidance for healthy adults, not medical advice; see docs/program-setup.md.
 */

@Serializable enum class FitnessGoal { BULK, GAIN_WEIGHT, MUSCLE, CUT, MAINTAIN, TONE, ENDURANCE }
/** Body problems the program works around. An empty set means "none". */
@Serializable enum class Limitation { KNEE, LOWER_BACK, SHOULDER, WRIST, NECK, HERNIA, HIGH_BLOOD_PRESSURE, PREGNANCY }
/** What the user has at home. An empty set means "nothing": body-weight exercises only. */
@Serializable enum class HomeEquipment { DUMBBELLS, KETTLEBELL, BARBELL, PULLUP_BAR, DIP_BARS, BENCH, BANDS, MAT, JUMP_ROPE }

/** The answers. [appliedOn] is the day the plan from these answers last became the app's targets; null until confirmed. */
@Serializable data class ProgramSetup(
    val sex:String="male",val age:Int=30,val heightCm:Double=170.0,val weightKg:Double=70.0,val targetWeightKg:Double?=null,
    val limitations:Set<Limitation> = emptySet(),val equipment:Set<HomeEquipment> = emptySet(),
    val goal:FitnessGoal=FitnessGoal.MAINTAIN,val activity:Double=1.375,val experience:Int=1,
    val daysPerWeek:Int=3,val minutesPerSession:Int=30,val appliedOn:String?=null)

val ACTIVITY_FACTORS=listOf(1.2,1.375,1.55,1.725,1.9)
const val SETUP_MIN_AGE=14
const val SETUP_MAX_AGE=100

fun validateSetup(s:ProgramSetup) {
    require(s.sex in listOf("male","female")){tr("Укажите пол","Choose your sex")}
    require(s.age in SETUP_MIN_AGE..SETUP_MAX_AGE){tr("Возраст — от $SETUP_MIN_AGE до $SETUP_MAX_AGE лет","Age must be $SETUP_MIN_AGE to $SETUP_MAX_AGE")}
    require(s.heightCm.isFinite()&&s.heightCm in 100.0..250.0){tr("Рост — от 100 до 250 см","Height must be 100 to 250 cm")}
    require(s.weightKg.isFinite()&&s.weightKg in 30.0..300.0){tr("Вес — от 30 до 300 кг","Weight must be 30 to 300 kg")}
    require(s.targetWeightKg==null||s.targetWeightKg.isFinite()&&s.targetWeightKg in 30.0..300.0){tr("Желаемый вес — от 30 до 300 кг","Target weight must be 30 to 300 kg")}
    require(s.activity in ACTIVITY_FACTORS&&s.experience in 1..3&&s.daysPerWeek in 1..7&&s.minutesPerSession in 10..120)
    require(Limitation.PREGNANCY !in s.limitations||s.sex=="female")
    s.appliedOn?.let{LocalDate.parse(it)}
}

/** Starting answers: the saved ones, else what the profile and training settings already know. */
fun initialSetup(saved:ProgramSetup?,profile:Profile?,prefs:TrainingPrefs?):ProgramSetup = saved?:ProgramSetup().let{d->
    if(profile==null)d.copy(equipment=if(prefs?.dumbbells==true)setOf(HomeEquipment.DUMBBELLS) else emptySet(),experience=prefs?.level?:1)
    else d.copy(sex=profile.sex,age=profile.age.coerceIn(SETUP_MIN_AGE,SETUP_MAX_AGE),heightCm=profile.heightCm.coerceIn(100.0,250.0),weightKg=profile.currentWeight.coerceIn(30.0,300.0),
        activity=profile.activity,equipment=if(prefs?.dumbbells==true)setOf(HomeEquipment.DUMBBELLS) else emptySet(),experience=prefs?.level?:1)
}

/** Body parameters for the profile; the first recorded weight stays the starting weight. */
fun ProgramSetup.toProfile(old:Profile?):Profile = Profile(sex,age,heightCm,old?.initialWeight?:weightKg,weightKg,activity)
fun ProgramSetup.toTrainingPrefs():TrainingPrefs = TrainingPrefs(dumbbells=HomeEquipment.DUMBBELLS in equipment||HomeEquipment.KETTLEBELL in equipment,level=experience)

// ---------------------------------------------------------------- nutrition

data class NutritionPlan(
    val bmr:Double?,val maintenance:Double?,val kcal:Double?,val protein:Double?,val fat:Double?,val carbs:Double?,val waterMl:Double,
    /** The goal asked for fewer calories than is safe, so the plan uses the safe minimum. */
    val floorApplied:Boolean,
    /** Expected change of body weight per week, kg (negative = losing); null without a calorie plan. */
    val weeklyChangeKg:Double?,val weeksToTarget:Int?)

fun minimumKcal(sex:String):Double = if(sex=="male")1500.0 else 1200.0
/** Calories against maintenance per goal: a moderate deficit or surplus, not a crash diet. */
fun goalFactor(goal:FitnessGoal):Double = when(goal){
    FitnessGoal.BULK->1.15;FitnessGoal.GAIN_WEIGHT->1.10;FitnessGoal.MUSCLE->1.05
    FitnessGoal.CUT->0.80;FitnessGoal.TONE->0.90;FitnessGoal.MAINTAIN,FitnessGoal.ENDURANCE->1.0
}
/** Protein per kg of (reference) body weight, within the usual 1.2–2.2 g/kg range for active people. */
fun proteinPerKg(goal:FitnessGoal):Double = when(goal){
    FitnessGoal.CUT,FitnessGoal.MUSCLE->2.0;FitnessGoal.BULK->1.8;FitnessGoal.GAIN_WEIGHT,FitnessGoal.TONE->1.6;FitnessGoal.ENDURANCE->1.5;FitnessGoal.MAINTAIN->1.4
}
fun bmi(s:ProgramSetup):Double = s.weightKg/((s.heightCm/100)*(s.heightCm/100))
private fun roundTo(value:Double,step:Double):Double = (value/step).roundToInt()*step

/**
 * Mifflin–St Jeor × activity, adjusted for the goal (at most −750 / +500 kcal), never below the safe minimum: the larger
 * of the basal rate and 1500 (men) / 1200 (women) kcal, unless that is above maintenance. No deficit in pregnancy and no
 * calorie targets under 18. Protein is counted on the weight at BMI 25 when BMI is above 27, so it does not balloon.
 */
fun nutritionPlan(s:ProgramSetup):NutritionPlan {
    validateSetup(s)
    val pregnant=Limitation.PREGNANCY in s.limitations
    val training=s.daysPerWeek*s.minutesPerSession/60.0*500/7
    val water=roundTo((s.weightKg*30+training+if(pregnant)300 else 0).coerceIn(1500.0,4000.0),50.0)
    if(s.age<18)return NutritionPlan(null,null,null,null,null,null,water,false,null,null)
    val bmr=10*s.weightKg+6.25*s.heightCm-5*s.age+if(s.sex=="male")5 else -161
    val maintenance=bmr*s.activity
    val factor=if(pregnant)max(1.0,goalFactor(s.goal)) else goalFactor(s.goal)
    val wanted=(maintenance*factor).coerceIn(maintenance-750,maintenance+500)
    val floor=min(max(minimumKcal(s.sex),bmr),maintenance)
    val kcal=roundTo(max(wanted,floor),10.0)
    val reference=if(bmi(s)>27)25*(s.heightCm/100)*(s.heightCm/100) else s.weightKg
    val protein=roundTo(reference*proteinPerKg(s.goal),5.0)
    val fat=roundTo(max(kcal*0.25/9,0.6*s.weightKg),5.0)
    val carbs=roundTo(((kcal-protein*4-fat*9)/4).coerceAtLeast(0.0),5.0)
    val weekly=(kcal-maintenance)*7/7700
    val weeks=s.targetWeightKg?.let{t->val diff=t-s.weightKg
        if(abs(diff)>=0.5&&abs(weekly)>=0.05&&diff*weekly>0)ceil(abs(diff)/abs(weekly)).toInt() else null}
    return NutritionPlan(bmr,maintenance,kcal,protein,fat,carbs,water,wanted<floor,weekly,weeks)
}

// ---------------------------------------------------------------- exercise safety

/** Jumps, running and other high-impact or all-out moves. */
val HIGH_IMPACT=setOf("jump_squat","jump_lunge","jumping_jacks","skip_imitation","skater_jump","high_knees","plank_jack","squat_thrust","burpee","half_burpee",
    "star_jump","tuck_jump","stairs_fast","dance_high","hiit_moderate","tabata","cross_jacks","seal_jacks","sprint_in_place","squat_jacks","ankle_hops",
    "jogging_outdoor","jog_in_place","butt_kicks","a_skip","bounce_squat","video_cardio_hard","explosive_pushup","squat_and_jack")
private val DEEP_KNEE=setOf("forward_lunge","walking_lunge","curtsy_lunge","split_squat","bulgarian_split_squat","pistol_assisted","side_lunge","db_lunge",
    "db_bulgarian","squat_negative","reverse_lunge_knee_up","stairs","stairs_up_down")
private val LOWER_BACK_LOAD=setOf("situp","v_up","v_sit_in_out","leg_raise","single_leg_raise","flutter_kick","hollow_hold","russian_twist","db_russian_twist",
    "good_morning","db_rdl","db_deadlift","db_swing","db_clean_press","db_bent_row","db_renegade_row","back_extension","superman","swimmer","pilates_roll_up",
    "pilates_hundred","db_side_bend","db_woodchop","seated_forward_fold","db_pullover","bicycle_crunch","star_crunch")
private val SHOULDER_LOAD=setOf("pike_pushup","incline_pike_pushup","hindu_pushup","decline_pushup","wide_pushup","chair_dip","db_shoulder_press","db_arnold_press",
    "db_thruster","db_clean_press","db_pullover","db_overhead_triceps","db_front_raise","explosive_pushup","seal_jacks","plank_pike","pushup_pike","forearm_plank_pike","bear_crawl")
/** Weight on straight hands; wall push-ups and moves on all fours stay, with a tip to use fists or forearms. */
private val WRIST_LOAD=setOf("knee_pushup","incline_pushup","pushup","wide_pushup","diamond_pushup","decline_pushup","pike_pushup","hindu_pushup","incline_pike_pushup",
    "pushup_knee_up","pushup_t_rotation","pushup_pike","explosive_pushup","chair_dip","plank_updown","plank_shoulder_tap","bear_crawl","mountain_climber",
    "cross_mountain_climber","climber_toe_tap","plank_jack","burpee","half_burpee","squat_thrust","inchworm","db_renegade_row","high_plank_toe_in_out","plank_pike",
    "plank_toe_touch","plank_t_rotation","plank_knee_drive","plank_reach","downward_dog","sun_salutation","yoga_power")
private val NECK_LOAD=setOf("crunch","twist_crunch","extended_crunch","knee_elbow_crunch","oblique_crunch","tabletop_crunch","bridge_crunch","star_crunch",
    "bicycle_crunch","situp","v_up","v_sit_in_out","pilates_hundred","pilates_roll_up","db_shrug","explosive_pushup","burpee","tuck_jump")
private val HEAVY_LIFTS=setOf("db_deadlift","db_rdl","db_swing","db_clean_press","db_thruster","db_farmer_hold")
private val HEAD_DOWN=setOf("decline_pushup","pike_pushup","incline_pike_pushup","downward_dog","plank_pike","forearm_plank_pike","pushup_pike","inchworm","yoga_power")
private val PRONE=setOf("superman","ytw_raise","snow_angel","swimmer","cobra_hold","back_extension","prone_t_raise","prone_pullup")
private val SUPINE=setOf("glute_bridge","single_leg_bridge","hip_thrust_sofa","db_glute_bridge","db_floor_press","db_floor_fly","db_skull_crusher","db_pullover",
    "supine_twist","dead_bug","hip_raise","lying_leg_press","lying_knee_rotation","bridge_crunch")
private val BALANCE_RISK=setOf("pistol_assisted","single_leg_rdl","bulgarian_split_squat","db_bulgarian","walking_lunge","curtsy_lunge","db_woodchop","woodchop")
/** Core moves that stay for hernia and pregnancy: no crunching and little pressure on the belly. */
private val GENTLE_CORE=setOf("bird_dog","dead_bug")
/** Dumbbell exercises that work the same with one kettlebell. */
val KETTLEBELL_FRIENDLY=setOf("db_goblet_squat","db_sumo_squat","db_swing","db_deadlift","db_rdl","db_glute_bridge","db_calf_raise","db_one_arm_row",
    "db_shoulder_press","db_farmer_hold","db_russian_twist","db_lunge","db_reverse_lunge","db_step_up","db_side_bend","db_overhead_triceps")
/** Every exercise id the safety and equipment rules name; a test checks that they all exist in the catalog. */
val SAFETY_RULE_IDS:Set<String> get() = HIGH_IMPACT+DEEP_KNEE+LOWER_BACK_LOAD+SHOULDER_LOAD+WRIST_LOAD+NECK_LOAD+HEAVY_LIFTS+HEAD_DOWN+PRONE+SUPINE+BALANCE_RISK+GENTLE_CORE+KETTLEBELL_FRIENDLY

private fun Exercise.coreFirst() = kind==ExerciseKind.STRENGTH&&muscles.firstOrNull()==Muscle.CORE&&id !in GENTLE_CORE
private fun Exercise.highImpact() = id in HIGH_IMPACT||category==ExerciseCategory.HIIT

/** The limitations an exercise is not suitable for. A rough, conservative filter, not a clinical rule. */
fun unsafeFor(e:Exercise):Set<Limitation> = buildSet {
    val impact=e.highImpact()
    if(impact||e.id in DEEP_KNEE)add(Limitation.KNEE)
    if(e.id in LOWER_BACK_LOAD||e.id=="burpee"||e.id=="tuck_jump"||e.id=="jump_squat"||e.id=="jump_lunge"||e.id=="tabata")add(Limitation.LOWER_BACK)
    if(e.id in SHOULDER_LOAD||e.id=="burpee")add(Limitation.SHOULDER)
    if(e.id in WRIST_LOAD)add(Limitation.WRIST)
    if(e.id in NECK_LOAD)add(Limitation.NECK)
    if(e.coreFirst()||e.id in HEAVY_LIFTS||impact)add(Limitation.HERNIA)
    if(e.category==ExerciseCategory.HIIT||e.met>=7||e.kind==ExerciseKind.STRENGTH&&e.measure==ExerciseMeasure.SECONDS||e.id in HEAD_DOWN||e.id in HEAVY_LIFTS)add(Limitation.HIGH_BLOOD_PRESSURE)
    if(impact||e.met>=6||e.coreFirst()||e.id in PRONE||e.id in SUPINE||e.id in BALANCE_RISK||e.id in HEAVY_LIFTS||e.id=="pilates"||e.id=="pilates_hundred"||e.id=="pilates_roll_up")add(Limitation.PREGNANCY)
}

fun equipmentFits(e:Exercise,equipment:Set<HomeEquipment>):Boolean = when(e.equipment){
    Equipment.NONE->true
    Equipment.DUMBBELLS->HomeEquipment.DUMBBELLS in equipment||HomeEquipment.KETTLEBELL in equipment&&e.id in KETTLEBELL_FRIENDLY
}

/** Exercises the program may use: level not above the user's, equipment at hand, nothing unsafe for a limitation. */
fun allowedExercises(catalog:List<Exercise>,s:ProgramSetup):List<Exercise> =
    catalog.filter{e->e.level<=s.experience&&equipmentFits(e,s.equipment)&&unsafeFor(e).none{it in s.limitations}}

// ---------------------------------------------------------------- weekly program

enum class SessionType { FULL_BODY, UPPER, LOWER, CARDIO, MOBILITY }
/** [weekday] 1 = Monday … 7 = Sunday. [minutes] is the estimated length with rests. */
data class PlannedSession(val weekday:Int,val type:SessionType,val items:List<RecommendedExercise>,val minutes:Double)
data class TrainingProgram(val sessions:List<PlannedSession>,val weeklyCardioMinutes:Double)

/** Training days spread over the week with rest days in between where possible. */
val WEEK_LAYOUT:Map<Int,List<Int>> = mapOf(1 to listOf(3),2 to listOf(2,5),3 to listOf(1,3,5),4 to listOf(1,2,4,5),5 to listOf(1,2,3,5,6),6 to listOf(1,2,3,4,5,6),7 to (1..7).toList())

fun sessionTypes(goal:FitnessGoal,days:Int):List<SessionType> {
    val F=SessionType.FULL_BODY;val U=SessionType.UPPER;val L=SessionType.LOWER;val C=SessionType.CARDIO;val M=SessionType.MOBILITY
    val order=when(goal){
        FitnessGoal.BULK,FitnessGoal.GAIN_WEIGHT,FitnessGoal.MUSCLE->if(days<=3)listOf(F,F,F) else listOf(U,L,U,L,C,M,C)
        FitnessGoal.CUT,FitnessGoal.TONE->listOf(F,C,F,C,F,M,C)
        FitnessGoal.MAINTAIN->listOf(F,C,F,M,C,F,M)
        FitnessGoal.ENDURANCE->listOf(C,F,C,C,F,M,C)
    }
    return order.take(days.coerceIn(1,7))
}

private fun focus(type:SessionType):List<Muscle> = when(type){
    SessionType.FULL_BODY->listOf(Muscle.LEGS,Muscle.CHEST,Muscle.BACK,Muscle.GLUTES,Muscle.SHOULDERS,Muscle.CORE,Muscle.ARMS)
    SessionType.UPPER->listOf(Muscle.CHEST,Muscle.BACK,Muscle.SHOULDERS,Muscle.ARMS,Muscle.CORE,Muscle.BACK,Muscle.CHEST)
    SessionType.LOWER->listOf(Muscle.LEGS,Muscle.GLUTES,Muscle.CORE,Muscle.LEGS,Muscle.GLUTES,Muscle.CORE)
    else->emptyList()
}

/** Sets and reps for the goal: heavier and more sets for muscle, more reps for endurance and toning. */
fun prescribeFor(e:Exercise,s:ProgramSetup):Prescription {
    val base=prescribe(e,s.experience)
    if(e.kind!=ExerciseKind.STRENGTH||e.measure!=ExerciseMeasure.REPS)return base
    return when(s.goal){
        FitnessGoal.BULK,FitnessGoal.GAIN_WEIGHT,FitnessGoal.MUSCLE->base.copy(sets=((base.sets?:3)+1).coerceAtMost(5),reps=if(e.equipment==Equipment.DUMBBELLS)listOf(10,10,8)[s.experience-1] else base.reps)
        FitnessGoal.ENDURANCE,FitnessGoal.TONE->base.copy(reps=(base.reps?:12)+3)
        else->base
    }
}
private fun itemMinutes(e:Exercise,p:Prescription):Double = p.minutesFor(e)?:((p.sets?:2)*1.5)

/**
 * A week of home sessions. Each has a warm-up, a main part filled up to the minutes the user has, and a cool-down stretch.
 * Exercises rotate so the same move is not repeated all week; the result depends only on the answers and the catalog.
 */
fun buildProgram(catalog:List<Exercise>,s:ProgramSetup):TrainingProgram {
    validateSetup(s)
    val pool=allowedExercises(catalog,s)
    val order=catalog.withIndex().associate{it.value.id to it.index}
    val used=mutableMapOf<String,Int>()
    val loaded=HomeEquipment.DUMBBELLS in s.equipment||HomeEquipment.KETTLEBELL in s.equipment
    val strengthGoal=s.goal in setOf(FitnessGoal.BULK,FitnessGoal.GAIN_WEIGHT,FitnessGoal.MUSCLE)
    fun pick(candidates:List<Exercise>,exclude:Set<String>):Exercise? = candidates.filter{it.id !in exclude}.minWithOrNull(
        compareBy<Exercise>({used[it.id]?:0},{if(loaded&&strengthGoal&&it.kind==ExerciseKind.STRENGTH&&it.equipment==Equipment.NONE)1 else 0},{abs(it.level-s.experience)},{order[it.id]?:0}))
        ?.also{used[it.id]=(used[it.id]?:0)+1}
    val warmups=pool.filter{it.category==ExerciseCategory.WARMUP&&it.kind==ExerciseKind.MOBILITY&&it.measure==ExerciseMeasure.SECONDS}
    val stretches=pool.filter{it.category==ExerciseCategory.STRETCH&&it.measure==ExerciseMeasure.SECONDS}
    val timedCardio=pool.filter{it.kind==ExerciseKind.CARDIO&&it.measure==ExerciseMeasure.MINUTES&&it.category!=ExerciseCategory.HIIT}
    val intervalCardio=pool.filter{it.kind==ExerciseKind.CARDIO&&it.measure!=ExerciseMeasure.MINUTES}
    val calm=pool.filter{it.kind==ExerciseKind.MOBILITY&&it.measure==ExerciseMeasure.MINUTES}
    val budget=s.minutesPerSession.toDouble()
    val bookends=if(budget>=30)2 else 1
    val types=sessionTypes(s.goal,s.daysPerWeek)
    val days=WEEK_LAYOUT.getValue(types.size)
    val sessions=types.mapIndexed{i,type->
        val items=mutableListOf<RecommendedExercise>()
        fun add(e:Exercise?,role:Role,p:Prescription?=null){if(e!=null)items+=RecommendedExercise(e,p?:prescribeFor(e,s),role)}
        fun ids()=items.map{it.exercise.id}.toSet()
        fun time()=items.sumOf{itemMinutes(it.exercise,it.prescription)}
        repeat(bookends){add(pick(warmups,ids()),Role.WARMUP)}
        val cooldown=bookends*1.0
        when(type){
            SessionType.CARDIO,SessionType.MOBILITY->{
                val source=if(type==SessionType.CARDIO)timedCardio else calm.ifEmpty{timedCardio}
                val left=(budget-time()-cooldown).coerceAtLeast(5.0)
                // Long sessions split into two activities so they do not get monotonous.
                val parts=if(left>=35)2 else 1
                repeat(parts){add(pick(source,ids())?.let{it},if(type==SessionType.CARDIO)Role.CARDIO else Role.MAIN,Prescription(minutes=roundTo(left/parts,5.0).coerceAtLeast(5.0)))}
            }
            else->{
                val muscles=focus(type)
                var k=0;var misses=0
                // Main part: round-robin over the muscle groups until the time is used up; a group with nothing suitable is skipped.
                while(time()+cooldown<budget-(if(s.goal==FitnessGoal.CUT||s.goal==FitnessGoal.TONE)4 else 0)&&items.count{it.role==Role.MAIN}<8&&misses<muscles.size){
                    val m=muscles[k%muscles.size];k++
                    val e=pick(pool.filter{it.kind==ExerciseKind.STRENGTH&&it.measure!=ExerciseMeasure.MINUTES&&it.muscles.firstOrNull()==m},ids())
                    if(e==null){misses++;continue}
                    misses=0;add(e,Role.MAIN)
                }
                // Still short of time with the maximum of exercises: more sets rather than more moves, up to 5 each.
                val target=budget-(if(s.goal==FitnessGoal.CUT||s.goal==FitnessGoal.TONE)4 else 0)
                var grew=true
                while(grew&&time()+cooldown<target*0.95){
                    grew=false
                    for(j in items.indices){
                        val r=items[j];val sets=r.prescription.sets
                        if(r.role!=Role.MAIN||sets==null||sets>=5||r.prescription.minutes!=null||time()+cooldown>=target*0.95)continue
                        items[j]=r.copy(prescription=r.prescription.copy(sets=sets+1));grew=true
                    }
                }
                // Fat loss and toning end the strength part with a short cardio burst when the body allows it.
                if((s.goal==FitnessGoal.CUT||s.goal==FitnessGoal.TONE)&&time()+cooldown<budget)add(pick(intervalCardio,ids()),Role.CARDIO)
            }
        }
        repeat(bookends){add(pick(stretches,ids()),Role.COOLDOWN)}
        PlannedSession(days[i],type,items,time())
    }
    val cardio=sessions.sumOf{p->p.items.filter{it.role==Role.CARDIO}.sumOf{itemMinutes(it.exercise,it.prescription)}}
    return TrainingProgram(sessions,cardio)
}

// ---------------------------------------------------------------- the whole plan

data class ProgramPlan(val setup:ProgramSetup,val nutrition:NutritionPlan,val program:TrainingProgram,val tips:List<String>,val consultDoctor:Boolean)

/** Pregnancy, blood pressure, hernia, older age or high BMI: the plan asks to see a doctor first. */
fun needsDoctor(s:ProgramSetup):Boolean = Limitation.PREGNANCY in s.limitations||Limitation.HIGH_BLOOD_PRESSURE in s.limitations||Limitation.HERNIA in s.limitations||s.age>=65||bmi(s)>=35

fun buildPlan(catalog:List<Exercise>,s:ProgramSetup):ProgramPlan {
    val n=nutritionPlan(s);val p=buildProgram(catalog,s)
    return ProgramPlan(s,n,p,tips(s,n,p),needsDoctor(s))
}

/** Short advice in the current language, most important first. */
fun tips(s:ProgramSetup,n:NutritionPlan,p:TrainingProgram):List<String> = buildList {
    val b=bmi(s)
    if(s.age<18)add(tr("До 18 лет калории не считаем: растущему организму нужен полноценный рацион. Цели питания обсудите с врачом или тренером.","Under 18 the app does not set calories: a growing body needs full meals. Discuss nutrition goals with a doctor or coach."))
    if(Limitation.PREGNANCY in s.limitations)add(tr("Беременность и первые месяцы после родов: без дефицита калорий, прыжков, упражнений лёжа на спине и животе. Нагрузку согласуйте с врачом.","Pregnancy and the first months after birth: no calorie deficit, no jumps, no exercises lying on the back or belly. Agree the load with your doctor."))
    if(n.floorApplied)add(tr("Для вашей цели нужно меньше калорий, чем безопасно. План держит безопасный минимум — вес уйдёт медленнее, зато без вреда.","Your goal would need fewer calories than is safe. The plan keeps a safe minimum: slower, but without harm."))
    when(s.goal){
        FitnessGoal.CUT,FitnessGoal.TONE->add(tr("Худейте плавно: 0,25–0,75 кг в неделю. Белок в каждом приёме пищи сохраняет мышцы.","Lose weight gradually: 0.25–0.75 kg a week. Protein at every meal keeps your muscles."))
        FitnessGoal.BULK,FitnessGoal.GAIN_WEIGHT->add(tr("Набирайте плавно: 0,25–0,5 кг в неделю. Ешьте 4–5 раз в день; если вес не растёт 2 недели, добавьте 150–200 ккал.","Gain gradually: 0.25–0.5 kg a week. Eat 4–5 times a day; if the weight has not moved for 2 weeks, add 150–200 kcal."))
        FitnessGoal.MUSCLE->add(tr("Мышцы растут от прогрессии: каждую неделю добавляйте повтор или подход, пока техника чистая.","Muscle grows with progression: add a rep or a set each week while your form stays clean."))
        FitnessGoal.ENDURANCE->add(tr("Выносливость растёт от регулярности: большую часть кардио делайте в темпе, при котором можно говорить.","Endurance grows with regularity: do most cardio at a pace where you can still talk."))
        FitnessGoal.MAINTAIN->add(tr("Для поддержания веса взвешивайтесь раз в неделю; изменение больше 1 кг — повод пересчитать план.","To maintain, weigh yourself weekly; a change of over 1 kg is a reason to recalculate."))
    }
    if(b<18.5&&(s.goal==FitnessGoal.CUT||s.goal==FitnessGoal.TONE))add(tr("Индекс массы тела ниже 18,5: худеть не рекомендуется. Лучше цель «поддерживать» или «набрать мышцы».","BMI is below 18.5: losing weight is not advised. Choose “maintain” or “build muscle” instead."))
    if(b>=30&&(s.goal==FitnessGoal.BULK||s.goal==FitnessGoal.GAIN_WEIGHT))add(tr("Индекс массы тела 30 и выше: для силы и формы лучше цель «нарастить мышцы» без большого профицита.","BMI is 30 or more: for strength and shape, “build muscle” without a big surplus suits better."))
    s.targetWeightKg?.let{t->
        val wantsLess=t<s.weightKg-0.4;val wantsMore=t>s.weightKg+0.4
        if(wantsLess&&s.goal in setOf(FitnessGoal.BULK,FitnessGoal.GAIN_WEIGHT,FitnessGoal.MUSCLE)||wantsMore&&s.goal in setOf(FitnessGoal.CUT,FitnessGoal.TONE))
            add(tr("Желаемый вес не совпадает с целью — проверьте ответы.","The target weight does not match the goal — check your answers."))
        n.weeksToTarget?.let{w->add(tr("До желаемого веса примерно $w нед. при этом плане.","About $w weeks to the target weight with this plan."))}
    }
    if(Limitation.KNEE in s.limitations)add(tr("Колени: без прыжков и глубоких выпадов, приседайте в безболезненной амплитуде.","Knees: no jumps or deep lunges; squat only as deep as is pain-free."))
    if(Limitation.LOWER_BACK in s.limitations)add(tr("Поясница: держите спину нейтральной, без скручиваний с весом и подъёмов ног лёжа.","Lower back: keep the spine neutral; no loaded twists or lying leg raises."))
    if(Limitation.SHOULDER in s.limitations)add(tr("Плечи: без жимов над головой и отжиманий на брусьях; работайте в комфортной амплитуде.","Shoulders: no overhead presses or dips; work in a comfortable range."))
    if(Limitation.WRIST in s.limitations)add(tr("Запястья: упор на кулаки или предплечья, гантели держите прямым хватом.","Wrists: rest on fists or forearms and hold dumbbells with a straight wrist."))
    if(Limitation.NECK in s.limitations)add(tr("Шея: без скручиваний корпуса с отрывом головы; голова на одной линии со спиной.","Neck: no crunches that lift the head; keep the head in line with the spine."))
    if(Limitation.HERNIA in s.limitations)add(tr("Грыжа: не задерживайте дыхание и не поднимайте тяжёлое; бандаж — по совету врача.","Hernia: do not hold your breath or lift heavy; a support belt only on a doctor's advice."))
    if(Limitation.HIGH_BLOOD_PRESSURE in s.limitations)add(tr("Давление: умеренный темп, дышите ровно, без статики и рывков. При давлении выше 160/100 — сначала к врачу.","Blood pressure: moderate pace, breathe evenly, no long holds or bursts. Above 160/100, see a doctor first."))
    if(s.limitations.isNotEmpty())add(tr("Боль во время упражнения — сигнал остановиться. Если боль не проходит, обратитесь к врачу.","Pain during an exercise is a signal to stop. If it persists, see a doctor."))
    val eq=s.equipment
    if(HomeEquipment.PULLUP_BAR in eq&&Limitation.SHOULDER !in s.limitations)add(tr("Турник: в дни спины добавьте 2–3 подхода подтягиваний или виса.","Pull-up bar: on back days add 2–3 sets of pull-ups or hangs."))
    if(HomeEquipment.DIP_BARS in eq&&Limitation.SHOULDER !in s.limitations&&Limitation.WRIST !in s.limitations)add(tr("Брусья: отжимания на брусьях подойдут в дни груди и рук.","Dip bars: dips fit chest and arm days."))
    if(HomeEquipment.BANDS in eq)add(tr("Резинки: тяга к поясу и разведения рук — хорошее дополнение в дни спины и плеч.","Bands: rows and pull-aparts are a good addition on back and shoulder days."))
    if(HomeEquipment.BARBELL in eq)add(tr("Штанга: приседания и тяги со штангой — только с освоенной техникой. Программа строится из упражнений с гантелями и весом тела.","Barbell: barbell squats and pulls only with practised form. The program uses dumbbell and body-weight exercises."))
    if(HomeEquipment.BENCH in eq)add(tr("Скамья: подходит для зашагиваний, отжиманий с упора и жима гантелей.","Bench: use it for step-ups, incline push-ups and dumbbell presses."))
    if(HomeEquipment.JUMP_ROPE in eq&&Limitation.KNEE !in s.limitations&&Limitation.PREGNANCY !in s.limitations)add(tr("Скакалка заменит «прыжки без скакалки» в кардио.","A jump rope can replace “air jump rope” in cardio."))
    if(HomeEquipment.KETTLEBELL in eq&&HomeEquipment.DUMBBELLS !in eq)add(tr("Гиря: упражнения с гантелью из программы, которые подходят для гири, выполняйте с ней.","Kettlebell: the program's dumbbell moves that suit a kettlebell can be done with it."))
    if(s.experience==1&&s.daysPerWeek>=5)add(tr("Новичку хватает 3–4 тренировок в неделю; оставьте хотя бы один день отдыха.","Beginners do well with 3–4 sessions a week; keep at least one rest day."))
    if(p.weeklyCardioMinutes<WHO_AEROBIC_MINUTES&&Limitation.PREGNANCY !in s.limitations)add(tr("ВОЗ советует 150 минут умеренной активности в неделю: добавьте прогулки быстрым шагом.","WHO advises 150 minutes of moderate activity a week: add brisk walks."))
    add(tr("Пейте воду равномерно в течение дня, больше — в жару и в дни тренировок.","Drink water evenly through the day, more in hot weather and on training days."))
}

/** Shown with every plan. */
fun setupDisclaimer():String = tr(
    "Это общие рекомендации для здоровых взрослых, а не медицинская консультация. При беременности, болезнях, травмах или приёме лекарств сначала посоветуйтесь с врачом.",
    "This is general guidance for healthy adults, not medical advice. If you are pregnant, ill, injured or on medication, talk to a doctor first.")
