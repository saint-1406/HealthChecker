package az.healthchecker
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.Season
import az.healthchecker.domain.seasonOn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
class AppearanceFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    // The look lives in the settings file, which other tests do not reset: clear it before and after.
    private fun clearLook(){File(context.filesDir,"datastore/settings.preferences_pb").delete()}
    @Before fun reset(){context.deleteDatabase("healthchecker.db");context.getSharedPreferences("app",Context.MODE_PRIVATE).edit().clear().commit();clearLook()}
    @After fun close(){scenario?.close();clearLook()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun waitText(text:String,timeout:Long=90000)=app.waitUntil(timeout){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun skipOnboarding(){launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performClick();waitText("Обзор")}
    private fun now(name:String)=app.waitUntil(10000){runCatching{app.onNodeWithTag("appearanceNow").assertTextEquals("Сейчас: $name")}.isSuccess}

    @Test fun paletteHolidayThemeDecorationsAndResetAreChosenInAppearance() {
        skipOnboarding()
        app.onNodeWithText("Профиль").performClick()
        app.onNodeWithTag("openAppearance").performScrollTo().performClick()
        // On a holiday its theme is on by itself; otherwise the original mint look.
        val holiday=when(seasonOn(LocalDate.now())){Season.HALLOWEEN->"Хэллоуин";Season.NEW_YEAR->"Новый год";null->null}
        now(holiday?:"Мята")
        app.onNodeWithTag("palette-ocean").performScrollTo().performClick();now("Океан")
        app.onNodeWithTag("palette-ocean").assertIsSelected()
        app.onNodeWithTag("holiday-newyear").performScrollTo().performClick();now("Новый год")
        app.onNodeWithTag("seasonStatus").assertExists()
        app.onNodeWithTag("decorationsSwitch").performScrollTo().assertIsOn().performClick().assertIsOff()
        app.onNodeWithTag("mode-dark").performScrollTo().performClick()
        app.onNodeWithTag("resetAppearance").performScrollTo().performClick();now(holiday?:"Мята")
        app.onNodeWithTag("decorationsSwitch").performScrollTo().assertIsOn()
        // The mode survives the reset; the profile names the theme and the mode.
        app.onNodeWithContentDescription("Назад").performClick()
        app.onNodeWithText("Тема: ${holiday?:"Мята"} · тёмная",substring=true).performScrollTo().assertExists()
    }
    @Test fun diaryOpensWithTheDaySummary() {
        skipOnboarding()
        app.onNodeWithText("Дневник").performClick()
        app.onNodeWithTag("diarySummary").assert(hasAnyDescendant(hasText("0 ккал"))).assert(hasAnyDescendant(hasText("Цель не задана")))
    }
}
