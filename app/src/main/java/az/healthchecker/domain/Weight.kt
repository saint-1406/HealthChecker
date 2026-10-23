package az.healthchecker.domain
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One weigh-in per calendar day; a second one the same day replaces the first. */
@Serializable data class WeightEntry(val date:String,val kg:Double,val instant:String,val zoneId:String)

fun makeWeight(date:String,kg:Double,now:Instant,zone:ZoneId):WeightEntry {
    LocalDate.parse(date)
    require(kg.isFinite()&&kg in 20.0..400.0){tr("Вес от 20 до 400 кг","Weight must be 20 to 400 kg")}
    return WeightEntry(date,kg,now.toString(),zone.id)
}
fun validateWeight(w:WeightEntry) { LocalDate.parse(w.date);require(w.kg.isFinite()&&w.kg in 2.0..500.0);Instant.parse(w.instant);ZoneId.of(w.zoneId) }

enum class Trend { DOWN, UP, STABLE }
/** Changes smaller than this over a week are within daily water and food swings. */
const val WEIGHT_STABLE_KG=0.2
data class WeightStats(val latest:WeightEntry?,val weekChange:Double?,val monthChange:Double?,val sinceStart:Double?,val trend:Trend?,val series:List<WeightEntry>)

/**
 * Morning weights jump by a kilogram with water and salt, so direction compares the average of the last 7 days with
 * the 7 days before (and with the same week a month earlier). With only one week of data the first and last weigh-in
 * of that week are compared. [startKg] is the profile's initial weight; without a profile the first weigh-in is the start.
 */
fun weightStats(weights:List<WeightEntry>,endDate:String,startKg:Double?):WeightStats {
    val end=LocalDate.parse(endDate)
    val sorted=weights.filter{it.date<=endDate}.sortedBy{it.date}
    fun avg(fromDaysAgo:Long,toDaysAgo:Long):Double? {
        val from=end.minusDays(fromDaysAgo).toString();val to=end.minusDays(toDaysAgo).toString()
        return sorted.filter{it.date in from..to}.map{it.kg}.takeIf{it.isNotEmpty()}?.average()
    }
    val recent=avg(6,0)
    val week=recent?.let{r->avg(13,7)?.let{r-it}}?:sorted.filter{it.date>=end.minusDays(6).toString()}.takeIf{it.size>=2}?.let{it.last().kg-it.first().kg}
    val month=recent?.let{r->avg(36,30)?.let{r-it}}
    val start=startKg?:sorted.firstOrNull()?.kg
    val since=sorted.lastOrNull()?.let{l->start?.let{l.kg-it}}
    val trend=week?.let{when{it<=-WEIGHT_STABLE_KG->Trend.DOWN;it>=WEIGHT_STABLE_KG->Trend.UP;else->Trend.STABLE}}
    return WeightStats(sorted.lastOrNull(),week,month,since,trend,sorted.filter{it.date>=end.minusDays(89).toString()})
}

/** Body weight for calorie estimates on a date: the last weigh-in up to that day, else the profile's current weight. */
fun UserSnapshot.bodyWeight(date:String):Double? = weights.filter{it.date<=date}.maxByOrNull{it.date}?.kg?:profile?.currentWeight
