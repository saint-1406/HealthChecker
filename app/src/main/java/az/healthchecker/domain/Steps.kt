package az.healthchecker.domain
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Steps of one day: [sensor] is counted by the phone, [manual] is what the user entered and wins when set. */
@Serializable data class StepDay(val date:String,val sensor:Int=0,val manual:Int?=null) { val steps:Int get() = manual?:sensor }

fun UserSnapshot.stepsOn(date:String):StepDay = steps.find{it.date==date}?:StepDay(date)

/**
 * Walking energy by the 2024 Adult Compendium code 17190 (walking 2.8–3.4 mph, moderate pace, 3.8 MET) — the same
 * gross MET × kg × h estimate the training section uses. Step length ≈ 0.415 (men) / 0.413 (women) × height.
 */
const val WALK_MET=3.8
const val WALK_KMH=5.0
fun stepLengthMeters(profile:Profile):Double = profile.heightCm/100*if(profile.sex=="female")0.413 else 0.415
fun stepDistanceKm(steps:Int,profile:Profile):Double = steps*stepLengthMeters(profile)/1000
fun stepKcal(steps:Int,profile:Profile?):Double? = profile?.let{burnedKcal(WALK_MET,it.currentWeight,stepDistanceKm(steps,it)/WALK_KMH*60)}

/** Last reading of the phone's cumulative step counter, which restarts from zero after a reboot. */
data class CounterState(val counter:Long?=null)
/**
 * Steps since the previous reading. The first reading only sets the baseline: steps taken before counting was switched
 * on are unknown. A smaller value than before means the phone restarted, so the whole value is new steps.
 */
fun stepsSince(state:CounterState,counter:Long):Pair<Int,CounterState> {
    val last=state.counter
    val delta=when{last==null->0L;counter>=last->counter-last;else->counter}
    return Pair(delta.coerceIn(0L,MAX_STEPS_PER_DAY.toLong()).toInt(),CounterState(counter))
}
const val MAX_STEPS_PER_DAY=200_000

fun validateSteps(day:StepDay) {
    LocalDate.parse(day.date)
    require(day.sensor in 0..MAX_STEPS_PER_DAY&&(day.manual==null||day.manual in 0..MAX_STEPS_PER_DAY)){tr("Проверьте число шагов","Check the number of steps")}
}
