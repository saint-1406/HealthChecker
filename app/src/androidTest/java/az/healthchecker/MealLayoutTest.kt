package az.healthchecker
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.*
import az.healthchecker.ui.AppViewModel
import az.healthchecker.ui.diary.*
import org.junit.*
import java.util.UUID

class MealLayoutTest {
    @get:Rule val app=createComposeRule()
    private val store=ViewModelStore()
    private val verticalList=hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private val food=Food("usda:oil","Оливковое масло",per100g=Nutrients(884.0,0.0,100.0,0.0))
    private fun vm()=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(ApplicationProvider.getApplicationContext<Application>()))[AppViewModel::class.java]
    @Before fun russian(){Lang.current=AppLanguage.RU}
    @After fun close(){store.clear()}
    @Test fun aMealItemRemainsReachableOnAShortDisplayAtTwoHundredPercentFont() {
        val vm=vm();val draft=MealDraft(UUID.randomUUID().toString(),"2026-10-29",listOf(MealItem(food,Quantity(7.0,Measure.GRAMS))))
        app.setContent { MaterialTheme { CompositionLocalProvider(LocalDensity provides Density(1f,2f)){Box(Modifier.size(320.dp,280.dp)){MealDraftScreen(vm,draft,{},{},{},{})}} } }
        app.onNode(verticalList).assertIsDisplayed()
        app.onNode(verticalList).performScrollToNode(hasContentDescription("Изменить порцию: Оливковое масло"))
        app.onNodeWithContentDescription("Изменить порцию: Оливковое масло").assertIsDisplayed()
    }
    @Test fun aSavedMealRemainsReachableOnAShortDisplayAtTwoHundredPercentFont() {
        val vm=vm();val meal=SavedMeal("meal:breakfast","Завтрак",listOf(MealItem(food,Quantity(7.0,Measure.GRAMS))))
        app.setContent { MaterialTheme { CompositionLocalProvider(LocalDensity provides Density(1f,2f)){Box(Modifier.size(320.dp,280.dp)){SavedMealsScreen(vm,UserSnapshot(savedMeals=listOf(meal)),"2026-10-29",{},{})}} } }
        app.onNode(verticalList).assertIsDisplayed()
        app.onNode(verticalList).performScrollToNode(hasContentDescription("Добавить набор: Завтрак"))
        app.onNodeWithContentDescription("Добавить набор: Завтрак").assertIsDisplayed()
    }
    @Test fun returningFromUnresolvedPiecesToMillilitresKeepsTheOriginalAmount() {
        val drink=Food("drink","Напиток",per100g=Nutrients(50.0),density=0.8)
        app.setContent {MaterialTheme {PortionEditor(drink,Quantity(150.0,Measure.ML),"drink","","Записать",false,{}){}}}
        app.onNodeWithText("Штуки").performClick();app.onNodeWithText("Миллилитры").performClick()
        app.onNodeWithTag("quantity").assert(hasText("150"))
    }
    @Test fun searchKeepsRoomForResultsOnAShortDisplayWithLargeFont() {
        val vm=vm()
        app.setContent {MaterialTheme {CompositionLocalProvider(LocalDensity provides Density(1f,2f)){Box(Modifier.size(320.dp,280.dp)){FoodSearchScreen(vm,"Что вы съели?",{},{},{},{},{})}}}}
        app.onNode(verticalList).assertIsDisplayed()
    }
}
