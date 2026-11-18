package az.healthchecker.domain
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Adaptive energy expenditure: what the body really spends, worked out from what was eaten and how the weight trend
 * moved — the energy balance in reverse. Eating 2 200 kcal a day while the trend stays flat means maintenance is about
 * 2 200 kcal, whatever a formula says.
 *
 * - **Intake** is the average of the logged days in the window. Days marked "day complete" are used when there are
 *   enough of them; otherwise every day with at least [MIN_DAY_KCAL] logged (shorter days are probably half-logged).
 * - **Weight** is smoothed with an exponential moving average (10 % per day, gaps filled linearly), so water and salt
 *   swings of a kilogram do not move it much. Its change over the window × 7 700 kcal/kg is the stored or lost energy.
 * - **Blending**: with little data the estimate leans on the formula (Mifflin–St Jeor × activity); with three weeks of
 *   logging and weighing it is the observed value.
 */
const val EXPENDITURE_WINDOW_DAYS=28
const val MIN_LOGGED_DAYS=7
const val MIN_WEIGH_SPAN_DAYS=10
const val MIN_WEIGH_INS=4
const val MIN_DAY_KCAL=800.0
const val KCAL_PER_KG=7700.0
private const val TREND_ALPHA=0.1
private const val FULL_CONFIDENCE_DAYS=21.0

enum class ExpenditureStatus { OK, NEED_FOOD, NEED_WEIGHT, NEED_PROFILE }

data class Expenditure(
    val status:ExpenditureStatus,
    /** Mifflin–St Jeor × activity from the profile; null without a profile or under 18. */
    val formula:Double?,
    /** From intake and weight trend only; null until there is enough data. */
    val observed:Double?,
    /** What to plan with: the observed value blended with the formula by [confidence]. */
    val estimate:Double?,
    /** 0–1: how much of [estimate] comes from the user's own data. */
    val confidence:Double,
    val avgIntake:Double?,val loggedDays:Int,val usedCompletedDays:Boolean,
    val weighIns:Int,
    /** Weight trend change per week, kg (negative = losing). */
    val trendPerWeek:Double?,
    val trendKg:Double?,
)

/** Daily weight trend (exponential moving average) from the first weigh-in up to [end], one value per day. */
fun weightTrend(weights:List<WeightEntry>,end:LocalDate):Map<LocalDate,Double> {
    val sorted=weights.map{LocalDate.parse(it.date) to it.kg}.filter{!it.first.isAfter(end)}.sortedBy{it.first}
    if(sorted.isEmpty())return emptyMap()
    val out=LinkedHashMap<LocalDate,Double>()
    var trend=sorted.first().second
    var i=0
    var day=sorted.first().first
    while(!day.isAfter(end)) {
        // The scale value for the day: the weigh-in, or a straight line between the neighbouring weigh-ins.
        while(i+1<sorted.size&&!sorted[i+1].first.isAfter(day))i++
        val (d0,w0)=sorted[i]
        val value=if(d0==day||i+1>=sorted.size)w0 else {
            val (d1,w1)=sorted[i+1];val f=ChronoUnit.DAYS.between(d0,day).toDouble()/ChronoUnit.DAYS.between(d0,d1)
            w0+(w1-w0)*f
        }
        trend+=TREND_ALPHA*(value-trend)
        out[day]=trend
        day=day.plusDays(1)
    }
    return out
}

fun expenditure(s:UserSnapshot,endDate:String,window:Int=EXPENDITURE_WINDOW_DAYS):Expenditure {
    val end=LocalDate.parse(endDate);val start=end.minusDays((window-1).toLong())
    val formula=s.profile?.let{p->runCatching{estimateMaintenance(p.copy(currentWeight=s.bodyWeight(endDate)?:p.currentWeight))}.getOrNull()}
    // Intake per day; today is skipped while it is still going on.
    val perDay=s.entries.filter{it.waterMl==0.0}.groupBy{it.localDate}.mapValues{(_,e)->e.sumOf{it.nutrients.kcal?:0.0}}
    val days=(0 until window).map{start.plusDays(it.toLong()).toString()}.filter{it<endDate}
    val completed=days.filter{it in s.completedDays&&(perDay[it]?:0.0)>0}
    val useCompleted=completed.size>=MIN_LOGGED_DAYS
    val logged=if(useCompleted)completed else days.filter{(perDay[it]?:0.0)>=MIN_DAY_KCAL}
    val avgIntake=logged.takeIf{it.isNotEmpty()}?.map{perDay[it]!!}?.average()
    // The weight trend needs a run-in before the window, so it starts from the first weigh-in ever.
    val inWindow=s.weights.filter{it.date in start.toString()..endDate}.sortedBy{it.date}
    val trend=weightTrend(s.weights,end)
    val span=if(inWindow.size>=2)ChronoUnit.DAYS.between(LocalDate.parse(inWindow.first().date),LocalDate.parse(inWindow.last().date)).toInt() else 0
    val enoughWeight=inWindow.size>=MIN_WEIGH_INS&&span>=MIN_WEIGH_SPAN_DAYS
    val from=LocalDate.parse(inWindow.firstOrNull()?.date?:endDate);val to=LocalDate.parse(inWindow.lastOrNull()?.date?:endDate)
    val trendKg=if(enoughWeight)trend[to]?.let{t->trend[from]?.let{t-it}} else null
    val trendPerWeek=trendKg?.let{it/span*7}
    val enoughFood=logged.size>=MIN_LOGGED_DAYS&&avgIntake!=null
    val observed=if(enoughFood&&trendPerWeek!=null)(avgIntake!!-trendPerWeek/7*KCAL_PER_KG).coerceIn(1000.0,6000.0) else null
    val confidence=if(observed==null)0.0 else min(1.0,logged.size/FULL_CONFIDENCE_DAYS)*min(1.0,span/FULL_CONFIDENCE_DAYS)
    val estimate=when{observed!=null&&formula!=null->observed*confidence+formula*(1-confidence);observed!=null->observed;else->formula}
    val status=when{!enoughFood->ExpenditureStatus.NEED_FOOD;!enoughWeight->ExpenditureStatus.NEED_WEIGHT;formula==null&&observed==null->ExpenditureStatus.NEED_PROFILE;else->ExpenditureStatus.OK}
    return Expenditure(status,formula,observed,estimate,confidence,avgIntake,logged.size,useCompleted,inWindow.size,trendPerWeek,trendKg)
}

/**
 * A calorie target from the estimated expenditure: the program's goal factor (or maintenance without a program), at most
 * −750 / +500 kcal from it, never below the safe minimum for the sex; rounded to 10 kcal.
 */
fun adaptiveTarget(e:Expenditure,s:UserSnapshot):Double? {
    val tdee=e.estimate?:return null
    val goal=s.setup?.goal?:FitnessGoal.MAINTAIN
    if(s.profile?.age?.let{it<18}==true)return null
    val pregnant=s.setup?.limitations?.contains(Limitation.PREGNANCY)==true
    val factor=if(pregnant)max(1.0,goalFactor(goal)) else goalFactor(goal)
    val wanted=(tdee*factor).coerceIn(tdee-750,tdee+500)
    val floor=min(minimumKcal(s.profile?.sex?:s.setup?.sex?:"female"),tdee)
    return ((max(wanted,floor)/10.0).roundToInt()*10).toDouble()
}

/** Worth suggesting a new target: enough own data, and at least 100 kcal away from the current one. */
fun shouldSuggest(e:Expenditure,current:Double?,suggested:Double?):Boolean =
    suggested!=null&&e.observed!=null&&e.confidence>=0.5&&(current==null||abs(suggested-current)>=100)
