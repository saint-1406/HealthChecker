package az.healthchecker
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
class PhotoInboxFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val inbox get()=File(context.filesDir,"photo-inbox")
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit();inbox.deleteRecursively()}
    @After fun close(){scenario?.close();inbox.deleteRecursively()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun waitTag(tag:String,timeout:Long=30000)=app.waitUntil(timeout){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun skipOnboarding(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")}
    /** A photo "taken" just now, as the camera would leave it in the inbox. */
    private fun seedPhoto() {
        val bitmap=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply{drawColor(Color.WHITE);drawText("Oats 100 g: 12,3 g protein",40f,300f,Paint().apply{textSize=48f})}
        inbox.mkdirs();File(inbox,"food-${System.currentTimeMillis()}.jpg").outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}
    }
    private fun openInboxPhoto() {
        app.onNodeWithText("Дневник").performClick()
        app.onNodeWithTag("diaryList").performScrollToNode(hasTestTag("photoInbox"))
        waitTag("inboxPhoto");app.onAllNodesWithTag("inboxPhoto").assertCountEquals(1)
        app.onNodeWithTag("inboxPhoto").performClick()
        waitTag("photoView")
    }

    @Test fun foodFromAPhotoIsLoggedOnItsDayAndThePhotoDeleted() {
        seedPhoto();skipOnboarding()
        // The overview reminds about photos waiting to be logged.
        app.onNodeWithTag("photosWaiting").performScrollTo().assert(hasText("1 фото ждёт внесения",substring=true))
        openInboxPhoto()
        app.onNodeWithTag("foodSearch").performTextInput("овсяные хлопья сух")
        waitText("Овсяные хлопья, сухие",30000);app.onAllNodesWithText("Овсяные хлопья, сухие").onFirst().performClick()
        app.onNodeWithTag("quantity").performTextReplacement("50")
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick()
        waitTag("photoLogged");app.onNodeWithTag("photoLogged").assertTextEquals("Внесено с этого фото: 1")
        // The photo stays on screen while the next food is searched.
        app.onNodeWithTag("photoView").assertIsDisplayed()
        app.onNodeWithTag("photoDone").performClick()
        app.onNodeWithTag("confirmDeletePhoto").performClick()
        app.waitUntil(30000){runCatching{app.onNodeWithTag("diaryList").performScrollToNode(hasText("Овсяные хлопья, сухие"))}.isSuccess}
        app.onNodeWithText("Отменить").assertExists()
        // With no photos left the card goes; the camera stays beside "Log food".
        app.waitUntil(10000){app.onAllNodesWithTag("photoInbox").fetchSemanticsNodes().isEmpty()}
        app.onNodeWithTag("diaryList").performScrollToNode(hasTestTag("photoMenu"))
        app.onNodeWithTag("photoMenu").performClick();app.onNodeWithTag("photoFromGallery").assertExists();app.onNodeWithTag("shootMedicine").assertExists()
    }

    @Test fun medicinePhotoBecomesACardAndATakenDoseAndCanBeKept() {
        seedPhoto();skipOnboarding()
        openInboxPhoto()
        app.onNodeWithTag("photoKindMedicine").performClick()
        waitTag("photoNewMedicine");app.onNodeWithTag("photoNewMedicine").performClick()
        waitTag("medName")
        app.onNodeWithText("Витамин").performScrollTo().performClick()
        app.onNodeWithTag("medName").performScrollTo().performTextInput("Витамин D3")
        app.onNodeWithTag("saveMedication").performScrollTo().performClick()
        waitTag("photoTake");app.onNodeWithTag("photoTake").performClick()
        waitTag("photoLogged");app.onNodeWithTag("photoLogged").assertTextEquals("Внесено с этого фото: 1")
        app.onNodeWithTag("photoDone").performClick()
        app.onNodeWithText("Оставить").performClick()
        app.waitUntil(30000){app.onAllNodesWithTag("diaryList").fetchSemanticsNodes().isNotEmpty()}
        // Kept as a medicine photo; the dose is marked for today.
        app.onNodeWithTag("diaryList").performScrollToNode(hasTestTag("photoInbox"))
        app.onAllNodesWithTag("inboxPhoto").assertCountEquals(1)
        app.onNodeWithContentDescription("Фото лекарства, ",substring=true).assertExists()
        app.onNodeWithTag("diaryList").performScrollToNode(hasText("Витамин D3"))
        waitText("Принято: 1 из 1")
    }
}
