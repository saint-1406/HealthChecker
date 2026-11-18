package az.healthchecker.domain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WatchTest {
    private val baku=ZoneId.of("Asia/Baku")
    private fun at(s:String)=Instant.parse(s)

    @Test fun manualWinsThenWatchThenPhone() {
        assertEquals(StepSource.PHONE,StepDay("2026-11-15",sensor=4000).source)
        val watch=StepDay("2026-11-15",sensor=4000,watch=9000)
        assertEquals(9000,watch.steps);assertEquals(StepSource.WATCH,watch.source)
        val manual=watch.copy(manual=12000)
        assertEquals(12000,manual.steps);assertEquals(StepSource.MANUAL,manual.source)
        assertEquals(StepSource.NONE,StepDay("2026-11-15").source)
    }

    @Test fun watchStepsAreNeverStoredOrBackedUp() {
        val text=Json.encodeToString(StepDay("2026-11-15",sensor=10,watch=9000))
        assertFalse(text,text.contains("watch"))
        assertNull(Json.decodeFromString<StepDay>(text).watch)
    }

    @Test fun snapshotTakesTheWatchTotalOfThatDayOnly() {
        val s=UserSnapshot(steps=listOf(StepDay("2026-11-14",sensor=3000),StepDay("2026-11-15",sensor=500)))
        val w=WatchData(steps=mapOf("2026-11-15" to 8000,"2026-11-14" to 0))
        assertEquals(8000,s.stepsOn("2026-11-15",w).steps)
        // A zero from Health Connect means nothing came in that day: the phone's own count stays.
        assertEquals(3000,s.stepsOn("2026-11-14",w).steps)
        assertEquals(500,s.stepsOn("2026-11-15",null).steps)
    }

    @Test fun awakeStagesAreSubtractedAndClipped() {
        val start=at("2026-11-14T19:30:00Z");val end=at("2026-11-15T03:30:00Z") // 23:30–07:30 in Baku, 8 h
        assertEquals(480,asleepMinutes(start,end,emptyList()))
        val awake=listOf(at("2026-11-15T00:00:00Z") to at("2026-11-15T00:20:00Z"),at("2026-11-15T03:20:00Z") to at("2026-11-15T04:00:00Z"))
        assertEquals(450,asleepMinutes(start,end,awake))
        assertEquals(0,asleepMinutes(end,start,emptyList()))
    }

    @Test fun nightBelongsToTheMorningItEnds() {
        val n=WatchSleep(at("2026-11-14T19:30:00Z"),at("2026-11-15T03:30:00Z"),450,"Mi Fitness")
        assertEquals("2026-11-15",n.date(baku))
        assertEquals(listOf(n),WatchData(sleep=listOf(n)).sleepOn("2026-11-15",baku))
        assertTrue(WatchData(sleep=listOf(n)).sleepOn("2026-11-14",baku).isEmpty())
    }

    @Test fun xiaomiSourcesAreRecognised() {
        assertEquals("Mi Fitness",sourceName("com.mi.health"))
        assertEquals("Some App",sourceName("org.example","Some App"))
        assertEquals("org.example",sourceName("org.example",null))
        assertTrue(WatchData(stepSources=setOf("com.mi.health".let{sourceName(it)})).sources.isNotEmpty())
        val w=WatchWorkout(at("2026-11-15T05:00:00Z"),at("2026-11-15T05:42:30Z"),WatchActivity.RUN,null,151,"com.mi.health")
        assertTrue(WatchData(workouts=listOf(w)).fromXiaomi)
        assertEquals(42,w.minutes)
        assertTrue(WatchData().isEmpty)
    }

    @Test fun rawStepsTakeTheLargestAppOfEachDayWithoutAddingApps() {
        val r=listOf(RawSteps("2026-11-15","com.mi.health",3000),RawSteps("2026-11-15","com.mi.health",5000),RawSteps("2026-11-15","android",6500),
            RawSteps("2026-11-14","android",1200),RawSteps("2026-11-13","com.mi.health",0))
        assertEquals(mapOf("2026-11-15" to 8000L,"2026-11-14" to 1200L),stepsBySource(r))
    }

    @Test fun scaleWeighInsFillOnlyNewDaysWithTheMorningValue() {
        val scale=listOf(
            ScaleWeight(at("2026-11-16T03:10:00Z"),82.437,"Mi Fitness"),   // 07:10 in Baku
            ScaleWeight(at("2026-11-16T15:00:00Z"),83.1,"Mi Fitness"),     // evening, same day: the morning one wins
            ScaleWeight(at("2026-11-17T03:00:00Z"),82.2,"Mi Fitness"),     // weighed by hand that day already
            ScaleWeight(at("2026-11-15T03:00:00Z"),82.6,"Mi Fitness"),     // taken before, then deleted by the user
            ScaleWeight(at("2026-11-18T03:00:00Z"),12.0,"Mi Fitness"),     // a bag on the scale
            ScaleWeight(at("2026-11-14T20:30:00Z"),82.9,"Mi Fitness"),     // 00:30 on the 15th in Baku — still the 15th
        )
        val add=scaleWeightsToImport(scale,existing=setOf("2026-11-17"),seen=setOf("2026-11-15"),zone=baku)
        assertEquals(listOf("2026-11-16"),add.map{it.date})
        assertEquals(82.44,add.single().kg,1e-9)
        assertEquals("2026-11-16T03:10:00Z",add.single().instant)
        assertEquals("Asia/Baku",add.single().zoneId)
        validateWeight(add.single())
        assertTrue(scaleWeightsToImport(emptyList(),emptySet(),emptySet(),baku).isEmpty())
        assertFalse(WatchData(weights=listOf(scale.first())).isEmpty)
    }
}
