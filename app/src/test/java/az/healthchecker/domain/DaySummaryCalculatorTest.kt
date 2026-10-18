package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
class DaySummaryCalculatorTest {
    private fun entry(date: String, protein: Double? = 10.0) = DiaryEntry("one","food","Food",Quantity(100.0,Measure.GRAMS),100.0,Nutrients(100.0,protein),instant="2026-10-18T19:59:00Z",zoneId="Asia/Baku",localDate=date)
    @Test fun partialNutrientsStayPartial() { val s=summarize(listOf(entry("2026-10-18"),entry("2026-10-18",null))); assertEquals(10.0,s.protein.value,0.0);assertFalse(s.protein.complete) }
    @Test fun datesAndGoalsAreHistorical() { val s=UserSnapshot(entries=listOf(entry("2026-10-18"),entry("2026-10-19")),goals=listOf(DailyGoals("2026-10-18",2000.0),DailyGoals("2026-10-19",2100.0)));assertEquals(1,s.day("2026-10-18").entries.size);assertEquals(2000.0,s.day("2026-10-18").goals!!.kcal!!,0.0) }
    @Test fun weekKeepsMissingDaysAndExplicitZero() { val s=UserSnapshot(completedDays=setOf("2026-10-18"));val w=s.week("2026-10-18");assertEquals(7,w.size);assertEquals("2026-10-12",w.first().date);assertTrue(w.last().completed);assertFalse(w.first().completed) }
}
