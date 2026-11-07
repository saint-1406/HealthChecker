package az.healthchecker.ui.training
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import az.healthchecker.domain.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h1400dp")
class VideoLogEditorTest {
    @get:Rule val compose=createComposeRule()
    private fun ex(id:String,measure:ExerciseMeasure,secondsPerRep:Double?=null)=Exercise(id,id,muscles=listOf(Muscle.LEGS),equipment=Equipment.NONE,kind=ExerciseKind.STRENGTH,level=1,met=4.0,metCode="02022",measure=measure,secondsPerRep=secondsPerRep)
    private val start=listOf(videoLogItem(ex("squat",ExerciseMeasure.REPS,3.0),VideoWork("squat",2,80)),videoLogItem(ex("plank",ExerciseMeasure.SECONDS),VideoWork("plank",1,30)),
        videoLogItem(ex("jog",ExerciseMeasure.MINUTES),VideoWork("jog",1,60)))
    private var saved:List<VideoLogItem>?=null
    private var closed=false

    @Before fun show() {
        Lang.current=AppLanguage.EN
        compose.setContent{
            var items by androidx.compose.runtime.remember{mutableStateOf(start)}
            VideoLogEditor(items,{items=it},{"Name $it"},{4.0},70.0,{},{closed=true}){saved=it}
        }
    }

    @After fun language(){Lang.current=AppLanguage.RU}

    @Test fun everythingIsCheckedAndTotalledAtFirst() {
        compose.onNodeWithTag("videoLogTotals").assert(hasText("3 exercises",substring=true)).assert(hasText("work 2:50",substring=true)).assert(hasText("kcal",substring=true))
        (0..2).forEach{compose.onNodeWithTag("videoLogDone_$it").assertIsOn()}
        compose.onNodeWithTag("videoLogSets_0_field").assert(hasText("2"))
        compose.onNodeWithTag("videoLogAmount_0_field").assert(hasText("13"))
        // A timed exercise has no sets, only its time.
        compose.onNodeWithTag("videoLogSets_2_field").assertDoesNotExist()
        compose.onNodeWithTag("videoLogAmount_2_field").assert(hasText("60"))
    }
    @Test fun onlyCheckedItemsAreSavedWithTheirEditedAmounts() {
        compose.onNodeWithTag("videoLogDone_1").performClick()
        compose.onNodeWithTag("videoLogSets_0_plus").performClick()
        compose.onNodeWithTag("videoLogAmount_0_field").performTextReplacement("20")
        compose.onNodeWithTag("videoLogList").performScrollToNode(hasTestTag("videoLogAmount_2_minus"))
        compose.onNodeWithTag("videoLogAmount_2_minus").performClick()
        compose.onNodeWithTag("videoLogTotals").assert(hasText("2 exercises",substring=true))
        compose.onNodeWithTag("saveVideoLog").assert(hasText("Log (2)")).performClick()
        val result=saved!!
        assertEquals(listOf("squat","jog"),result.map{it.exerciseId})
        assertEquals(listOf(3,20),listOf(result[0].sets,result[0].amount))
        assertEquals(45,result[1].amount)
        assertFalse(closed)
    }
    @Test fun nothingCheckedCannotBeSavedAndCancelSavesNothing() {
        compose.onNodeWithTag("videoLogNone").performClick()
        compose.onNodeWithTag("saveVideoLog").assertIsNotEnabled()
        compose.onNodeWithTag("videoLogSets_0_field").assertDoesNotExist()
        compose.onNodeWithTag("cancelVideoLog").performClick()
        assertTrue(closed);assertNull(saved)
    }
    @Test fun aNumberOutOfRangeIsNotTaken() {
        compose.onNodeWithTag("videoLogSets_0_field").performTextReplacement("0")
        compose.onNodeWithTag("videoLogTotals").assert(hasText("work 2:50",substring=true))
        compose.onNodeWithTag("saveVideoLog").performClick()
        assertEquals(2,saved!![0].sets)
    }
}
