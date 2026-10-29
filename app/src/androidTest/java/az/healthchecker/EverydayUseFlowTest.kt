package az.healthchecker
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.data.SettingsRepository
import az.healthchecker.domain.OverviewCards
import kotlinx.coroutines.runBlocking
import org.junit.*

class EverydayUseFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val egg="Яйцо куриное, варёное"
    @Before fun reset(){val context=ApplicationProvider.getApplicationContext<Context>();context.deleteDatabase("healthchecker.db");runBlocking{val r=SettingsRepository(context);r.cards(OverviewCards());r.waterMl(250);r.backupPromptDismissed(false)}}
    @After fun close(){scenario?.close();runBlocking{val r=SettingsRepository(ApplicationProvider.getApplicationContext<Context>());r.cards(OverviewCards());r.waterMl(250);r.backupPromptDismissed(false)}}
    private fun waitText(text:String)=app.waitUntil(90000){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun waitTag(tag:String)=app.waitUntil(90000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun begin(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор")}
    private fun search(query:String,name:String){app.onNodeWithTag("foodSearch").performTextReplacement(query);app.onNodeWithTag("foodSearch").performImeAction();app.waitUntil(90000){app.onNodeWithTag("foodResultsList").onChildren().filter(hasClickAction()).fetchSemanticsNodes().isNotEmpty()};app.onNodeWithTag("foodResultsList").performScrollToNode(hasText(name))}
    private fun find(query:String,name:String){search(query,name);app.onAllNodesWithText(name).onFirst().performClick()}
    private fun diaryCount(text:String){app.onNodeWithTag("diaryList").performScrollToNode(hasText(text));app.onNodeWithText(text).assertExists()}
    @Test fun favoritesAndBatchEntryKeepSeparateProductsAndAmounts() {
        begin();app.onNodeWithTag("logFoodButton").performScrollTo().performClick()
        search("yumurta",egg)
        app.onNodeWithContentDescription("В избранное: $egg").performClick()
        app.onNodeWithText("Избранное").performClick();waitText(egg)
        app.onAllNodesWithText(egg).onFirst().performClick()
        app.onNodeWithText("Штуки").performClick();app.onNodeWithTag("quantity").performTextReplacement("2")
        app.onNodeWithTag("addAnotherFood").performScrollTo().performClick()
        waitText("Название продукта")
        find("овсяные хлопья сух","Овсяные хлопья, сухие")
        app.onNodeWithTag("quantity").performTextReplacement("50")
        app.onNodeWithText("Записать всё").performScrollTo().performClick()
        waitText("Дневник");app.onNodeWithText("Дневник").performClick();diaryCount("2 записи")
        app.onNodeWithText("Обзор").performClick();app.onNodeWithText("345").assertExists()
        launch();waitText("Обзор");app.onNodeWithTag("logFoodButton").performScrollTo().performClick()
        find("варёное яйцо",egg)
        app.onNodeWithTag("quantity").assert(hasText("2"))
    }
    @Test fun aDraftAndSavedBreakfastSurviveRelaunchAndAllowChangingAPortion() {
        begin();app.onNodeWithTag("logFoodButton").performScrollTo().performClick();find("варёное яйцо",egg)
        app.onNodeWithText("Штуки").performClick();app.onNodeWithTag("quantity").performTextReplacement("2")
        app.onNodeWithTag("addAnotherFood").performScrollTo().performClick();waitText("Название продукта")
        launch();waitText("Обзор");waitTag("mealDraftCard");app.onNodeWithTag("mealDraftCard").performScrollTo().performClick();waitText("Приём пищи")
        app.onNodeWithTag("mealDraftList").performScrollToNode(hasText("Сохранить набор"));app.onNodeWithText("Сохранить набор").performClick();app.onNodeWithTag("mealName").performTextInput("Мой завтрак")
        app.onNodeWithTag("saveMeal").performClick();app.waitUntil(30000){app.onAllNodesWithTag("mealName").fetchSemanticsNodes().isEmpty()}
        app.onNodeWithTag("mealDraftList").performScrollToNode(hasContentDescription("Изменить порцию: $egg"));app.onNodeWithContentDescription("Изменить порцию: $egg").performClick()
        app.onNodeWithTag("quantity").performTextReplacement("3")
        app.onNodeWithText("Сохранить порцию").performScrollTo().performClick()
        app.onNodeWithTag("mealDraftList").performScrollToNode(hasText("Записать всё"));app.onNodeWithText("Записать всё").performClick();waitText("Дневник");app.onNodeWithText("Дневник").performClick();diaryCount("1 запись")
        app.onNodeWithTag("diaryList").performScrollToNode(hasText("Наборы еды"));app.onNodeWithText("Наборы еды").performClick()
        waitTag("savedMealsList");app.onNodeWithTag("savedMealsList").performScrollToNode(hasContentDescription("Добавить набор: Мой завтрак"));app.onNodeWithContentDescription("Добавить набор: Мой завтрак").performClick()
        waitText("Приём пищи");app.onNodeWithTag("mealDraftList").performScrollToNode(hasText("Записать всё"));app.onNodeWithText("Записать всё").performClick();waitText("Дневник");diaryCount("2 записи")
        app.onNodeWithText("Обзор").performClick();app.onNodeWithText("388").assertExists()
    }
    @Test fun overviewCardsCanBeHiddenWithoutHidingFoodLogging() {
        begin();app.onNodeWithText("Профиль").performClick()
        listOf("showWeight","showSteps","showTraining").forEach{tag->app.onNodeWithTag(tag).performScrollTo().performClick();app.waitUntil(30000){runCatching{app.onNodeWithTag(tag).assertIsOff();true}.getOrDefault(false)}}
        app.onNodeWithText("Обзор").performClick()
        app.onNodeWithTag("weightCard").assertDoesNotExist();app.onNodeWithTag("stepsCard").assertDoesNotExist();app.onNodeWithTag("trainingCard").assertDoesNotExist()
        app.onNodeWithTag("logFoodButton").performScrollTo().assertIsDisplayed()
    }
    @Test fun backupReminderCanBeDeferredWithoutLosingTheDiary() {
        begin();app.onNodeWithText("+250 мл").performScrollTo().performClick()
        waitTag("backupSetup");app.onNodeWithText("Позже").performScrollTo().performClick()
        app.waitUntil(30000){app.onAllNodesWithTag("backupSetup").fetchSemanticsNodes().isEmpty()}
        launch();waitText("Обзор")
        app.onNodeWithText("250 мл").assertExists()
        app.waitUntil(30000){app.onAllNodesWithTag("backupSetup").fetchSemanticsNodes().isEmpty()}
        app.onNodeWithTag("saveStatus").performScrollTo().assertExists()
    }
    @Test fun aSavedMealCanBeCreatedFromDiaryEntriesAndRenamed() {
        begin();app.onNodeWithTag("logFoodButton").performScrollTo().performClick();find("оливковое масло","Масло оливковое")
        app.onNodeWithTag("quantity").performTextReplacement("7")
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick();waitText("Дневник")
        app.onNodeWithTag("diaryList").performScrollToNode(hasText("Наборы еды"));app.onNodeWithText("Наборы еды").performClick()
        app.onNodeWithTag("savedMealsList").performScrollToNode(hasText("Новый набор из дневника"));app.onNodeWithText("Новый набор из дневника").performClick()
        app.onNodeWithTag("mealName").performTextInput("Масло на ужин")
        app.onNodeWithText("Масло оливковое").performScrollTo().performClick()
        app.onNodeWithTag("saveMeal").performScrollTo().performClick();waitTag("savedMealsList")
        app.onNodeWithTag("savedMealsList").performScrollToNode(hasText("Изменить"));app.onNodeWithText("Изменить").performClick()
        app.onNodeWithTag("mealName").performTextReplacement("Заправка")
        app.onNodeWithTag("saveMeal").performScrollTo().performClick();waitTag("savedMealsList")
        app.onNodeWithTag("savedMealsList").performScrollToNode(hasContentDescription("Добавить набор: Заправка"));app.onNodeWithContentDescription("Добавить набор: Заправка").performClick()
        waitTag("mealDraftList");app.onNodeWithTag("mealDraftList").performScrollToNode(hasText("Записать всё"));app.onNodeWithText("Записать всё").performClick()
        waitText("Дневник");diaryCount("2 записи");app.onNodeWithText("Обзор").performClick();app.onNodeWithText("124").assertExists()
    }
}
