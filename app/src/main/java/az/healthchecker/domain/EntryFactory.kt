package az.healthchecker.domain
import java.time.Instant
import java.time.ZoneId
fun makeEntry(id:String,food:Food,q:Quantity,date:String,old:DiaryEntry?,now:Instant,zone:ZoneId):DiaryEntry {
    val c=calculate(food,q)
    // An edit keeps the entry on its original calendar day, even if the selected date moved past midnight.
    return DiaryEntry(id,food.id,food.name,q,c.grams,c.nutrients,if(food.isWater)c.grams else 0.0,old?.instant?:now.toString(),old?.zoneId?:zone.id,old?.localDate?:date)
}
fun advanceSelectedDate(selected:String,previousToday:String,today:String):String = if(selected==previousToday)today else selected
fun isLogged(day:DayState):Boolean = day.entries.isNotEmpty()||day.completed
// Days without data and days whose calorie sum is only partially known would distort the average.
fun averageDays(days:List<DayState>):List<DayState> = days.filter{isLogged(it)&&it.summary.kcal.complete}
fun weeklyAverage(days:List<DayState>):Double? = averageDays(days).takeIf{it.isNotEmpty()}?.map{it.summary.kcal.value}?.average()
