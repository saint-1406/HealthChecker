package az.healthchecker.domain
import az.healthchecker.backup.BackupCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class MealsTest {
    private val egg=Food("usda:egg","Яйцо",per100g=Nutrients(155.0,12.58,10.61,1.12))
    private val oats=Food("usda:oats","Хлопья",per100g=Nutrients(379.0,13.15,6.52,67.7))
    private val now=Instant.parse("2026-10-30T00:01:00Z")
    @Test fun aMealKeepsSeparateProductsAndTheDraftDayAcrossMidnight() {
        val draft=MealDraft(UUID.randomUUID().toString(),"2026-10-29",listOf(MealItem(egg,Quantity(2.0,Measure.PIECES,50.0)),MealItem(oats,Quantity(50.0,Measure.GRAMS))))
        val entries=draft.entries(now,ZoneId.of("Asia/Baku"))
        assertEquals(2,entries.size);assertEquals(listOf(100.0,50.0),entries.map{it.grams})
        assertEquals(344.5,summarize(entries).kcal.value,1e-9)
        assertTrue(entries.all{it.localDate=="2026-10-29"});assertEquals(draft.items.map{it.id},entries.map{it.id})
    }
    @Test fun savedMealsKeepNutritionWhenTheOriginalFoodChangesOrDisappears() {
        val item=MealItem(egg,Quantity(2.0,Measure.PIECES,50.0))
        val meal=SavedMeal("meal:${UUID.randomUUID()}","Завтрак",listOf(item))
        val snapshot=UserSnapshot(favoriteFoods=setOf(egg.id),savedMeals=listOf(meal))
        val restored=BackupCodec.decode(BackupCodec.encode(snapshot))
        assertEquals(snapshot,restored)
        assertEquals(155.0,calculate(restored.savedMeals.single().items.single().food,item.quantity).nutrients.kcal!!,0.0)
        assertTrue(BackupCodec.decode("""{"format":"healthchecker-backup","schemaVersion":6,"snapshot":{}}""".toByteArray()).savedMeals.isEmpty())
    }
    @Test fun invalidAndDuplicateMealItemsAreRejected() {
        val item=MealItem(egg,Quantity(100.0,Measure.GRAMS))
        assertThrows(IllegalArgumentException::class.java){validateSnapshot(UserSnapshot(savedMeals=listOf(SavedMeal("meal:1"," ",listOf(item)))))}
        assertThrows(IllegalArgumentException::class.java){validateSnapshot(UserSnapshot(savedMeals=listOf(SavedMeal("meal:1","Breakfast",listOf(item,item)))))}
        assertThrows(IllegalArgumentException::class.java){MealDraft(UUID.randomUUID().toString(),"2026-10-29",listOf(item.copy(quantity=Quantity(-1.0,Measure.GRAMS)))).entries(now,ZoneId.of("Asia/Baku"))}
    }
}
