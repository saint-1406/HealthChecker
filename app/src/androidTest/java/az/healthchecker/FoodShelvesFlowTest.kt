package az.healthchecker
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.*

/** Food search shelves take you straight to fast food or coffee, and the "logged" notice can be flicked away. */
class FoodShelvesFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit()}
    @After fun close(){scenario?.close()}
    private fun waitText(text:String,substring:Boolean=false)=app.waitUntil(90000){app.onAllNodesWithText(text,substring=substring).fetchSemanticsNodes().isNotEmpty()}
    private fun waitTag(tag:String)=app.waitUntil(90000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun begin(){scenario=ActivityScenario.launch(MainActivity::class.java);waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор")}

    @Test fun shelvesNarrowTheSearchAndTheNoticeSwipesAway() {
        begin();app.onNodeWithText("Дневник").performClick()
        waitTag("logFoodButton");app.onNodeWithTag("logFoodButton").performScrollTo().performClick()
        waitTag("categoryShelves")
        // Fast food: KFC's menu is right there without typing.
        app.onNodeWithTag("categoryShelves").performScrollToNode(hasTestTag("category-fastfood"))
        app.onNodeWithTag("category-fastfood").performClick()
        waitTag("categoryTitle");app.onNodeWithTag("categoryTitle").assertTextContains("Фастфуд",substring=true)
        waitText("· KFC",substring=true)
        // A name narrows the shelf: "твистер" in fast food finds the KFC twister.
        app.onNodeWithTag("foodSearch").performTextReplacement("твистер острый");app.onNodeWithTag("foodSearch").performImeAction()
        waitText("Твистер острый · KFC")
        // The same word on the coffee shelf finds nothing.
        app.onNodeWithTag("categoryShelves").performScrollToNode(hasTestTag("category-coffee"))
        app.onNodeWithTag("category-coffee").performClick()
        waitText("Ничего не найдено",substring=true)
        // Tapping the chosen shelf again goes back to all foods.
        app.onNodeWithTag("category-coffee").performClick()
        waitText("Твистер острый · KFC")
        app.onAllNodesWithText("Твистер острый · KFC").onFirst().performClick()
        waitTag("savePortion");app.onNodeWithTag("savePortion").performScrollTo().performClick()
        // The "logged" notice leaves with a flick sideways, without pressing its close button.
        waitTag("noticeSwipe")
        app.onNodeWithTag("noticeSwipe").performTouchInput{swipeLeft(startX=centerX,endX=left-width)}
        app.waitUntil(10000){app.onAllNodesWithTag("noticeSwipe").fetchSemanticsNodes().isEmpty()}
    }
}
