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
class DishAndTrainingFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    @Before fun reset(){ApplicationProvider.getApplicationContext<Context>().deleteDatabase("healthchecker.db")}
    @After fun close(){scenario?.close()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun ingredient(query:String,name:String,fill:()->Unit) {
        app.onNodeWithTag("addIngredient").performScrollTo().performClick()
        app.onNodeWithTag("foodSearch").performTextInput(query)
        waitText(name,30000);app.onAllNodesWithText(name).onFirst().performClick()
        fill()
        app.onNodeWithText("Добавить в блюдо").performScrollTo().performClick()
        waitText("Ингредиенты")
    }
    @Test fun dishFromIngredientsThenEatenPortion() {
        launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")
        app.onNodeWithText("Дневник").performClick()
        app.onNodeWithContentDescription("Добавить еду").performClick()
        app.onNodeWithTag("createDish").performClick()
        app.onNodeWithTag("dishName").performTextInput("Курица с картошкой")
        ingredient("куриное филе","Куриное филе без кожи, сырое"){app.onNodeWithTag("quantity").performTextReplacement("500")}
        ingredient("картофель сырой","Картофель сырой с кожурой"){app.onNodeWithText("Штуки").performClick();app.onNodeWithTag("quantity").performTextReplacement("5")}
        // 500 g × 120/100 + 5 × 170 g × 77/100 = 1254,5 kcal; weighed finished dish 1250 g → 100,36 kcal per 100 g.
        app.onNodeWithTag("dishCooked").performScrollTo().performTextInput("1250")
        app.onNodeWithTag("dishPer100").performScrollTo().assertTextEquals("100 ккал")
        app.onNodeWithTag("saveDish").performScrollTo().performClick()
        waitText("Добавить в дневник")
        app.onNodeWithTag("quantity").performTextReplacement("300")
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick()
        waitText("Записать еду")
        app.onNodeWithText("Обзор").performClick()
        app.onNodeWithText("301").assertExists()
        // The dish is now a product of its own and can be found again.
        app.onNodeWithText("Дневник").performClick();app.onNodeWithContentDescription("Добавить еду").performClick()
        app.onNodeWithTag("foodSearch").performTextInput("курица с картошкой")
        waitText("Своё блюдо · 2 ингредиента",30000)
    }
    @Test fun inventoryDishCanBeEatenAndDeletedWithoutLosingDiary() {
        launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")
        app.onNodeWithText("Профиль").performClick()
        app.onNodeWithTag("newDishCell").performScrollTo().performClick()
        app.onNodeWithTag("dishName").performTextInput("Суп")
        ingredient("куриное филе","Куриное филе без кожи, сырое"){app.onNodeWithTag("quantity").performTextReplacement("300")}
        app.onNodeWithTag("saveDish").performScrollTo().performClick()
        waitText("Инвентарь")
        app.onNodeWithContentDescription("Суп").performScrollTo().performClick()
        app.onNodeWithTag("eatGrams").performScrollTo().performTextInput("200")
        // 300 g × 120 kcal/100 g makes a 300 g dish; 200 g of it is 240 kcal.
        app.onNodeWithTag("eatPreview").performScrollTo().assertTextEquals("≈ 240 ккал · Б 45,0 г")
        app.onNodeWithTag("eatDish").performScrollTo().performClick()
        waitText("Записано в дневник: Суп, 240 ккал")
        app.onNodeWithContentDescription("Суп").performScrollTo().performClick()
        app.onNodeWithTag("deleteDish").performScrollTo().performClick()
        app.onNodeWithText("Удалить").performClick()
        app.waitUntil(30000){app.onAllNodesWithContentDescription("Суп").fetchSemanticsNodes().isEmpty()}
        app.onNodeWithText("Дневник").performClick()
        waitText("1 запись");app.onAllNodesWithText("Суп").onFirst().assertExists()
    }
    @Test fun loggedExercisesBurnCaloriesFromBodyWeight() {
        launch();waitText("Начать без расчёта")
        app.onNodeWithText("Возраст, лет").performScrollTo().performTextInput("30")
        app.onNodeWithText("Рост, см").performScrollTo().performTextInput("180")
        app.onNodeWithText("Текущий вес, кг").performScrollTo().performTextInput("80")
        app.onNodeWithText("Сохранить и начать").performScrollTo().performClick()
        waitText("Обзор")
        app.onNodeWithText("Тренировки").performClick()
        waitText("Рекомендации на сегодня")
        app.onNodeWithTag("addExercise").performClick()
        app.onNodeWithTag("exerciseSearch").performTextInput("отжимания от пола")
        waitText("Отжимания от пола",30000);app.onNodeWithText("Отжимания от пола").performClick()
        app.onNodeWithTag("sets").performTextReplacement("3");app.onNodeWithTag("reps").performTextReplacement("12")
        // 3 × 12 × 2 s + 2 × 60 s rest = 3,2 min; 3,8 MET × 80 kg × 3,2/60 h ≈ 16 kcal.
        app.onNodeWithTag("workoutKcal").performScrollTo().assertTextEquals("≈ 16 ккал")
        app.onNodeWithTag("saveWorkout").performScrollTo().performClick()
        waitText("Отмечено по выполненным упражнениям")
        // Recommendations sit further down a lazy list: scroll there before the buttons exist.
        app.onNodeWithTag("trainingList").performScrollToNode(hasText("Выполнено"))
        app.onAllNodesWithText("Выполнено").onFirst().performClick()
        app.onNodeWithTag("saveWorkout").performScrollTo().performClick()
        waitText("2 упражнения")
        app.onNodeWithText("Обзор").performClick()
        waitText("Тренировка была")
    }
}
