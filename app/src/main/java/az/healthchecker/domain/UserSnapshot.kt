package az.healthchecker.domain
import kotlinx.serialization.Serializable
@Serializable data class UserSnapshot(val profile: Profile? = null, val goals: List<DailyGoals> = emptyList(), val entries: List<DiaryEntry> = emptyList(), val customFoods: List<Food> = emptyList(), val completedDays: Set<String> = emptySet(), val onboarded: Boolean = false)
data class DayState(val date: String, val entries: List<DiaryEntry>, val summary: DaySummary, val goals: DailyGoals?, val completed: Boolean)
fun UserSnapshot.day(date: String): DayState {
    java.time.LocalDate.parse(date)
    val e=entries.filter{it.localDate==date}.sortedByDescending{it.instant}
    return DayState(date,e,summarize(e),goals.filter{it.effectiveFrom<=date}.maxByOrNull{it.effectiveFrom},date in completedDays)
}
fun UserSnapshot.week(endDate: String): List<DayState> = (6 downTo 0).map { day(java.time.LocalDate.parse(endDate).minusDays(it.toLong()).toString()) }
