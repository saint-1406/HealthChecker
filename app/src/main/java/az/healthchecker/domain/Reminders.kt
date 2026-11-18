package az.healthchecker.domain
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Which reminders are on and when. A device setting (not in backups): notifications belong to this phone.
 * Times are "HH:mm"; [trainingDays] are ISO days of week, 1 = Monday.
 */
@Serializable data class ReminderSettings(
    val medications:Boolean=false,
    val water:Boolean=false,val waterFrom:String="09:00",val waterTo:String="21:00",val waterEveryHours:Int=2,
    val weigh:Boolean=false,val weighAt:String="08:00",
    val training:Boolean=false,val trainingDays:Set<Int> = setOf(1,3,5),val trainingAt:String="18:00",
    val log:Boolean=false,val logAt:String="21:00",
) { val anyOn:Boolean get() = medications||water||weigh||training||log }

fun validateReminders(r:ReminderSettings) {
    listOf(r.waterFrom,r.waterTo,r.weighAt,r.trainingAt,r.logAt).forEach{require(runCatching{LocalTime.parse(it)}.isSuccess){tr("Время — в формате 08:00","Use 08:00 for times")}}
    require(LocalTime.parse(r.waterFrom)<LocalTime.parse(r.waterTo)){tr("Вода: «с» должно быть раньше «до»","Water: “from” must be before “to”")}
    require(r.waterEveryHours in 1..6&&r.trainingDays.all{it in 1..7})
}

enum class ReminderKind { MEDICATION, WATER, WEIGH, TRAINING, LOG }
/** One planned reminder; for a medicine, [doseIndex] is which of the day's doses (0 = the first time of the day). */
data class ReminderSlot(val kind:ReminderKind,val at:LocalDateTime,val medicationId:String?=null,val doseIndex:Int=0)

/** Every reminder of [date], in time order. */
fun ReminderSettings.slotsOn(date:LocalDate,meds:List<Medication>):List<ReminderSlot> {
    val d=date.toString();val out=ArrayList<ReminderSlot>()
    if(medications)meds.filter{it.activeOn(d)}.forEach{m->m.times.map(LocalTime::parse).sorted().forEachIndexed{i,t->out+=ReminderSlot(ReminderKind.MEDICATION,date.atTime(t),m.id,i)}}
    if(water){var t=LocalTime.parse(waterFrom);val to=LocalTime.parse(waterTo)
        while(!t.isAfter(to)){out+=ReminderSlot(ReminderKind.WATER,date.atTime(t));val n=t.plusHours(waterEveryHours.toLong());if(n<=t)break;t=n}}
    if(weigh)out+=ReminderSlot(ReminderKind.WEIGH,date.atTime(LocalTime.parse(weighAt)))
    if(training&&date.dayOfWeek.value in trainingDays)out+=ReminderSlot(ReminderKind.TRAINING,date.atTime(LocalTime.parse(trainingAt)))
    if(log)out+=ReminderSlot(ReminderKind.LOG,date.atTime(LocalTime.parse(logAt)))
    return out.sortedBy{it.at}
}

/** The first reminder strictly after [after], looking a week ahead; null when nothing is planned. */
fun ReminderSettings.nextSlot(meds:List<Medication>,after:LocalDateTime):ReminderSlot? =
    (0..7).asSequence().map{after.toLocalDate().plusDays(it.toLong())}.flatMap{slotsOn(it,meds)}.firstOrNull{it.at.isAfter(after)}

/**
 * Reminders due in (from, to]. One that is more than [STALE_MINUTES] late (the phone was off) is dropped rather than
 * shown out of place; the limit is above the up-to-an-hour window Android gives an inexact alarm in Doze.
 */
const val STALE_MINUTES=75L
fun ReminderSettings.dueSlots(meds:List<Medication>,from:LocalDateTime,to:LocalDateTime):List<ReminderSlot> {
    val earliest=maxOf(from,to.minusMinutes(STALE_MINUTES))
    return generateSequence(earliest.toLocalDate()){it.plusDays(1)}.takeWhile{!it.isAfter(to.toLocalDate())}
        .flatMap{slotsOn(it,meds)}.filter{it.at.isAfter(earliest)&&!it.at.isAfter(to)}.toList()
}

/**
 * Whether a due reminder still makes sense: a dose already marked, a weigh-in already made, training already logged
 * or water goal already reached make it pointless.
 */
fun ReminderSlot.stillNeeded(s:UserSnapshot):Boolean {
    val d=at.toLocalDate().toString()
    return when(kind) {
        ReminderKind.MEDICATION->s.medicationIntakes.count{it.medicationId==medicationId&&it.localDate==d}<=doseIndex
        ReminderKind.WATER->s.day(d).let{day->val goal=day.goals?.waterMl;goal==null||day.entries.sumOf{it.waterMl}<goal}
        ReminderKind.WEIGH->s.weights.none{it.date==d}
        ReminderKind.TRAINING->s.workouts.none{it.localDate==d}&&s.trainingDays[d]!=false
        ReminderKind.LOG->d !in s.completedDays
    }
}

fun dayOfWeekShort(day:Int):String = when(DayOfWeek.of(day)){
    DayOfWeek.MONDAY->tr("Пн","Mon");DayOfWeek.TUESDAY->tr("Вт","Tue");DayOfWeek.WEDNESDAY->tr("Ср","Wed");DayOfWeek.THURSDAY->tr("Чт","Thu")
    DayOfWeek.FRIDAY->tr("Пт","Fri");DayOfWeek.SATURDAY->tr("Сб","Sat");DayOfWeek.SUNDAY->tr("Вс","Sun")
}

/** Drinking water as the catalog has it (usda:173647), so a glass from a notification needs no catalog load. */
val WATER_FOOD=Food("usda:173647","Вода питьевая",listOf("su вода стакан"),Nutrients(0.0,0.0,0.0,0.0,0.0),listOf(Portion("Стакан (250 мл)",250.0,nameEn="Glass (250 ml)")),density=1.0,
    source="USDA FoodData Central · 173647",isWater=true,nameEn="Beverages, water, tap, drinking",common=true)
