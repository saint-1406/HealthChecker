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
class TrainingAndWeightFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit()}
    @After fun close(){scenario?.close()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000,substring:Boolean=false)=app.waitUntil(timeout){app.onAllNodesWithText(text,substring=substring).fetchSemanticsNodes().isNotEmpty()}
    private fun skipOnboarding(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")}
    /** The suggestions appear once the exercise catalog is loaded and may sit below the first screen. */
    private fun openTraining() {
        app.onNodeWithText("Тренировки").performClick()
        app.waitUntil(90000){runCatching{app.onNodeWithTag("trainingList").performScrollToNode(hasText("Рекомендации на сегодня"))}.isSuccess}
        app.onNodeWithTag("trainingList").performScrollToNode(hasTestTag("addExercise"))
    }
    private fun openExercise(query:String,name:String) {
        openTraining()
        app.onNodeWithTag("addExercise").performClick()
        app.onNodeWithTag("exerciseSearch").performTextInput(query)
        waitText(name,30000);app.onAllNodesWithText(name).onFirst().performClick()
    }

    @Test fun videoTitleFindsTheExerciseAndBothNamesAreShown() {
        skipOnboarding()
        openTraining()
        app.onNodeWithTag("addExercise").performClick()
        app.onNodeWithTag("exerciseSearch").performTextInput("Jog on the Spot")
        // The best match comes first and its card carries the English name and the video title.
        waitText("Бег на месте",30000)
        app.onNodeWithTag("exerciseResults").onChildren().onFirst().assert(hasText("Бег на месте",substring=true)).assert(hasText("Jog on the Spot",substring=true))
        app.onAllNodesWithText("Бег на месте").onFirst().performClick()
        waitText("Также: ",substring=true);app.onNodeWithText("Jog on the Spot",substring=true).assertExists()
        app.onNodeWithTag("addExercisePicture").assertExists()
    }
    @Test fun stopwatchTimesAnExerciseAndKeepsRunningAcrossScreens() {
        skipOnboarding()
        openExercise("бег на месте","Бег на месте")
        app.onNodeWithTag("stopwatchStart").performScrollTo().performClick()
        Thread.sleep(2500)
        app.onNodeWithTag("stopwatchPause").performScrollTo().performClick()
        app.onNodeWithTag("stopwatchUse").performScrollTo().performClick()
        app.onNodeWithTag("minutes").performScrollTo().assert(hasText("0,1"))
        // The same stopwatch is on the workout screen after leaving the form (the picture pushed the header up).
        app.onNodeWithContentDescription("Назад").performScrollTo().performClick()
        openWorkout()
        app.onNodeWithTag("stopwatchTime").assert(!hasText("0:00.0"))
    }
    private fun openWorkout() {
        app.onNodeWithTag("trainingList").performScrollToNode(hasTestTag("startWorkout"))
        app.onNodeWithTag("startWorkout").performClick()
        app.onNodeWithTag("workoutProgress").assertExists()
    }
    @Test fun workoutScreenShowsMovementsLogsInOneTapAndTimesTheRest() {
        skipOnboarding()
        openTraining()
        openWorkout()
        app.onNodeWithTag("workoutProgress").assert(hasText("Выполнено 0 из",substring=true))
        // Without the user's own pictures every card shows the app's motion scheme.
        app.onAllNodesWithTag("exerciseFigure").onFirst().assert(hasContentDescription("Схема движения: ",substring=true))
        app.onNodeWithTag("workoutNowList").performScrollToNode(hasTestTag("logPrescribed"))
        app.onAllNodesWithTag("logPrescribed").onFirst().performClick()
        app.waitUntil(10000){app.onAllNodesWithText("Выполнено 1 из",substring=true).fetchSemanticsNodes().isNotEmpty()}
        app.onAllNodesWithText("Записано").onFirst().assertExists()
        // The rest timer sits under the list and counts down on its own.
        app.onNodeWithTag("rest30").performClick()
        app.onNodeWithTag("countdownTime").assertExists()
        Thread.sleep(1500)
        app.onNodeWithTag("countdownPause").performClick()
        app.onNodeWithTag("countdownTime").assert(hasText("0:2",substring=true))
        app.onNodeWithTag("countdownClose").performClick()
        app.onNodeWithTag("rest30").assertExists()
        app.onNodeWithContentDescription("Назад").performClick()
        waitText("Отмечено по выполненным упражнениям")
    }
    @Test fun morningWeightsShowTheDirection() {
        skipOnboarding()
        app.onNodeWithTag("recordWeight").performScrollTo().performClick()
        app.onNodeWithTag("weightInput").performTextInput("81,0");app.onNodeWithTag("saveWeight").performClick()
        app.waitUntil(30000){app.onAllNodesWithText("81,0 кг").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithContentDescription("Предыдущий день").performClick()
        app.onNodeWithTag("recordWeight").performScrollTo().performClick()
        app.onNodeWithTag("weightInput").performTextInput("81,6");app.onNodeWithTag("saveWeight").performClick()
        app.waitUntil(30000){app.onAllNodesWithText("81,6 кг").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithContentDescription("Следующий день").performClick()
        app.waitUntil(30000){app.onAllNodesWithTag("weightTrend",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithTag("weightTrend",useUnmergedTree=true).assertTextEquals("За неделю: −0,6 кг · снижается")
        app.onNodeWithTag("weightLatest",useUnmergedTree=true).assertTextEquals("81,0 кг")
    }
}
