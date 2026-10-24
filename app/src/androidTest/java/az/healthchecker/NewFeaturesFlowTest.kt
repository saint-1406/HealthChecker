package az.healthchecker
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
class NewFeaturesFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    // Fresh diary and the default Russian interface for every test, also after a failed language test.
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit()}
    @After fun close(){scenario?.close();context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun waitTag(tag:String,timeout:Long=30000)=app.waitUntil(timeout){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun skipOnboarding(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")}
    private fun textOf(tag:String)=app.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("")

    @Test fun medicationCardAndTakenMarks() {
        skipOnboarding()
        app.onNodeWithText("Дневник").performClick()
        app.onNodeWithTag("diaryList").performScrollToNode(hasTestTag("addMedication"))
        app.onNodeWithTag("addMedication").performClick()
        waitTag("medName")
        app.onNodeWithText("Витамин").performClick()
        app.onNodeWithTag("medName").performTextInput("Витамин D3")
        app.onNodeWithText("Капсулы").performClick()
        app.onNodeWithTag("medPerDay").performTextReplacement("2")
        app.onNodeWithTag("saveMedication").performScrollTo().performClick()
        app.waitUntil(30000){app.onAllNodesWithTag("diaryList").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithTag("diaryList").performScrollToNode(hasTestTag("takeMedication"))
        waitText("Принято: 0 из 2")
        app.onNodeWithText("Витамин D3").assertExists()
        app.onNodeWithTag("takeMedication").performClick()
        waitText("Принято: 1 из 2")
        // The mark survives closing the app.
        launch();waitText("Обзор");app.onNodeWithText("Дневник").performClick()
        app.onNodeWithTag("diaryList").performScrollToNode(hasTestTag("takeMedication"));waitText("Принято: 1 из 2")
    }
    @Test fun manualStepsAndTheirCalories() {
        launch();waitText("Начать без расчёта")
        app.onNodeWithText("Возраст, лет").performScrollTo().performTextInput("30")
        app.onNodeWithText("Рост, см").performScrollTo().performTextInput("180")
        app.onNodeWithText("Текущий вес, кг").performScrollTo().performTextInput("80")
        app.onNodeWithText("Сохранить и начать").performScrollTo().performClick()
        waitText("Обзор")
        app.onNodeWithTag("stepsCard").performScrollTo().performClick()
        waitTag("manualSteps")
        app.onNodeWithTag("manualSteps").performScrollTo().performTextInput("10000")
        app.onNodeWithTag("saveSteps").performClick()
        app.waitUntil(30000){textOf("stepsTotal").filter(Char::isDigit)=="10000"}
        // 10 000 steps × 0.747 m at 5 km/h × 3.8 MET × 80 kg ≈ 454 kcal, 7.5 km.
        assertEquals("≈ 454 ккал · 7,5 км",textOf("stepsKcal"))
    }
    @Test fun energySheetShowsFiberForTheDayAndEachEntry() {
        skipOnboarding()
        app.onNodeWithText("Дневник").performClick()
        app.onNodeWithContentDescription("Добавить еду").performClick()
        app.onNodeWithTag("foodSearch").performTextInput("овсяные хлопья")
        waitText("Овсяные хлопья, сухие",30000);app.onAllNodesWithText("Овсяные хлопья, сухие").onFirst().performClick()
        app.onNodeWithTag("quantity").performTextReplacement("50")
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick()
        waitText("Записать еду")
        app.onNodeWithTag("diaryList").performScrollToNode(hasText("Б 6,6 · Ж 3,3 · У 33,9 · Кл 5,1 г",substring=true))
        app.onAllNodesWithText("Б 6,6 · Ж 3,3 · У 33,9 · Кл 5,1 г",substring=true).onFirst().assertExists()
        app.onNodeWithText("Обзор").performClick()
        app.onNodeWithTag("energyCard").performClick()
        waitTag("energySheet")
        app.onNodeWithText("Клетчатка").assertExists()
        app.onNodeWithText("5,1 г").assertExists()
    }
    @Test fun languageSwitchesTheWholeInterface() {
        skipOnboarding()
        app.onNodeWithText("Профиль").performClick()
        app.onNodeWithTag("lang-en").performScrollTo().performClick()
        waitText("Overview")
        app.onNodeWithText("Diary").performClick()
        waitText("Log food")
        app.onNodeWithContentDescription("Add food").performClick()
        app.onNodeWithTag("foodSearch").performTextInput("варёное яйцо")
        waitText("Egg, whole, cooked, hard-boiled",30000)
        launch();waitText("Overview")
        app.onNodeWithText("Profile").performClick()
        app.onNodeWithTag("lang-ru").performScrollTo().performClick()
        waitText("Обзор")
    }
}
