package az.healthchecker
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.*

/** Favorites tabs: foods starred in search land on Diary → Favorites; exercises and videos on Training → Favorites. */
class FavoritesFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val egg="Яйцо куриное, варёное"
    @Before fun reset(){ApplicationProvider.getApplicationContext<Context>().deleteDatabase("healthchecker.db")}
    @After fun close(){scenario?.close()}
    private fun waitText(text:String)=app.waitUntil(90000){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun waitTag(tag:String)=app.waitUntil(90000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun begin(){scenario=ActivityScenario.launch(MainActivity::class.java);waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор")}

    @Test fun foodsStarredInSearchAreLoggedFromTheFavoritesTab() {
        begin();app.onNodeWithText("Дневник").performClick();waitTag("tabFavorites")
        app.onNodeWithTag("tabFavorites").performClick();waitTag("noFavoriteFoods")
        app.onNodeWithTag("tabDay").performClick();app.onNodeWithTag("logFoodButton").performScrollTo().performClick()
        app.onNodeWithTag("foodSearch").performTextReplacement("yumurta");app.onNodeWithTag("foodSearch").performImeAction()
        app.waitUntil(90000){app.onNodeWithTag("foodResultsList").onChildren().filter(hasClickAction()).fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithTag("foodResultsList").performScrollToNode(hasContentDescription("В избранное: $egg"))
        app.onNodeWithContentDescription("В избранное: $egg").performClick()
        app.waitUntil(30000){app.onAllNodesWithContentDescription("Убрать из избранного: $egg").fetchSemanticsNodes().isNotEmpty()}
        Espresso_back()
        waitTag("tabFavorites");app.onNodeWithTag("tabFavorites").performClick();waitTag("favoriteFood")
        app.onAllNodesWithText(egg).onFirst().performClick();waitTag("quantity")
        app.onNodeWithTag("quantity").performTextReplacement("120")
        app.onNodeWithTag("savePortion").performScrollTo().performClick()
        waitTag("tabDay");app.onNodeWithTag("tabDay").performClick();waitTag("diaryList")
        app.onNodeWithTag("diaryList").performScrollToNode(hasText(egg));app.onAllNodesWithText(egg).onFirst().assertExists()
        // The star on the diary entry takes it off the favorites again.
        app.onNodeWithTag("diaryList").performScrollToNode(hasContentDescription("Убрать из избранного: $egg"))
        app.onNodeWithContentDescription("Убрать из избранного: $egg").performClick()
        app.onNodeWithTag("tabFavorites").performClick();waitTag("noFavoriteFoods")
        // ...and offers it under "often eaten".
        app.onNodeWithTag("favoriteFoodsList").performScrollToNode(hasTestTag("frequentFood"))
    }

    @Test fun exercisesAndVideosStarredElsewhereOpenFromTrainingFavorites() {
        begin();app.onNodeWithText("Тренировки").performClick();waitTag("tabFavorites")
        app.onNodeWithTag("tabFavorites").performClick();waitTag("noFavoriteWorkouts")
        app.onNodeWithTag("tabDay").performClick();waitTag("addExercise");app.onNodeWithTag("addExercise").performClick()
        waitTag("exerciseResults");app.waitUntil(30000){app.onAllNodesWithTag("star").fetchSemanticsNodes().isNotEmpty()}
        app.onAllNodesWithTag("star").onFirst().performClick();app.waitUntil(30000){app.onAllNodesWithTag("unstar").fetchSemanticsNodes().isNotEmpty()}
        Espresso_back()
        waitTag("openVideos");app.onNodeWithTag("openVideos").performClick();waitTag("videoCard")
        app.onAllNodesWithTag("star").onFirst().performClick();app.waitUntil(30000){app.onAllNodesWithTag("unstar").fetchSemanticsNodes().isNotEmpty()}
        Espresso_back()
        waitTag("tabFavorites");app.onNodeWithTag("tabFavorites").performClick()
        waitTag("favoriteExercise");waitTag("favoriteVideo")
        app.onNodeWithTag("tabFavorites").assertTextContains("2",substring=true)
        // A favorite video opens and "back" returns to the favorites tab.
        app.onNodeWithTag("favoriteVideoOpen").performClick();waitTag("videoTimeline")
        Espresso_back();waitTag("favoriteWorkoutsList")
        // A favorite exercise opens the logging form.
        app.onNodeWithTag("favoriteExerciseOpen").performClick();waitTag("saveWorkout")
    }

    private fun Espresso_back(){scenario!!.onActivity{it.onBackPressedDispatcher.onBackPressed()};app.waitForIdle()}
}
