package az.healthchecker.domain
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

@Serializable enum class Muscle { LEGS, GLUTES, CHEST, BACK, SHOULDERS, ARMS, CORE }
@Serializable enum class Equipment { NONE, DUMBBELLS }
@Serializable enum class ExerciseKind { STRENGTH, CARDIO, MOBILITY }
@Serializable enum class ExerciseMeasure { REPS, SECONDS, MINUTES }
/** Home exercise; [met] comes from the Compendium activity [metCode], so energy cost is an estimate for the class of activity. */
@Serializable data class Exercise(val id:String,val name:String,val aliases:List<String> = emptyList(),val muscles:List<Muscle>,val equipment:Equipment,val kind:ExerciseKind,val level:Int,val met:Double,val metCode:String,val metSource:String="",val measure:ExerciseMeasure,val secondsPerRep:Double?=null,val cue:String="")
/** Snapshot of a performed exercise: later catalog or body-weight changes do not rewrite history. */
@Serializable data class WorkoutEntry(
    val id:String,val exerciseId:String,val name:String,val kind:ExerciseKind,val muscles:List<Muscle>,
    val sets:Int?=null,val reps:Int?=null,val seconds:Int?=null,val loadKg:Double?=null,val minutes:Double,
    val met:Double,val bodyWeightKg:Double?=null,val kcal:Double?=null,
    val instant:String,val zoneId:String,val localDate:String
)
@Serializable data class TrainingPrefs(val dumbbells:Boolean=false,val level:Int=1)

fun restSeconds(exercise:Exercise):Double = if(exercise.kind==ExerciseKind.STRENGTH)60.0 else 30.0
/** Working time plus rest between sets; MINUTES exercises are timed by the user. */
fun estimateMinutes(exercise:Exercise,sets:Int?,reps:Int?,seconds:Int?):Double? {
    if(sets==null||sets<=0)return null
    val work=when(exercise.measure){
        ExerciseMeasure.REPS->reps?.takeIf{it>0}?.let{r->exercise.secondsPerRep?.let{sets*r*it}}
        ExerciseMeasure.SECONDS->seconds?.takeIf{it>0}?.let{sets*it.toDouble()}
        ExerciseMeasure.MINUTES->null
    }?:return null
    return (work+(sets-1)*restSeconds(exercise))/60
}
/** Gross energy cost: kcal ≈ MET × kg × h (the Compendium's 1 MET = 1 kcal/kg/h approximation). Unknown without body weight. */
fun burnedKcal(met:Double,bodyWeightKg:Double?,minutes:Double):Double? = bodyWeightKg?.let{met*it*minutes/60}

fun makeWorkout(id:String,exercise:Exercise,sets:Int?,reps:Int?,seconds:Int?,loadKg:Double?,minutes:Double,date:String,old:WorkoutEntry?,now:Instant,zone:ZoneId,bodyWeightKg:Double?):WorkoutEntry {
    require(minutes.isFinite()&&minutes>0&&minutes<=600){"Укажите время от 1 до 600 минут"}
    require(sets==null||sets in 1..50){"Подходов может быть от 1 до 50"}
    require(reps==null||reps in 1..1000){"Проверьте число повторений"}
    require(seconds==null||seconds in 1..3600){"Проверьте время в секундах"}
    require(loadKg==null||loadKg.isFinite()&&loadKg>0&&loadKg<=200){"Проверьте вес гантелей"}
    // Like food entries, an edit keeps its day and the body weight it was calculated with.
    val weight=old?.bodyWeightKg?:bodyWeightKg
    return WorkoutEntry(id,exercise.id,exercise.name,exercise.kind,exercise.muscles,sets,reps,seconds,loadKg,minutes,exercise.met,weight,burnedKcal(exercise.met,weight,minutes),
        old?.instant?:now.toString(),old?.zoneId?:zone.id,old?.localDate?:date)
}

data class TrainingDay(val date:String,val workouts:List<WorkoutEntry>,val mark:Boolean?,val burned:NutrientTotal) {
    /** Logged exercises count as a training even without an explicit mark. */
    val trained:Boolean? get() = if(workouts.isNotEmpty())true else mark
}
fun UserSnapshot.training(date:String):TrainingDay {
    LocalDate.parse(date)
    val w=workouts.filter{it.localDate==date}.sortedByDescending{it.instant}
    return TrainingDay(date,w,trainingDays[date],NutrientTotal(w.sumOf{it.kcal?:0.0},w.all{it.kcal!=null}))
}

/** WHO 2020: 150 min moderate aerobic activity a week (vigorous minutes count double) and strength work on ≥2 days. */
const val WHO_AEROBIC_MINUTES=150.0
const val WHO_STRENGTH_DAYS=2
data class WeeklyActivity(val aerobicMinutes:Double,val strengthDays:Int,val trainedDays:Int)
fun weeklyActivity(snapshot:UserSnapshot,endDate:String):WeeklyActivity {
    val end=LocalDate.parse(endDate);val from=end.minusDays(6).toString()
    val week=snapshot.workouts.filter{it.localDate in from..endDate}
    val aerobic=week.filter{it.kind==ExerciseKind.CARDIO}.sumOf{it.minutes*when{it.met>=6->2.0;it.met>=3->1.0;else->0.0}}
    val marked=snapshot.trainingDays.filter{(d,v)->v&&d in from..endDate}.keys
    return WeeklyActivity(aerobic,week.filter{it.kind==ExerciseKind.STRENGTH}.map{it.localDate}.toSet().size,(week.map{it.localDate}+marked).toSet().size)
}

