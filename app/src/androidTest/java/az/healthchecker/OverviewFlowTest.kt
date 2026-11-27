package az.healthchecker
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.*

/** "Your day": the workout, food and water, weight, watch and week cards, and both ways into the day's energy sheet. */
class OverviewFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit()}
    @After fun close(){scenario?.close()}
    private fun waitTag(tag:String)=app.waitUntil(90000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun waitText(text:String)=app.waitUntil(90000){app.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}

    @Test fun cardsFollowTheMockupAndBothFoodDetailsOpenTheSameSheet() {
        scenario=ActivityScenario.launch(MainActivity::class.java)
        waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick()
        waitTag("trainingCard")
        app.onNodeWithTag("trainingCard").assert(hasText("Тренировки пока нет",substring=true))
        // Water: the glass button adds to the line next to it.
        app.onNodeWithTag("waterLine",useUnmergedTree=true).assertTextContains("0",substring=true)
        app.onNodeWithTag("waterButton").performClick()
        app.waitUntil(30000){runCatching{app.onNodeWithTag("waterLine",useUnmergedTree=true).assertTextContains("250",substring=true)}.isSuccess}
        // The food part of the card opens the day's energy; the close button shuts it.
        app.onNodeWithTag("energyCard").performClick()
        waitTag("energySheet");app.onNodeWithText("Энергия за день").assertExists()
        app.onNodeWithTag("closeEnergy").performClick()
        app.waitUntil(10000){app.onAllNodesWithTag("energySheet").fetchSemanticsNodes().isEmpty()}
        // The week card at the bottom scrolls fully into view; its "Day balance" opens the same sheet.
        app.onNodeWithTag("balanceRow").performScrollTo().assertIsDisplayed()
        app.onNodeWithTag("weekAverage",useUnmergedTree=true).assertExists()
        app.onNodeWithTag("balanceRow").performClick()
        waitTag("energySheet")
        app.onNodeWithTag("closeEnergy").performClick()
        app.waitUntil(10000){app.onAllNodesWithTag("energySheet").fetchSemanticsNodes().isEmpty()}
        // The watch card says "today" only for today.
        app.onNodeWithTag("watchCard").performScrollTo()
        app.onNodeWithTag("watchTitle",useUnmergedTree=true).assertTextEquals("Часы сегодня")
        app.onNodeWithContentDescription("Предыдущий день").performClick()
        app.waitUntil(10000){runCatching{app.onNodeWithTag("watchTitle",useUnmergedTree=true).assertTextContains("Часы · ",substring=true)}.isSuccess}
        // "Log food" still opens the food search.
        app.onNodeWithTag("logFoodButton").performScrollTo().performClick()
        waitTag("foodSearch")
    }
}
