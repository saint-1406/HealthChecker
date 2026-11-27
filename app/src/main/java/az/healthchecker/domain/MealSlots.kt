package az.healthchecker.domain
import java.time.Instant
import java.time.ZoneId

/**
 * Parts of the day the Arcade diary groups entries into. Entries carry no meal of their own, so the group comes from
 * the local time the entry was logged at; water is a group of its own whatever the time.
 */
enum class MealSlot { BREAKFAST, LUNCH, DINNER, LATE, WATER }

/** 04:00–10:59 breakfast, 11:00–15:59 lunch, 16:00–21:59 dinner, otherwise a late snack. */
fun mealSlot(e:DiaryEntry):MealSlot {
    if(e.waterMl>0)return MealSlot.WATER
    val hour=runCatching{Instant.parse(e.instant).atZone(ZoneId.of(e.zoneId)).hour}.getOrDefault(12)
    return when(hour){in 4..10->MealSlot.BREAKFAST;in 11..15->MealSlot.LUNCH;in 16..21->MealSlot.DINNER;else->MealSlot.LATE}
}

/** The day's entries by part of the day in the order of the day, each group in the order logged; empty groups left out. */
fun mealGroups(entries:List<DiaryEntry>):List<Pair<MealSlot,List<DiaryEntry>>> =
    entries.groupBy(::mealSlot).toList().sortedBy{it.first.ordinal}.map{(slot,list)->slot to list.sortedBy{it.instant}}
