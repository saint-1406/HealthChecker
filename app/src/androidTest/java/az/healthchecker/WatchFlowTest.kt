package az.healthchecker
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.time.*

/** Sample "watch" data written to Health Connect shows up on the watch screen, the steps screen and the home card. */
class WatchFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val zone=ZoneId.systemDefault()
    private fun shell(cmd:String){InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).close()}
    private val all=listOf("READ_STEPS","READ_SLEEP","READ_EXERCISE","READ_HEART_RATE","READ_WEIGHT","WRITE_STEPS","WRITE_SLEEP","WRITE_EXERCISE","WRITE_HEART_RATE","WRITE_WEIGHT")
    private fun waitTag(tag:String)=app.waitUntil(90000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun waitText(text:String)=app.waitUntil(90000){app.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}

    @Before fun setUp() {
        context.deleteDatabase("healthchecker.db")
        context.getSharedPreferences("watch",Context.MODE_PRIVATE).edit().clear().commit()
        all.forEach{shell("pm grant ${context.packageName} android.permission.health.$it")}
        Thread.sleep(1500)
        runBlocking {
            val c=HealthConnectClient.getOrCreate(context)
            clear(c)
            val today=LocalDate.now();val yesterday=today.minusDays(1);val run=yesterday.atTime(18,0).atZone(zone).toInstant()
            val morning=today.atTime(0,30).atZone(zone).toInstant()
            val m=Metadata.manualEntry()
            c.insertRecords(listOf<Record>(
                StepsRecord(morning,null,morning.plusSeconds(1800),null,8421,m),
                StepsRecord(today.minusDays(1).atTime(12,0).atZone(zone).toInstant(),null,today.minusDays(1).atTime(13,0).atZone(zone).toInstant(),null,5300,m),
                SleepSessionRecord(today.minusDays(2).atTime(23,30).atZone(zone).toInstant(),null,yesterday.atTime(7,0).atZone(zone).toInstant(),null,metadata=m,
                    stages=listOf(SleepSessionRecord.Stage(yesterday.atTime(3,0).atZone(zone).toInstant(),yesterday.atTime(3,30).atZone(zone).toInstant(),SleepSessionRecord.STAGE_TYPE_AWAKE))),
                ExerciseSessionRecord(startTime=run,startZoneOffset=null,endTime=run.plusSeconds(40*60),endZoneOffset=null,metadata=m,exerciseType=ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,title=null),
                WeightRecord(yesterday.atTime(7,5).atZone(zone).toInstant(),null,androidx.health.connect.client.units.Mass.kilograms(81.4),m),
                WeightRecord(yesterday.atTime(19,0).atZone(zone).toInstant(),null,androidx.health.connect.client.units.Mass.kilograms(82.3),m),
                HeartRateRecord(morning.plusSeconds(120),null,morning.plusSeconds(600),null,listOf(HeartRateRecord.Sample(morning.plusSeconds(120),120),HeartRateRecord.Sample(morning.plusSeconds(300),150),HeartRateRecord.Sample(morning.plusSeconds(500),60)),m),
            ))
        }
    }
    private suspend fun clear(c:HealthConnectClient)=listOf<kotlin.reflect.KClass<out Record>>(StepsRecord::class,SleepSessionRecord::class,ExerciseSessionRecord::class,HeartRateRecord::class,WeightRecord::class).forEach{c.deleteRecords(it,TimeRangeFilter.after(Instant.EPOCH))}
    // Revoking the permissions would kill the app under test, so only the sample data goes: other step tests see none.
    @After fun close(){scenario?.close();runBlocking{clear(HealthConnectClient.getOrCreate(context))}}

    @Test fun watchDataReachesTheScreens() {
        scenario=ActivityScenario.launch(MainActivity::class.java)
        waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор")
        // Home card: Health Connect's total replaces the phone's count.
        val steps=az.healthchecker.ui.steps.stepsText(8421)
        waitText(steps)
        app.onNodeWithTag("stepsCard").performScrollTo().performClick();waitTag("stepsSource")
        app.onNodeWithTag("stepsSource").assertTextEquals("с часов через Health Connect")
        app.onNodeWithTag("openWatch").performScrollTo().performClick()
        waitTag("watchScreen");waitTag("watchStepsToday")
        app.onNodeWithTag("watchStepsToday").assertTextEquals(steps)
        app.onNodeWithTag("watchSleep").performScrollTo();app.onNodeWithText("7 ч 0 мин").assertExists()
        app.onNodeWithTag("watchWorkouts").performScrollTo();app.onNodeWithText("Бег").assertExists();app.onNodeWithText("40 мин").assertExists()
        app.onNodeWithText("150",substring=false).assertExists()
        // The scale's morning weigh-in became yesterday's entry in the weight log; the evening one did not.
        app.onNodeWithTag("watchWeight").performScrollTo();app.onNodeWithText(az.healthchecker.ui.number(81.4,1)+" кг").assertExists()
        waitTag("weightsAdded")
        val yesterday=LocalDate.now().minusDays(1).toString()
        val log=runBlocking{az.healthchecker.data.HealthRepository(context).exportSnapshot()}.weights
        Assert.assertEquals(listOf(yesterday to 81.4),log.map{it.date to it.kg})
        // Deleted from the log, it does not come back on the next read.
        runBlocking{az.healthchecker.data.HealthRepository(context).deleteWeight(yesterday)}
        app.onNodeWithTag("refreshWatch").performScrollTo().performClick()
        Thread.sleep(3000);app.waitForIdle()
        Assert.assertTrue(runBlocking{az.healthchecker.data.HealthRepository(context).exportSnapshot()}.weights.isEmpty())
    }
}
