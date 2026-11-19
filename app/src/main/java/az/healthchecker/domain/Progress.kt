package az.healthchecker.domain
import kotlin.math.roundToInt

/**
 * Strength progress per exercise, from the workouts already logged: what was done last time, personal records, an
 * estimated one-rep max for dumbbell work and the next small step (double progression: a rep more, then a heavier
 * dumbbell). Nothing is stored — everything is worked out from the diary, so edits and deletions are always reflected.
 */
enum class RecordKind { LOAD, E1RM, REPS, TOTAL_REPS, SECONDS, MINUTES }

/** Every logged entry of the exercise, oldest first (same day: in the order they were logged). */
fun UserSnapshot.exerciseHistory(exerciseId:String):List<WorkoutEntry> =
    workouts.filter{it.exerciseId==exerciseId}.sortedWith(compareBy({it.localDate},{it.instant}))

/**
 * Epley's estimate of the weight that could be lifted once: load × (1 + reps / 30). Above 12 reps the estimate drifts
 * too far, so it is left out there.
 */
fun epley(loadKg:Double?,reps:Int?):Double? {
    if(loadKg==null||reps==null||reps<1||reps>12)return null
    return if(reps==1)loadKg else loadKg*(1+reps/30.0)
}

/** The value an entry scores for a record, or null when the record does not apply to it. */
fun WorkoutEntry.score(kind:RecordKind):Double? = when(kind) {
    RecordKind.LOAD->loadKg
    RecordKind.E1RM->epley(loadKg,reps)
    RecordKind.REPS->reps?.toDouble()
    RecordKind.TOTAL_REPS->reps?.let{r->(sets?:1)*r.toDouble()}
    RecordKind.SECONDS->seconds?.toDouble()
    RecordKind.MINUTES->minutes.takeIf{sets==null}
}

/** The best entry for each record kind that has any value. */
fun personalRecords(history:List<WorkoutEntry>):Map<RecordKind,WorkoutEntry> =
    RecordKind.entries.mapNotNull{k->history.filter{it.score(k)!=null}.maxWithOrNull(compareBy<WorkoutEntry>{it.score(k)}.thenByDescending{it.localDate})?.let{k to it}}.toMap()

/**
 * Records [entry] would set against the rest of [history] (itself left out by id). The first time an exercise is done
 * sets no record: there is nothing to beat yet. With a heavier dumbbell, fewer reps are not a rep record (and the
 * other way round): reps count only against entries with the same load.
 */
fun newRecords(entry:WorkoutEntry,history:List<WorkoutEntry>):Set<RecordKind> {
    val others=history.filter{it.id!=entry.id}
    return RecordKind.entries.filter{k->
        val mine=entry.score(k)?:return@filter false
        val rivals=others.filter{k!=RecordKind.REPS&&k!=RecordKind.TOTAL_REPS||it.loadKg==entry.loadKg}.mapNotNull{it.score(k)}
        rivals.isNotEmpty()&&mine>rivals.max()+1e-9
    }.toSet()
}
/** Records each entry set at the time it was logged (only earlier entries count), for trophies in the lists. */
fun recordsAtTheTime(history:List<WorkoutEntry>):Map<String,Set<RecordKind>> =
    history.mapIndexed{i,w->w.id to newRecords(w,history.subList(0,i))}.filter{it.second.isNotEmpty()}.toMap()

/** The latest entry of the exercise on or before [date], other than the one being edited. */
fun lastPerformance(history:List<WorkoutEntry>,date:String,exceptId:String?=null):WorkoutEntry? =
    history.lastOrNull{it.localDate<=date&&it.id!=exceptId}

data class NextStep(val sets:Int?,val reps:Int?,val seconds:Int?,val loadKg:Double?)
/** Upper end of the rep range: past it the dumbbell gets heavier and the reps start again from [RESTART_REPS]. */
const val TOP_REPS=12
const val RESTART_REPS=8
/** Past this many reps a body-weight exercise gets another set instead of more reps. */
const val MAX_BODYWEIGHT_REPS=20

/**
 * One small step up from [last]: +1 rep; with a dumbbell at [TOP_REPS] reps, the next dumbbell (+1 kg up to 10 kg,
 * then +2 kg) for [RESTART_REPS] reps; body weight past [MAX_BODYWEIGHT_REPS] reps, one more set (up to 5); a hold,
 * +5 seconds. Timed activities (minutes) get no suggestion.
 */
fun nextStep(last:WorkoutEntry,measure:ExerciseMeasure):NextStep? = when(measure) {
    ExerciseMeasure.REPS->{
        val sets=last.sets?:return null;val reps=last.reps?:return null;val load=last.loadKg
        when {
            load!=null&&reps>=TOP_REPS->NextStep(sets,RESTART_REPS,null,load+if(load<10)1.0 else 2.0)
            load==null&&reps>=MAX_BODYWEIGHT_REPS->if(sets<5)NextStep(sets+1,reps,null,null) else null
            else->NextStep(sets,reps+1,null,load)
        }
    }
    ExerciseMeasure.SECONDS->{val sets=last.sets?:return null;val s=last.seconds?:return null;if(s>=3600)null else NextStep(sets,null,(s+5).coerceAtMost(3600),null)}
    ExerciseMeasure.MINUTES->null
}

/** What the progress chart follows: the estimated max with dumbbells, otherwise reps, hold time or minutes. */
fun mainMetric(history:List<WorkoutEntry>,measure:ExerciseMeasure):RecordKind = when {
    history.any{it.score(RecordKind.E1RM)!=null}->RecordKind.E1RM
    measure==ExerciseMeasure.SECONDS->RecordKind.SECONDS
    measure==ExerciseMeasure.MINUTES->RecordKind.MINUTES
    history.any{it.reps!=null}->RecordKind.REPS
    history.any{it.seconds!=null}->RecordKind.SECONDS
    else->RecordKind.MINUTES
}
data class ProgressPoint(val date:String,val value:Double)
/** The best value of each day, oldest first. */
fun progressSeries(history:List<WorkoutEntry>,metric:RecordKind):List<ProgressPoint> =
    history.mapNotNull{w->w.score(metric)?.let{w.localDate to it}}.groupBy({it.first},{it.second}).map{(d,v)->ProgressPoint(d,v.max())}.sortedBy{it.date}

/** One line of the progress overview: an exercise with its sessions, last and best result, and change since the start. */
data class ExerciseProgress(val exerciseId:String,val name:String,val sessions:Int,val last:WorkoutEntry,val metric:RecordKind,val first:Double?,val best:Double?,val latest:Double?) {
    /** Change from the first to the latest session in percent; null with one session. */
    val changePercent:Int? get() = if(sessions<2||first==null||latest==null||first<=0)null else ((latest-first)/first*100).roundToInt()
}
/** Exercises done in sets (reps or holds), most recently trained first. */
fun strengthProgress(s:UserSnapshot,measureOf:(String)->ExerciseMeasure?):List<ExerciseProgress> =
    s.workouts.filter{it.sets!=null}.groupBy{it.exerciseId}.map{(id,_)->
        val h=s.exerciseHistory(id);val m=mainMetric(h,measureOf(id)?:if(h.any{it.reps!=null})ExerciseMeasure.REPS else ExerciseMeasure.SECONDS)
        val series=progressSeries(h,m)
        ExerciseProgress(id,h.last().name,h.map{it.localDate}.toSet().size,h.last(),m,series.firstOrNull()?.value,series.maxOfOrNull{it.value},series.lastOrNull()?.value)
    }.sortedWith(compareByDescending<ExerciseProgress>{it.last.localDate}.thenByDescending{it.last.instant})