data class Prescription(val sets:Int?=null,val reps:Int?=null,val seconds:Int?=null,val minutes:Double?=null)
fun prescribe(exercise:Exercise,level:Int):Prescription {
    val i=(level.coerceIn(1,3))-1
    return when(exercise.measure){
        ExerciseMeasure.REPS->Prescription(sets=listOf(2,3,4)[i],reps=when{exercise.kind==ExerciseKind.CARDIO->listOf(8,10,12)[i];exercise.equipment==Equipment.DUMBBELLS->listOf(10,12,12)[i];else->listOf(10,12,15)[i]})
        ExerciseMeasure.SECONDS->if(exercise.kind==ExerciseKind.MOBILITY)Prescription(sets=listOf(1,2,2)[i],seconds=30) else Prescription(sets=listOf(2,3,4)[i],seconds=listOf(20,30,45)[i])
        ExerciseMeasure.MINUTES->Prescription(minutes=when(exercise.kind){ExerciseKind.STRENGTH->listOf(20.0,30.0,40.0)[i];else->listOf(10.0,15.0,20.0)[i]})
    }
}
fun Prescription.minutesFor(exercise:Exercise):Double? = minutes?:estimateMinutes(exercise,sets,reps,seconds)

enum class Role { WARMUP, MAIN, CARDIO, COOLDOWN }
data class RecommendedExercise(val exercise:Exercise,val prescription:Prescription,val role:Role)
data class Recommendation(val items:List<RecommendedExercise>,val focus:List<Muscle>,val recovery:Boolean,val weekly:WeeklyActivity)

/**
 * Today's home session: muscle groups not trained today or yesterday, least recently trained first; a warm-up and a
 * stretch; cardio while the weekly aerobic target is not met. The same date and [variant] always give the same plan.
 */
fun recommend(catalog:List<Exercise>,snapshot:UserSnapshot,date:String,prefs:TrainingPrefs,variant:Int=0):Recommendation {
    val day=LocalDate.parse(date);val level=prefs.level.coerceIn(1,3)
    val random=Random(day.toEpochDay()*7919+variant)
    val usable=catalog.filter{it.level<=level&&(it.equipment==Equipment.NONE||prefs.dumbbells)}
    val strength=snapshot.workouts.filter{it.kind==ExerciseKind.STRENGTH&&it.localDate<=date}
    val last=Muscle.entries.associateWith{m->strength.filter{m in it.muscles}.maxOfOrNull{it.localDate}}
    val yesterday=day.minusDays(1).toString()
    val fresh=Muscle.entries.filter{m->last[m].let{it==null||it<yesterday}}.sortedBy{last[it]?:""}
    val recent=snapshot.workouts.filter{it.localDate>=day.minusDays(6).toString()}.map{it.exerciseId}.toSet()
    val weekly=weeklyActivity(snapshot,date)
    fun pick(candidates:List<Exercise>):Exercise? {
        val varied=candidates.filter{it.id !in recent}.ifEmpty{candidates}
        // With dumbbells at hand, prefer loaded variants but keep body-weight ones in the mix.
        val pool=if(prefs.dumbbells&&varied.any{it.equipment==Equipment.DUMBBELLS}&&random.nextInt(10)<6)varied.filter{it.equipment==Equipment.DUMBBELLS} else varied
        return pool.takeIf{it.isNotEmpty()}?.let{it[random.nextInt(it.size)]}
    }
    val items=mutableListOf<RecommendedExercise>()
    fun add(e:Exercise?,role:Role){if(e!=null&&items.none{it.exercise.id==e.id})items+=RecommendedExercise(e,prescribe(e,level),role)}
    val warmups=usable.filter{it.kind==ExerciseKind.MOBILITY&&it.measure==ExerciseMeasure.SECONDS&&it.id in WARMUP_IDS}
    val stretches=usable.filter{it.kind==ExerciseKind.MOBILITY&&it.measure==ExerciseMeasure.SECONDS&&it.id !in WARMUP_IDS}
    val recovery=fresh.size<2
    add(pick(warmups),Role.WARMUP)
    if(recovery){
        add(pick(usable.filter{it.kind==ExerciseKind.MOBILITY&&it.measure==ExerciseMeasure.MINUTES}),Role.MAIN)
        add(pick(usable.filter{it.kind==ExerciseKind.CARDIO&&it.met<5}),Role.CARDIO)
    } else {
        // A group without a suitable exercise at this level/equipment is skipped in favour of the next fresh one.
        for(m in fresh){
            if(items.count{it.role==Role.MAIN}>=3+level)break
            add(pick(usable.filter{e->e.kind==ExerciseKind.STRENGTH&&e.measure!=ExerciseMeasure.MINUTES&&e.muscles.first()==m&&items.none{it.exercise.id==e.id}}),Role.MAIN)
        }
        if(weekly.aerobicMinutes<WHO_AEROBIC_MINUTES)add(pick(usable.filter{it.kind==ExerciseKind.CARDIO&&(level>1||it.met<6)}),Role.CARDIO)
    }
    add(pick(stretches),Role.COOLDOWN)
    val focus=if(recovery)emptyList() else items.filter{it.role==Role.MAIN}.map{it.exercise.muscles.first()}
    return Recommendation(items,focus,recovery,weekly)
}
private val WARMUP_IDS=setOf("arm_circles","hip_circles","cat_cow","worlds_greatest")
