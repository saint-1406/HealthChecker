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
class DiaryFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    // Each test starts from a fresh diary; relaunching closes the activity so data must come back from disk.
    @Before fun reset(){ApplicationProvider.getApplicationContext<Context>().deleteDatabase("healthchecker.db")}
    @After fun close(){scenario?.close()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun skipOnboarding(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")}
    private fun addFood(query:String,name:String,fill:()->Unit) {
        app.onNodeWithContentDescription("Добавить еду").performClick()
        app.onNodeWithTag("foodSearch").performTextInput(query)
        waitText(name,30000)
        app.onAllNodesWithText(name).onFirst().performClick()
        fill()
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick()
        waitText("Записать еду")
    }
    @Test fun eggsAndOatsSumEditAndSurviveRestart() {
        skipOnboarding()
        app.onNodeWithText("Дневник").performClick()
        addFood("yumurta","Яйцо куриное, варёное"){app.onNodeWithText("Штуки").performClick();app.onNodeWithTag("quantity").performTextReplacement("2")}
        addFood("овсяные хлопья сух","Овсяные хлопья, сухие"){app.onNodeWithTag("quantity").performTextReplacement("50")}
        app.onNodeWithText("2 записи").assertExists()
        app.onNodeWithText("Обзор").performClick()
        // 2 × 50 г яйца × 155/100 + 50 г хлопьев × 379/100 = 344,5 ккал.
        app.onNodeWithText("345").assertExists()
        app.onNodeWithText("Дневник").performClick()
        app.onAllNodesWithText("Овсяные хлопья, сухие").onFirst().performClick()
        app.onNodeWithTag("quantity").performTextReplacement("100,0")
        app.onNodeWithText("Сохранить изменения").performScrollTo().performClick()
        waitText("Записать еду")
        app.onNodeWithText("2 записи").assertExists()
        launch()
        waitText("Обзор")
        app.onNodeWithText("534").assertExists()
    }
    @Test fun deletedEntryCanBeRestored() {
        skipOnboarding()
        app.onNodeWithText("Дневник").performClick()
        app.onNodeWithText("+250 мл воды").performClick()
        waitText("Вода питьевая")
        app.onNodeWithContentDescription("Удалить Вода питьевая").performClick()
        waitText("Запись удалена")
        app.onNodeWithText("0 записей").assertExists()
        app.onNodeWithText("Отменить").performClick()
        waitText("1 запись")
        app.onNodeWithText("Обзор").performClick()
        app.onNodeWithText("250 мл").assertExists()
    }
    @Test fun profileKeepsInitialWeightAndManualGoal() {
        launch();waitText("Начать без расчёта")
        app.onNodeWithText("Возраст, лет").performScrollTo().performTextInput("30")
        app.onNodeWithText("Рост, см").performScrollTo().performTextInput("180")
        app.onNodeWithText("Текущий вес, кг").performScrollTo().performTextInput("80")
        app.onNodeWithText("Рассчитать и подставить калории").performScrollTo().performClick()
        app.onNodeWithText("Калории, ккал").assert(hasText("2136"))
        app.onNodeWithText("Сохранить и начать").performScrollTo().performClick()
        waitText("Обзор")
        app.onNodeWithText("из 2136 ккал").assertExists()
        app.onNodeWithText("Профиль").performClick()
        app.onNodeWithText("Текущий вес, кг").performScrollTo().performTextReplacement("75")
        app.onNodeWithText("Сохранить").performScrollTo().performClick()
        waitText("Сохранено")
        launch();waitText("Обзор")
        app.onNodeWithText("Профиль").performClick()
        app.onNodeWithText("Текущий вес, кг").assert(hasText("75"))
        app.onNodeWithText("Начальный вес: 80,0 кг").assertExists()
        app.onNodeWithText("Калории, ккал").assert(hasText("2136"))
    }
}
