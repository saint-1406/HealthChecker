package az.healthchecker.catalog
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ExerciseCatalogTest {
    private val catalog by lazy{ExerciseCatalog(ApplicationProvider.getApplicationContext<Context>())}
    @Test fun bundledHomeExercisesLoad() {
        val all=catalog.exercises;assertEquals(195,all.size);assertEquals(all.size,all.map{it.id}.toSet().size)
        assertTrue(all.all{it.met in 1.0..15.0&&it.metCode.length==5&&it.cue.isNotBlank()&&it.level in 1..3})
        assertEquals(37,all.count{it.equipment==Equipment.DUMBBELLS})
    }
    @Test fun searchByRussianOrEnglishAndFilters() {
        assertTrue(catalog.search("отжимания",null,null,false).any{it.id=="pushup"})
        assertTrue(catalog.search("push-up",null,null,false).any{it.id=="pushup"})
        assertTrue(catalog.search("",Muscle.BACK,ExerciseCategory.STRENGTH,false).none{it.equipment==Equipment.DUMBBELLS})
        assertTrue(catalog.search("тяга",Muscle.BACK,null,true).any{it.id=="db_bent_row"})
    }
    @Test fun videoTitlesFindTheirExerciseFirst() {
        mapOf("OH Squat" to "prisoner_squat","Jog on the Spot" to "jog_in_place","High Plank SH Tap" to "plank_shoulder_tap","Heel Touch" to "standing_heel_touch",
            "T-Raise" to "prone_t_raise","Plank Pike" to "forearm_plank_pike","High Plank & Pike" to "plank_pike","Air Squat" to "squat","Jump Rope" to "skip_imitation",
            "wide squat" to "sumo_squat","mountain climber toe taps" to "climber_toe_tap").forEach{(q,id)->assertEquals(q,id,catalog.search(q,null,null,true).first().id)}
    }
    @Test fun keywordsMusclesAndDirectionsNarrowTheSearch() {
        assertTrue(catalog.search("приседания",null,null,false).take(3).all{it.name.startsWith("Присед")})
        assertTrue(catalog.search("пресс",null,null,false).isNotEmpty()&&catalog.search("пресс",null,null,false).all{Muscle.CORE in it.muscles})
        assertTrue(catalog.search("разминка",null,null,false).all{it.category==ExerciseCategory.WARMUP})
        assertTrue(catalog.search("планка таз",null,null,false).map{it.id}.containsAll(listOf("plank_pike","forearm_plank_pike")))
        assertEquals("skip_imitation",catalog.search("скакалка",null,null,false).first().id)
        assertTrue(catalog.search("отжиманий",null,null,false).any{it.id=="pushup"})
        assertEquals(17,catalog.search("",null,ExerciseCategory.WARMUP,false).size)
    }
    @Test fun everyBeginnerHomePlanCanBeBuilt() {
        val plan=recommend(catalog.exercises,UserSnapshot(),"2026-10-19",TrainingPrefs(false,1))
        assertEquals(4,plan.items.count{it.role==Role.MAIN});assertTrue(plan.items.all{it.exercise.equipment==Equipment.NONE&&it.exercise.level==1})
        val strong=recommend(catalog.exercises,UserSnapshot(),"2026-10-19",TrainingPrefs(true,3))
        assertEquals(6,strong.items.count{it.role==Role.MAIN})
    }
    @Test fun everyExerciseHasAGroundedMotionScheme() {
        catalog.exercises.forEach{e->
            val m=catalog.figure(e.id);assertNotNull(e.id,m)
            assertTrue(e.id,m!!.frames.size in 1..6);assertEquals(e.id,m.frames.size==1,m.hold)
            m.frames.forEach{f->
                val points=groundedJoints(f).points
                val bottom=maxOf(points.filterKeys{it!="head"}.values.maxOf{it.y},points.getValue("head").y+Body.HEAD)
                assertEquals(e.id,-f.lift,bottom,1e-4f)
            }
        }
    }
}
