package az.healthchecker.domain
import java.time.LocalDate

/** How far back "often eaten / often done" looks, and how many suggestions the favorites tabs offer. */
const val FREQUENT_DAYS=60
const val FREQUENT_LIMIT=8

/**
 * Foods logged most often in the last [days] days that are not starred yet — offered on Diary → Favorites with a star
 * to add. Water and days after [today] do not count; ties go to the most recently eaten.
 */
fun UserSnapshot.frequentFoods(today:String,days:Int=FREQUENT_DAYS,limit:Int=FREQUENT_LIMIT):List<String> {
    val from=LocalDate.parse(today).minusDays((days-1).toLong()).toString()
    return frequent(entries.filter{it.waterMl==0.0&&it.localDate in from..today&&it.foodId !in favoriteFoods}.map{it.foodId to it.instant},limit)
}

/** The same for exercises on Training → Favorites. */
fun UserSnapshot.frequentExercises(today:String,days:Int=FREQUENT_DAYS,limit:Int=FREQUENT_LIMIT):List<String> {
    val from=LocalDate.parse(today).minusDays((days-1).toLong()).toString()
    return frequent(workouts.filter{it.localDate in from..today&&it.exerciseId !in favoriteExercises}.map{it.exerciseId to it.instant},limit)
}

private fun frequent(uses:List<Pair<String,String>>,limit:Int):List<String> =
    uses.groupBy({it.first},{it.second}).entries.sortedWith(compareByDescending<Map.Entry<String,List<String>>>{it.value.size}.thenByDescending{it.value.max()}).take(limit).map{it.key}
