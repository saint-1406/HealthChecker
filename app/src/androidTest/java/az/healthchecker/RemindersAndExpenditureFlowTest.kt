package az.healthchecker
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import az.healthchecker.data.HealthRepository
import az.healthchecker.domain.*
import az.healthchecker.reminders.ReminderActionReceiver
import az.healthchecker.reminders.Reminders
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.time.*
import java.util.UUID

/** Reminders fire as notifications whose buttons write to the diary; the expenditure screen reads the logged weeks. */
class RemindersAndExpenditureFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val nm get()=context.getSystemService(NotificationManager::class.java)
    private fun waitTag(tag:String)=app.waitUntil(90000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}

    @Before fun reset() {
        context.deleteDatabase("healthchecker.db")
        context.getSharedPreferences("reminders",Context.MODE_PRIVATE).edit().clear().commit()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        nm.cancelAll()
    }
    @After fun close(){scenario?.close();context.getSharedPreferences("reminders",Context.MODE_PRIVATE).edit().clear().commit();nm.cancelAll()}

    @Test fun dueRemindersNotifyAndTheirButtonsLogWaterAndDoses()=runBlocking {
        val repo=HealthRepository(context)
        val now=LocalTime.now().withSecond(0).withNano(0)
        val med=Medication("med:vitd","Витамин D",form=MedForm.CAPSULE,times=listOf(now.minusMinutes(1).toString()))
        repo.saveMedication(med)
        // Water and the dose were due a minute ago; the alarm "went off" now.
        Reminders.save(context,ReminderSettings(medications=true,water=true,waterFrom=now.minusMinutes(1).toString(),waterTo=now.plusMinutes(1).toString(),waterEveryHours=6))
        Thread.sleep(1500)
        context.getSharedPreferences("reminders",Context.MODE_PRIVATE).edit().putString("handled",LocalDateTime.now().minusMinutes(5).toString()).commit()
        Reminders.fire(context)
        val shown=nm.activeNotifications.map{it.id}.toSet()
        val doseId=Reminders.notificationId(ReminderSlot(ReminderKind.MEDICATION,LocalDateTime.now(),"med:vitd",0))
        val waterId=Reminders.notificationId(ReminderSlot(ReminderKind.WATER,LocalDateTime.now()))
        Assert.assertTrue("shown: $shown",doseId in shown&&waterId in shown)
        // "Taken" and "+250 ml" from the notification.
        val today=LocalDate.now().toString()
        Reminders.act(context,Reminders.ACTION_TAKEN,"med:vitd",today)
        Reminders.act(context,Reminders.ACTION_WATER,null,today)
        val s=repo.exportSnapshot()
        Assert.assertEquals(1,s.medicationIntakes.count{it.medicationId=="med:vitd"&&it.localDate==today})
        Assert.assertEquals(250.0,s.entries.filter{it.localDate==today}.sumOf{it.waterMl},0.1)
        // A dose already marked is not reminded again.
        nm.cancelAll()
        context.getSharedPreferences("reminders",Context.MODE_PRIVATE).edit().putString("handled",LocalDateTime.now().minusMinutes(5).toString()).commit()
        Reminders.fire(context)
        Assert.assertFalse(doseId in nm.activeNotifications.map{it.id})
        // The action receiver is declared and reachable.
        context.sendBroadcast(Intent(context,ReminderActionReceiver::class.java).setAction("unknown"))
    }

    @Test fun remindersScreenOffersToLiftBatteryLimits() {
        runBlocking{HealthRepository(context).finishOnboarding()}
        Reminders.save(context,ReminderSettings(weigh=true))
        scenario=ActivityScenario.launch(MainActivity::class.java)
        app.waitUntil(90000){app.onAllNodesWithText("Профиль").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithText("Профиль").performClick()
        app.onNodeWithTag("open-profile-reminders").performScrollTo().performClick()
        waitTag("remindersScreen")
        // A fresh install is battery-optimised: the screen says so and offers the system dialog.
        app.onNodeWithTag("batteryPanel").assertExists()
        app.onNodeWithTag("allowBackground").assertExists()
        // With every reminder off there is nothing to warn about.
        app.onNodeWithTag("remindWeigh").performClick()
        app.waitUntil(10000){app.onAllNodesWithTag("batteryPanel").fetchSemanticsNodes().isEmpty()}
    }

    @Test fun expenditureScreenShowsTheEstimateFromFourWeeks() {
        runBlocking {
            val repo=HealthRepository(context)
            repo.saveProfile(Profile("male",35,180.0,90.0,85.0,1.375));repo.saveGoals(DailyGoals("2026-01-01",2000.0));repo.finishOnboarding()
            val today=LocalDate.now();val zone=ZoneId.systemDefault()
            val food=Food("own:meal","Обед",per100g=Nutrients(200.0,10.0,5.0,25.0))
            for(d in 1..28){val date=today.minusDays(d.toLong())
                repo.upsertEntry(makeEntry(UUID.randomUUID().toString(),food,Quantity(1000.0,Measure.GRAMS),date.toString(),null,date.atTime(13,0).atZone(zone).toInstant(),zone))}
            for(d in 90 downTo 0){val date=today.minusDays(d.toLong());repo.saveWeight(makeWeight(date.toString(),85.0+d*0.5/7,date.atTime(7,0).atZone(zone).toInstant(),zone))}
        }
        scenario=ActivityScenario.launch(MainActivity::class.java)
        // The home card offers the new target: 2 000 kcal eaten while losing 0,5 kg a week means ≈ 2 550 spent.
        waitTag("expenditureCard");app.onNodeWithTag("expenditureCard").performScrollTo().performClick()
        waitTag("expenditureScreen")
        app.onNodeWithTag("expenditureEstimate").assertTextContains("2",substring=true)
        app.onNodeWithTag("applyAdaptive").performScrollTo().performClick()
        app.waitUntil(30000){runBlocking{HealthRepository(context).exportSnapshot()}.day(LocalDate.now().toString()).goals?.kcal?.let{it in 2400.0..2700.0}==true}
    }
}
