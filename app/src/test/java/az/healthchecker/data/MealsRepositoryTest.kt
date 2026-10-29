package az.healthchecker.data
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.*
import az.healthchecker.backup.BackupCodec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class MealsRepositoryTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val now=Instant.parse("2026-10-30T00:01:00Z");private val zone=ZoneId.of("Asia/Baku")
    private fun item()=MealItem(Food("usda:egg","Яйцо",per100g=Nutrients(155.0,12.58)),Quantity(2.0,Measure.PIECES,50.0))
    @Test fun aDraftSurvivesReopeningAndCommitsOnlyOnce()=runTest {
        val name="meal-${UUID.randomUUID()}.db";val r=HealthRepository(context,name)
        val first=r.appendMealItems(listOf(item()),"2026-10-29")
        val reopened=HealthRepository(context,name);assertEquals(first,reopened.mealDraft())
        val second=reopened.appendMealItems(listOf(item()),"2026-10-30")
        assertEquals("2026-10-29",second.date)
        val before=r.exportVersioned().first
        val entries=reopened.commitMealDraft(second.id,now,zone)
        assertEquals(2,entries.size);assertTrue(entries.all{it.localDate=="2026-10-29"})
        assertEquals(before+1,r.exportVersioned().first)
        assertTrue(r.commitMealDraft(second.id,now,zone).isEmpty());assertEquals(2,r.exportSnapshot().entries.size)
        assertNull(reopened.mealDraft())
    }
    @Test fun anInvalidAdditionDoesNotPartiallyCommitTheDraft()=runTest {
        val r=HealthRepository(context,"meal-${UUID.randomUUID()}.db")
        val draft=r.appendMealItems(listOf(item()),"2026-10-29")
        assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{r.logMealItem(item().copy(quantity=Quantity(-1.0,Measure.GRAMS)),"2026-10-29",now,zone)}}
        assertEquals(draft,r.mealDraft());assertTrue(r.exportSnapshot().entries.isEmpty())
        val recorded=r.logMealItem(item(),"2026-10-30",now,zone)
        assertEquals(2,recorded.size);assertTrue(recorded.all{it.localDate=="2026-10-29"})
    }
    @Test fun favoritesAndMealsTravelInBackupsButUnfinishedDraftsDoNot()=runTest {
        val r=HealthRepository(context,"meal-${UUID.randomUUID()}.db")
        r.setFavorite("usda:egg",true);r.setFavorite("usda:egg",true)
        val meal=SavedMeal("meal:1","Breakfast",listOf(item()));r.saveMeal(meal)
        r.appendMealItems(meal.items,"2026-10-29")
        val snapshot=r.exportVersioned().second
        assertEquals(setOf("usda:egg"),snapshot.favoriteFoods);assertEquals(listOf(meal),snapshot.savedMeals)
        val other=HealthRepository(context,"meal-${UUID.randomUUID()}.db")
        other.replaceSnapshot(BackupCodec.decode(BackupCodec.encode(snapshot)))
        assertEquals(snapshot,other.exportSnapshot());assertNull(other.mealDraft())
        r.setFavorite("usda:egg",false);r.deleteMeal(meal.id)
        assertTrue(r.exportSnapshot().favoriteFoods.isEmpty());assertTrue(r.exportSnapshot().savedMeals.isEmpty())
    }
}
