package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MealSlotsTest {
    private val zone=ZoneId.of("Asia/Baku")
    private val food=Food("f","Каша",per100g=Nutrients(100.0,3.0,1.0,20.0))
    private val water=WATER_FOOD
    private fun at(f:Food,time:String,q:Quantity=Quantity(100.0,Measure.GRAMS))=makeEntry(time,f,q,"2026-11-27",null,LocalDateTime.parse("2026-11-27T$time").atZone(zone).toInstant(),zone)

    @Test fun localTimeDecidesThePartOfTheDayAndWaterIsItsOwnGroup() {
        assertEquals(MealSlot.BREAKFAST,mealSlot(at(food,"04:00")))
        assertEquals(MealSlot.BREAKFAST,mealSlot(at(food,"10:59")))
        assertEquals(MealSlot.LUNCH,mealSlot(at(food,"11:00")))
        assertEquals(MealSlot.DINNER,mealSlot(at(food,"16:00")))
        assertEquals(MealSlot.DINNER,mealSlot(at(food,"21:59")))
        assertEquals(MealSlot.LATE,mealSlot(at(food,"22:00")))
        assertEquals(MealSlot.LATE,mealSlot(at(food,"03:59")))
        assertEquals(MealSlot.WATER,mealSlot(at(water,"09:00",Quantity(250.0,Measure.ML))))
    }

    @Test fun groupsFollowTheDayAndLeaveEmptyOnesOut() {
        val list=listOf(at(food,"19:00"),at(water,"10:00",Quantity(250.0,Measure.ML)),at(food,"08:30"),at(food,"07:15"))
        val groups=mealGroups(list)
        assertEquals(listOf(MealSlot.BREAKFAST,MealSlot.DINNER,MealSlot.WATER),groups.map{it.first})
        assertEquals(listOf("07:15","08:30"),groups.first().second.map{it.id})
    }
}
