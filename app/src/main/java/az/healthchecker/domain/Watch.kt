package az.healthchecker.domain
import java.time.Instant
import java.time.ZoneId

/**
 * What a watch shares through Android's Health Connect (for Xiaomi watches: Mi Fitness → Health Connect). The app only
 * reads it. Steps, sleep, workouts and pulse are shown and never copied into the app's records or backups; weigh-ins
 * from a smart scale are the one exception — they become the app's own weight log (see [scaleWeightsToImport]).
 */
enum class WatchActivity { WALK, RUN, BIKE, SWIM, STRENGTH, YOGA, PILATES, STRETCH, HIKE, ELLIPTICAL, ROWING, HIIT, OTHER }
data class WatchWorkout(val start:Instant,val end:Instant,val activity:WatchActivity,val title:String?,val avgHeartRate:Int?,val source:String) {
    val minutes:Int get() = ((end.toEpochMilli()-start.toEpochMilli())/60_000).toInt().coerceAtLeast(0)
}
/** One night: [asleepMinutes] leaves out the awake stages when the watch recorded stages. */
data class WatchSleep(val start:Instant,val end:Instant,val asleepMinutes:Int,val source:String)
data class HeartRange(val min:Int,val avg:Int,val max:Int)
/** One weigh-in a smart scale's app (Mi Fitness for Xiaomi scales) wrote to Health Connect. */
data class ScaleWeight(val time:Instant,val kg:Double,val source:String)
/** [steps] holds Health Connect's daily totals, which already merge overlapping sources (phone, watch, other apps). */
data class WatchData(
    val steps:Map<String,Int> = emptyMap(), val stepSources:Set<String> = emptySet(), val sleep:List<WatchSleep> = emptyList(),
    val workouts:List<WatchWorkout> = emptyList(), val heartToday:HeartRange? = null, val heartSources:Set<String> = emptySet(),
    val weights:List<ScaleWeight> = emptyList(),
    /** Average heart rate of each day (ISO date → bpm), from every source Health Connect merges. */
    val heartByDay:Map<String,Int> = emptyMap(),
) {
    val sources:Set<String> get() = stepSources+heartSources+sleep.map{it.source}+workouts.map{it.source}+weights.map{it.source}
    val fromXiaomi:Boolean get() = sources.any{it in XIAOMI_APPS||it in XIAOMI_APPS.values}
    val isEmpty:Boolean get() = steps.values.none{it>0}&&sleep.isEmpty()&&workouts.isEmpty()&&heartToday==null&&weights.isEmpty()
}

/** Xiaomi's companion apps that write to Health Connect, by package name. */
val XIAOMI_APPS = mapOf("com.mi.health" to "Mi Fitness","com.xiaomi.wearable" to "Xiaomi Wear","com.xiaomi.hm.health" to "Zepp Life")
private val KNOWN_APPS = XIAOMI_APPS+mapOf("com.google.android.apps.fitness" to "Google Fit","com.sec.android.app.shealth" to "Samsung Health",
    "com.huawei.health" to "Huawei Health","com.google.android.apps.healthdata" to "Health Connect","android" to "Android")
/** A readable name for a data source; unknown apps fall back to [label] (the installed app's name) or the package. */
fun sourceName(packageName:String,label:String?=null):String = KNOWN_APPS[packageName]?:label?.takeIf{it.isNotBlank()}?:packageName

/** The day a night belongs to is the day it ends: sleep from 23:30 to 7:10 is "Tuesday's sleep" when it ends on Tuesday. */
fun WatchSleep.date(zone:ZoneId=ZoneId.systemDefault()):String = end.atZone(zone).toLocalDate().toString()
fun WatchWorkout.date(zone:ZoneId=ZoneId.systemDefault()):String = start.atZone(zone).toLocalDate().toString()
fun WatchData.sleepOn(date:String,zone:ZoneId=ZoneId.systemDefault()):List<WatchSleep> = sleep.filter{it.date(zone)==date}
fun WatchData.workoutsOn(date:String,zone:ZoneId=ZoneId.systemDefault()):List<WatchWorkout> = workouts.filter{it.date(zone)==date}
/** Minutes asleep in the nights that belong to [date] (a night belongs to the day it ends); null when none was recorded. */
fun WatchData.asleepOn(date:String,zone:ZoneId=ZoneId.systemDefault()):Int? = sleepOn(date,zone).takeIf{it.isNotEmpty()}?.sumOf{it.asleepMinutes}
/** The day's average heart rate; for today the same as [WatchData.heartToday] when only that was read. */
fun WatchData.heartOn(date:String,today:String):Int? = heartByDay[date]?:heartToday?.takeIf{date==today}?.avg

/**
 * Minutes asleep in a session. [awake] lists the stages marked awake, awake in bed or out of bed; they are subtracted
 * (clipped to the session) from its whole length. Without stages the whole session counts.
 */
fun asleepMinutes(start:Instant,end:Instant,awake:List<Pair<Instant,Instant>>):Int {
    val total=end.toEpochMilli()-start.toEpochMilli();if(total<=0)return 0
    val awakeMs=awake.sumOf{(s,e)->(minOf(e.toEpochMilli(),end.toEpochMilli())-maxOf(s.toEpochMilli(),start.toEpochMilli())).coerceAtLeast(0)}
    return ((total-awakeMs).coerceAtLeast(0)/60_000).toInt()
}

/** One raw steps record: the day it started, the app that wrote it and its count. */
data class RawSteps(val date:String,val source:String,val count:Long)
/**
 * Daily steps from raw records when Health Connect gives no total: each app's records of a day are added up, then the
 * largest app wins. Apps are not added together — a phone and a watch both count the same walk.
 */
fun stepsBySource(records:List<RawSteps>):Map<String,Long> =
    records.groupBy{it.date}.mapValues{(_,day)->day.groupBy{it.source}.values.maxOf{r->r.sumOf{it.count}}}.filterValues{it>0}

/** Steps of a day with the watch's total attached. */
fun UserSnapshot.stepsOn(date:String,watch:WatchData?):StepDay = stepsOn(date).copy(watch=watch?.steps?.get(date)?.takeIf{it>0})

/**
 * Scale weigh-ins to add to the app's weight log: the first weigh-in of each day (the morning one, before food), for
 * days with no weight of their own. [seen] are days taken from Health Connect before: a weight the user deleted or
 * typed over is never brought back. Values outside 20–400 kg (a child or a bag on the scale) are left out.
 */
fun scaleWeightsToImport(scale:List<ScaleWeight>,existing:Set<String>,seen:Set<String>,zone:ZoneId=ZoneId.systemDefault()):List<WeightEntry> =
    scale.filter{it.kg.isFinite()&&it.kg in 20.0..400.0}.groupBy{it.time.atZone(zone).toLocalDate().toString()}
        .filterKeys{it !in existing&&it !in seen}
        .map{(date,day)->val first=day.minBy{it.time};WeightEntry(date,Math.round(first.kg*100)/100.0,first.time.toString(),zone.id)}
        .sortedBy{it.date}
