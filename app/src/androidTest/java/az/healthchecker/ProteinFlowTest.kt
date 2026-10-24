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
class ProteinFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit()}
    @After fun close(){scenario?.close()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun skipOnboarding(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")}
    private fun scrollTo(tag:String)=app.onNodeWithTag("proteinList").performScrollToNode(hasTestTag(tag))

    @Test fun proteinScreenFindsFoodsByAmountSortsBothWaysAndLogsOne() {
        skipOnboarding()
        app.onNodeWithTag("proteinCard").performClick()
        app.onNodeWithTag("proteinToday").assertTextEquals("0 г")
        scrollTo("proteinGrams");app.onNodeWithTag("proteinGrams").performTextInput("25")
        // Everyday foods with at least 25 g per 100 g: chicken, turkey, pumpkin seeds, peanuts.
        waitText("Найдено: 6")
        scrollTo("proteinFood")
        app.onAllNodesWithTag("proteinFood").onFirst().assert(hasText("Куриная грудка без кожи, запечённая",substring=true)).assert(hasText("31,0 г",substring=true))
        scrollTo("sortLow");app.onNodeWithTag("sortLow").performClick()
        scrollTo("proteinFood")
        app.waitUntil(10000){runCatching{app.onAllNodesWithTag("proteinFood").onFirst().assert(hasText("Куриное бедро без кожи, отварное",substring=true))}.isSuccess}
        app.onAllNodesWithTag("proteinFood").onFirst().performClick()
        app.onNodeWithTag("quantity").performTextReplacement("100")
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick()
        // The form returns to the protein screen, which now counts the logged food.
        app.waitUntil(30000){app.onAllNodesWithTag("proteinToday").fetchSemanticsNodes().isNotEmpty()}
        app.waitUntil(10000){runCatching{app.onNodeWithTag("proteinToday").assertTextEquals("25 г")}.isSuccess}
        app.onNodeWithTag("proteinAverage").assertTextEquals("В среднем 25 г в день")
        scrollTo("proteinPeriod30");app.onNodeWithTag("proteinPeriod30").performClick()
        app.onNodeWithText("Главные источники за 30 дн.").assertExists()
    }
}
