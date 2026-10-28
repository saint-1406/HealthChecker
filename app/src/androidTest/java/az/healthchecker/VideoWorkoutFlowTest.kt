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
class VideoWorkoutFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit()}
    @After fun close(){scenario?.close()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000,substring:Boolean=false)=app.waitUntil(timeout){app.onAllNodesWithText(text,substring=substring).fetchSemanticsNodes().isNotEmpty()}
    private fun skipOnboarding(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")}
    private fun openVideos() {
        app.onNodeWithText("Тренировки").performClick()
        app.waitUntil(90000){runCatching{app.onNodeWithTag("trainingList").performScrollToNode(hasTestTag("openVideos"))}.isSuccess}
        app.onNodeWithTag("openVideos").performClick()
        waitText("30 MIN FULL BODY WORKOUT",substring=true)
    }

    @Test fun thirtyMinuteVideoFollowsItsTimelineAndLogsTheWholeWorkout() {
        skipOnboarding()
        openVideos()
        app.onAllNodesWithText("30 MIN FULL BODY WORKOUT",substring=true).onFirst().performClick()
        // The owner does not allow the video in other apps: the screen says so and offers YouTube instead of loading anything.
        waitText("34 упражнения",30000,substring=true)
        app.onNodeWithTag("playerMessage").assert(hasText("запретил",substring=true))
        app.onNodeWithTag("openYoutube").assertExists()
        app.onNodeWithTag("videoNow").assert(hasText("Скоро начало",substring=true)).assert(hasText("Круги руками",substring=true))
        // A tap on an exercise moves the timeline there; the timer then follows the video.
        app.onNodeWithTag("videoTimeline").performScrollToNode(hasText("Squat Knee to Elbow"))
        app.onAllNodesWithText("Squat Knee to Elbow").onFirst().performClick()
        app.onNodeWithTag("videoNow").assert(hasText("Присед с подъёмом колена к локтю",substring=true))
        app.onNodeWithTag("videoClockTime").assert(hasText("5:52 / 30:12"))
        app.onNodeWithTag("videoClockStart").performClick()
        Thread.sleep(2300)
        app.onNodeWithTag("videoClockPause").performClick()
        app.onNodeWithTag("videoClockTime").assert(!hasText("5:52 / 30:12"))
        // Half-way the dialog also offers the part done so far; the whole video gives every exercise once, repeats as sets.
        app.onNodeWithTag("videoTimeline").performScrollToNode(hasTestTag("logVideo"))
        app.onNodeWithTag("logVideo").performClick()
        app.onNodeWithTag("logUpToNow").assertExists()
        app.onNodeWithTag("logWholeVideo").performClick()
        app.onNodeWithTag("confirmLogVideo").performClick()
        waitText("Записано из видео: 32 упражнения",30000)
        app.onNodeWithContentDescription("Назад").performClick()
        app.onNodeWithContentDescription("Назад").performClick()
        app.onNodeWithTag("trainingList").performScrollToNode(hasText("Выполненные упражнения"))
        app.onNodeWithText("32 упражнения").assertExists()
    }
    @Test fun ownYouTubeLinkLoadsOnlyOnRequestAndIsLoggedByTime() {
        skipOnboarding()
        openVideos()
        app.onNodeWithTag("videoList").performScrollToNode(hasTestTag("addVideoLink"))
        app.onNodeWithTag("addVideoLink").performClick()
        app.onNodeWithTag("videoLink").performTextInput("vimeo.com/1")
        waitText("Это не похоже на ссылку YouTube",10000)
        app.onNodeWithTag("videoLink").performTextClearance()
        app.onNodeWithTag("videoLink").performTextInput("https://youtu.be/ml6cT4AZdqI?si=x")
        app.onNodeWithTag("videoTitle").performTextInput("Утренняя тренировка")
        app.onNodeWithTag("saveVideo").performClick()
        // The new video opens; nothing goes online until the user taps "Load the video".
        waitText("Утренняя тренировка",30000)
        app.onNodeWithTag("loadYoutube").assertExists()
        app.onNodeWithTag("logVideo").performClick()
        app.onNodeWithTag("videoMinutes").performTextInput("20")
        app.onNodeWithTag("confirmLogVideo").performClick()
        waitText("Записано: Кардио-тренировка по видео, умеренная",30000)
        // The link is kept after a restart.
        launch()
        openVideos()
        app.onNodeWithTag("videoList").performScrollToNode(hasText("Утренняя тренировка"))
        app.onNodeWithText("Утренняя тренировка").assertExists()
    }
}
