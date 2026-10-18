package az.healthchecker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class DiaryFlowTest {
    @get:Rule val app=createAndroidComposeRule<MainActivity>()
    @Test fun onboardingDiaryAndDashboard() {
        app.waitUntil(20000) { app.onAllNodesWithText("Начать без расчёта").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithText("Начать без расчёта").performClick()
        app.onNodeWithText("Дневник").performClick()
        app.onNodeWithContentDescription("Добавить еду").performClick()
        app.onNodeWithTag("foodSearch").performTextInput("яйцо")
        app.waitUntil(10000){app.onAllNodesWithText("Яйцо куриное, варёное").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithText("Яйцо куриное, варёное").performClick()
        app.onNodeWithText("Штуки").performClick()
        app.onNodeWithTag("quantity").performTextReplacement("2")
        app.onNodeWithText("Добавить в дневник").performClick()
        app.onNodeWithText("Яйцо куриное, варёное").assertExists()
        app.onNodeWithText("Обзор").performClick()
        app.onNodeWithText("155").assertExists()
    }
}
