package az.healthchecker
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.data.HealthRepository
import az.healthchecker.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.time.LocalDate
import java.time.ZoneId

/** Last time is filled in, one tap steps up, the new record shows, and the progress screens chart the exercise. */
class StrengthProgressFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun waitTag(tag:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}

    @Before fun seed() {
        context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking {
            val repo=HealthRepository(context);repo.finishOnboarding();repo.saveTrainingPrefs(TrainingPrefs(dumbbells=true))
            val zone=ZoneId.systemDefault();val today=LocalDate.now()
            listOf(14L to 10,7L to 12).forEach{(ago,reps)->val d=today.minusDays(ago)
                repo.upsertWorkout(WorkoutEntry(java.util.UUID.randomUUID().toString(),"db_goblet_squat","Гоблет-присед с гантелью",ExerciseKind.STRENGTH,listOf(Muscle.LEGS,Muscle.GLUTES),3,reps,null,8.0,6.0,5.0,null,null,
                    d.atTime(18,0).atZone(zone).toInstant().toString(),zone.id,d.toString()))}
        }
    }
    @After fun close(){scenario?.close()}

    @Test fun lastTimeStepUpRecordAndChart() {
        scenario=ActivityScenario.launch(MainActivity::class.java)
        waitText("Тренировки");app.onNodeWithText("Тренировки").performClick()
        waitTag("trainingList")
        app.onNodeWithTag("trainingList").performScrollToNode(hasTestTag("addExercise"))
        app.onNodeWithTag("addExercise").performClick()
        app.onNodeWithTag("exerciseSearch").performTextInput("гоблет")
        waitText("Гоблет-присед с гантелью",30000);app.onAllNodesWithText("Гоблет-присед с гантелью").onFirst().performClick()
        // Last week's 3 × 12 at 8 kg is shown and already filled in.
        waitTag("lastTime")
        app.onNodeWithTag("lastTimeValue").assertTextEquals("3 × 12 · 8 кг")
        app.onNodeWithTag("reps").assert(hasText("12"))
        // 12 reps are done: the step up is a 9 kg dumbbell for 8 reps, which beats the heaviest weight and the 1RM.
        app.onNodeWithTag("applyNextStep").performScrollTo().performClick()
        app.onNodeWithTag("reps").assert(hasText("8"))
        waitTag("newRecord",10000)
        app.onNodeWithTag("newRecord").assertTextContains("9 кг",substring=true)
        app.onNodeWithTag("saveWorkout").performScrollTo().performClick()
        // The day's list marks it with a trophy.
        waitTag("trainingList")
        // The row is one clickable node, so its texts merge: scroll by text, then check the badge in the unmerged tree.
        app.onNodeWithTag("trainingList").performScrollToNode(hasText("Рекорд: ",substring=true))
        app.onNodeWithTag("recordBadge",useUnmergedTree=true).assertTextContains("9 кг",substring=true)
        val saved=runBlocking{HealthRepository(context).exportSnapshot()}.workouts.filter{it.localDate==LocalDate.now().toString()}
        Assert.assertEquals(listOf(Triple(3,8,9.0)),saved.map{Triple(it.sets,it.reps,it.loadKg)})
        // Progress: the exercise with three sessions, its chart and records.
        app.onNodeWithTag("trainingList").performScrollToNode(hasTestTag("openProgress"))
        app.onNodeWithTag("openProgress").performClick()
        waitTag("progress-db_goblet_squat")
        app.onNodeWithTag("progress-db_goblet_squat").assertTextContains("3 тренировки",substring=true)
        app.onNodeWithTag("progress-db_goblet_squat").performClick()
        waitTag("exerciseProgress")
        app.onNodeWithTag("progressChart").assertExists()
        app.onNodeWithTag("exerciseProgress").performScrollToNode(hasTestTag("personalRecords"))
        app.onNodeWithText("Самая тяжёлая гантель").assertExists()
        app.onNodeWithContentDescription("Назад").performClick();waitTag("progressList")
    }

    @Test fun setBySetStartsTheRestTimer() {
        scenario=ActivityScenario.launch(MainActivity::class.java)
        waitText("Тренировки");app.onNodeWithText("Тренировки").performClick()
        app.waitUntil(90000){runCatching{app.onNodeWithTag("trainingList").performScrollToNode(hasTestTag("startWorkout"))}.isSuccess}
        app.onNodeWithTag("startWorkout").performClick()
        waitTag("workoutNowList")
        app.waitUntil(30000){runCatching{app.onNodeWithTag("workoutNowList").performScrollToNode(hasTestTag("setDone"))}.isSuccess}
        app.onAllNodesWithTag("setDone").onFirst().assertTextContains("Подход 1/",substring=true).performClick()
        waitTag("countdownTime",10000)
        app.onNodeWithText("дальше подход 2/",substring=true).assertExists()
        app.onAllNodesWithTag("setDone").onFirst().assert(!hasText("Подход 1/",substring=true))
        app.onNodeWithTag("countdownClose").performClick()
    }
}
