package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
class WeightTest {
    private fun w(date:String,kg:Double)=makeWeight(date,kg,Instant.parse("${date}T04:30:00Z"),ZoneId.of("Asia/Baku"))
    private fun days(end:String,vararg kg:Double)=kg.mapIndexed{i,v->w(LocalDate.parse(end).minusDays((kg.size-1-i).toLong()).toString(),v)}
    @Test fun weeklyAveragesShowDirectionDespiteDailySwings() {
        // Two weeks; a noisy morning (81,4) does not turn a falling week into a rise.
        val log=days("2026-10-21",81.0,81.2,80.9,81.1,80.8,81.0,80.9, 80.6,80.4,81.4,80.2,80.3,80.1,80.0)
        val s=weightStats(log,"2026-10-21",null)
        assertEquals(Trend.DOWN,s.trend);assertEquals(80.43-80.99,s.weekChange!!,0.01);assertEquals(80.0,s.latest!!.kg,0.0);assertEquals(-1.0,s.sinceStart!!,1e-9)
    }
    @Test fun smallChangesAreStableAndOneWeekComparesItsEnds() {
        assertEquals(Trend.STABLE,weightStats(days("2026-10-21",80.0,80.1,80.0,79.9,80.0,80.1,80.0, 80.1,80.0,80.0,80.1,79.9,80.0,80.1),"2026-10-21",null).trend)
        val oneWeek=weightStats(days("2026-10-21",70.0,70.5,70.8),"2026-10-21",72.0)
        assertEquals(Trend.UP,oneWeek.trend);assertEquals(0.8,oneWeek.weekChange!!,1e-9);assertEquals(-1.2,oneWeek.sinceStart!!,1e-9)
        assertNull(weightStats(days("2026-10-21",70.0),"2026-10-21",null).trend)
    }
    @Test fun statsIgnoreLaterDaysAndMonthUsesTheSameWeekAMonthEarlier() {
        val log=days("2026-10-21",*DoubleArray(40){90.0-it*0.1})
        val s=weightStats(log,"2026-10-21",null);assertEquals(-3.0,s.monthChange!!,0.01)
        assertEquals("2026-10-11",weightStats(log,"2026-10-11",null).latest!!.date)
    }
    @Test fun bodyWeightUsesTheLatestWeighInUpToTheDay() {
        val s=UserSnapshot(profile=Profile(currentWeight=85.0),weights=listOf(w("2026-10-10",82.0),w("2026-10-20",81.0)))
        assertEquals(82.0,s.bodyWeight("2026-10-15")!!,0.0);assertEquals(81.0,s.bodyWeight("2026-10-22")!!,0.0);assertEquals(85.0,s.bodyWeight("2026-10-01")!!,0.0)
        assertNull(UserSnapshot().bodyWeight("2026-10-22"))
    }
    @Test fun implausibleWeightsAreRejected() {
        listOf(0.0,19.9,400.1,Double.NaN,Double.POSITIVE_INFINITY).forEach{assertThrows(IllegalArgumentException::class.java){w("2026-10-21",it)}}
    }
}
