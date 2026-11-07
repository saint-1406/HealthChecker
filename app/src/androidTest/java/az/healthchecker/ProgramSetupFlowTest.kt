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
class ProgramSetupFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    @Before fun reset(){ApplicationProvider.getApplicationContext<Context>().deleteDatabase("healthchecker.db")}
    @After fun close(){scenario?.close()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}
    private fun waitTag(tag:String,timeout:Long=30000)=app.waitUntil(timeout){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun tap(tag:String)=app.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun newUserSetsUpTheProgramAndGetsTargets() {
        launch();waitText("Начать без расчёта")
        tap("startSetup");waitTag("setupStep0")
        tap("sex-male")
        app.onNodeWithTag("setupAge").performTextReplacement("30")
        app.onNodeWithTag("setupHeight").performTextReplacement("180")
        app.onNodeWithTag("setupWeight").performTextReplacement("80")
        app.onNodeWithTag("setupNext").performClick();waitTag("setupStep1")
        tap("limit-KNEE");app.onNodeWithTag("setupNext").performClick();waitTag("setupStep2")
        tap("equip-DUMBBELLS");app.onNodeWithTag("setupNext").performClick();waitTag("setupStep3")
        tap("goal-CUT");app.onNodeWithTag("setupNext").performClick();waitTag("setupStep4")
        tap("activity-1.55");tap("level-1");app.onNodeWithTag("setupNext").performClick();waitTag("setupStep5")
        tap("days-3");tap("minutes-30");app.onNodeWithTag("setupNext").performClick();waitTag("setupStep6")
        // Mifflin–St Jeor: 1780 × 1,55 = 2759 kcal, −20 % for fat loss ≈ 2210 kcal.
        waitTag("planKcal");app.onNodeWithTag("planKcal").assertTextEquals("2210 ккал")
        app.onNodeWithText("не медицинская консультация",substring=true).assertExists()
        tap("applySetup")
        waitTag("programScreen")
        app.onNodeWithTag("back").performClick()
        waitTag("profileHub");app.onNodeWithTag("todayTargets").assert(hasText("2210",substring=true))
        app.onNodeWithText("Обзор").performClick()
        waitText("из 2210 ккал")
    }
    @Test fun skippingKeepsTheSetupInTheProfile() {
        launch();waitText("Начать без расчёта")
        app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор")
        app.onNodeWithText("Профиль").performClick()
        waitTag("profileHub");tap("startSetup");waitTag("setupStep0")
        app.onNodeWithTag("back").performClick();waitTag("profileHub")
        tap("open-inventory");waitTag("inventoryScreen");app.onNodeWithTag("newDishCell").assertExists()
    }
}
