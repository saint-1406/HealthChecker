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
        val all=catalog.exercises;assertEquals(141,all.size);assertEquals(all.size,all.map{it.id}.toSet().size)
        assertTrue(all.all{it.met in 1.0..15.0&&it.metCode.length==5&&it.cue.isNotBlank()&&it.level in 1..3})
        assertEquals(37,all.count{it.equipment==Equipment.DUMBBELLS})
    }
    @Test fun searchByRussianOrEnglishAndFilters() {
        assertTrue(catalog.search("отжимания",null,null,false).any{it.id=="pushup"})
        assertTrue(catalog.search("push-up",null,null,false).any{it.id=="pushup"})
        assertTrue(catalog.search("",Muscle.BACK,ExerciseKind.STRENGTH,false).none{it.equipment==Equipment.DUMBBELLS})
        assertTrue(catalog.search("тяга",Muscle.BACK,null,true).any{it.id=="db_bent_row"})
    }
    @Test fun everyBeginnerHomePlanCanBeBuilt() {
        val plan=recommend(catalog.exercises,UserSnapshot(),"2026-10-19",TrainingPrefs(false,1))
        assertEquals(4,plan.items.count{it.role==Role.MAIN});assertTrue(plan.items.all{it.exercise.equipment==Equipment.NONE&&it.exercise.level==1})
        val strong=recommend(catalog.exercises,UserSnapshot(),"2026-10-19",TrainingPrefs(true,3))
        assertEquals(6,strong.items.count{it.role==Role.MAIN})
    }
}
