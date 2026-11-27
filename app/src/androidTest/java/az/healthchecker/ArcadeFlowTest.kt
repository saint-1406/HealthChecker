package az.healthchecker
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The "Arcade" layout: chosen in Appearance, kept across restarts, reset to standard, and its overview's actions. */
class ArcadeFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    // The layout lives in the settings file, which other tests do not reset: clear it before and after.
    // Written through the repository, so the app's in-memory settings forget the layout too.
    private fun clearLook(){kotlinx.coroutines.runBlocking{az.healthchecker.data.SettingsRepository(context).appearance(az.healthchecker.domain.Appearance())}}
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit();clearLook()}
    @After fun close(){scenario?.close();clearLook()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun waitTag(tag:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun waitLine(tag:String,text:String)=app.waitUntil(30000){runCatching{app.onNodeWithTag(tag,useUnmergedTree=true).assertTextContains(text,substring=true)}.isSuccess}
    private fun skipOnboarding(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")}
    private fun openAppearance(arcadeNow:Boolean){
        if(arcadeNow)app.onNodeWithTag("arcadeTab-3").performClick() else app.onNodeWithText("Профиль").performClick()
        app.onNodeWithTag("open-profile-look").performScrollTo().performClick()
        app.onNodeWithTag("openAppearance").performScrollTo().performClick()
        waitTag("layoutPicker")
    }
    private fun chooseArcade(){
        openAppearance(false)
        app.onNodeWithTag("layout-standard").performScrollTo().assertIsSelected()
        app.onNodeWithTag("layout-arcade").performScrollTo().performClick().assertIsSelected()
        app.onNodeWithContentDescription("Назад").performClick()
        app.onNodeWithContentDescription("Назад").performClick()
        // Applied at once: the square tab strip replaces the standard bar.
        waitTag("arcadeTab-0")
    }

    @Test fun arcadeIsChosenKeptAfterARestartAndResetBringsTheStandardLayout() {
        skipOnboarding()
        chooseArcade()
        // A new start opens straight in Arcade.
        launch()
        waitTag("arcadeTitle")
        app.onNodeWithTag("arcadeTab-0").assertIsSelected()
        // "Restore the default look" also brings the standard layout back.
        openAppearance(true)
        app.onNodeWithTag("layout-arcade").performScrollTo().assertIsSelected()
        app.onNodeWithTag("resetAppearance").performScrollTo().performClick()
        app.waitUntil(10000){runCatching{app.onNodeWithTag("layout-standard").assertIsSelected()}.isSuccess}
        app.onNodeWithContentDescription("Назад").performClick()
        app.onNodeWithContentDescription("Назад").performClick()
        waitText("Обзор")
        app.onAllNodesWithTag("arcadeTab-0").assertCountEquals(0)
    }

    @Test fun arcadeOverviewLogsWaterWithUndoWeightFoodAndSwitchesTheDate() {
        skipOnboarding()
        chooseArcade()
        app.onNodeWithTag("arcadeTab-0").performClick()
        waitTag("arcadeTitle")
        app.onNodeWithTag("trainingCard").assert(hasText("Тренировки пока нет",substring=true))
        // "+250 ml" counts at once and offers an undo that takes it back.
        app.onNodeWithTag("waterButton").performScrollTo().performClick()
        waitLine("waterLine","250")
        app.onNodeWithTag("undoNotice").performClick()
        app.waitUntil(30000){runCatching{app.onNodeWithTag("waterLine",useUnmergedTree=true).assertTextEquals("0 мл")}.isSuccess}
        // The food summary opens the day's energy.
        app.onNodeWithTag("energyCard").performScrollTo().performClick()
        waitTag("energySheet");app.onNodeWithTag("closeEnergy").performClick()
        app.waitUntil(10000){app.onAllNodesWithTag("energySheet").fetchSemanticsNodes().isEmpty()}
        // "+ Entry" → Weight logs a weigh-in shown large in the weight section.
        app.onNodeWithTag("quickEntry").performClick()
        waitTag("entrySheet");app.onNodeWithTag("entryOption-2").performClick()
        waitTag("weightInput");app.onNodeWithTag("weightInput").performTextInput("80,4")
        app.onNodeWithTag("saveWeight").performClick()
        waitLine("weightLatest","80,4")
        // "7 days" brings the week into view; its balance opens the same sheet.
        app.onNodeWithTag("quickWeek").performClick()
        app.waitUntil(10000){runCatching{app.onNodeWithTag("balanceRow").assertIsDisplayed()}.isSuccess}
        app.onNodeWithTag("weekAverage",useUnmergedTree=true).assertExists()
        // The date box switches to yesterday; the watch section names the date.
        val yesterday=LocalDate.now().minusDays(1).toString()
        app.onNodeWithTag("arcadeDate").performClick()
        app.onNodeWithTag("arcadeDay-$yesterday").performClick()
        app.waitUntil(10000){runCatching{app.onNodeWithTag("watchTitle",useUnmergedTree=true).assertTextContains("ЧАСЫ · ",substring=true)}.isSuccess}
        // "Log food" still opens the food search.
        app.onNodeWithTag("quickDay").performClick()
        app.onNodeWithTag("logFoodButton").performScrollTo().performClick()
        waitTag("foodSearch")
    }

    @Test fun arcadeDiaryGroupsEntriesByPartOfTheDay() {
        skipOnboarding()
        chooseArcade()
        app.onNodeWithTag("arcadeTab-0").performClick()
        app.onNodeWithTag("waterButton").performScrollTo().performClick()
        waitLine("waterLine","250")
        app.onNodeWithTag("arcadeTab-1").performClick()
        app.onNodeWithTag("diaryList").performScrollToNode(hasTestTag("mealGroup-WATER"))
        app.onNodeWithTag("mealGroup-WATER").assert(hasAnyDescendant(hasText("250 мл")))
        app.onNodeWithContentDescription("Изменить запись: Вода питьевая",substring=true).assertExists()
    }
}
