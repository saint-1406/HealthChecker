package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID
class ReviewRegressionTest {
    private val food=Food("f","Food",per100g=Nutrients(200.0))
    private val q=Quantity(100.0,Measure.GRAMS)
    private fun old()=DiaryEntry(UUID.randomUUID().toString(),"f","Food",q,100.0,Nutrients(200.0),instant="2026-10-18T19:55:00Z",zoneId="Asia/Baku",localDate="2026-10-18")
    @Test fun editingAfterMidnightPreservesOriginalDate(){val e=old();val result=makeEntry(e.id,food,q,"2026-10-19",e,Instant.parse("2026-10-18T20:05:00Z"),ZoneId.of("Asia/Baku"));assertEquals("2026-10-18",result.localDate);assertEquals(e.instant,result.instant)}
    @Test fun viewingTodayAdvancesButHistoryDoesNot(){assertEquals("2026-10-19",advanceSelectedDate("2026-10-18","2026-10-18","2026-10-19"));assertEquals("2026-10-17",advanceSelectedDate("2026-10-17","2026-10-18","2026-10-19"))}
    @Test fun weeklyAverageExcludesIncompleteDays(){val e=old();val s=UserSnapshot(entries=listOf(e,e.copy(id=UUID.randomUUID().toString(),localDate="2026-10-19",nutrients=Nutrients())));assertEquals(200.0,weeklyAverage(s.week("2026-10-19"))!!,0.0)}
    @Test fun completedEmptyDayCountsAsZeroButMissingDayDoesNot(){val s=UserSnapshot(entries=listOf(old()),completedDays=setOf("2026-10-17"));assertEquals(100.0,weeklyAverage(s.week("2026-10-19"))!!,0.0);assertNull(weeklyAverage(UserSnapshot().week("2026-10-19")))}
    @Test fun newEntryUsesSelectedDateAndCurrentTime(){val now=Instant.parse("2026-10-18T20:05:00Z");val e=makeEntry(UUID.randomUUID().toString(),food,q,"2026-10-17",null,now,ZoneId.of("Asia/Baku"));assertEquals("2026-10-17",e.localDate);assertEquals(now.toString(),e.instant);assertEquals("Asia/Baku",e.zoneId)}
    @Test fun waterCountsOnlyForWaterProduct(){val water=Food("w","Вода",per100g=Nutrients(0.0,0.0,0.0,0.0),density=1.0,isWater=true);val zone=ZoneId.of("Asia/Baku");val now=Instant.now();assertEquals(250.0,makeEntry(UUID.randomUUID().toString(),water,Quantity(250.0,Measure.ML),"2026-10-18",null,now,zone).waterMl,0.0);assertEquals(0.0,makeEntry(UUID.randomUUID().toString(),food.copy(density=1.03),Quantity(250.0,Measure.ML),"2026-10-18",null,now,zone).waterMl,0.0)}
}
