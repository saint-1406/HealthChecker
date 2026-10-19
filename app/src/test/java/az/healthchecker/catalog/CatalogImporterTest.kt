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
class CatalogImporterTest {
    private val catalog by lazy{CatalogImporter(ApplicationProvider.getApplicationContext<Context>())}
    private fun ids(query:String,custom:List<Food> = emptyList())=catalog.search(query,custom).map{it.id}
    @Test fun bundledCatalogHasSourcedFoodsAndWater() {
        assertEquals(7793,catalog.foods.size);assertEquals(catalog.foods.size,catalog.foods.map{it.id}.toSet().size)
        val water=catalog.foods.single{it.isWater};assertEquals("usda:173647",water.id);assertEquals(1.0,water.density!!,0.0)
    }
    @Test fun azerbaijaniAndRussianSpellingsFindTheSameEgg() {
        listOf("yumurta","YUMURTA","вареное яйцо","варёное яйцо").forEach{assertTrue(it,"usda:173424" in ids(it))}
        assertTrue("usda:171284" in ids("QATIQ"))
    }
    @Test fun rawAndCookedOatsStaySeparate() { val oats=ids("овсяные хлопья");assertTrue("usda:173904" in oats);assertFalse(catalog.foods.single{it.id=="usda:173904"}.name==catalog.foods.single{it.id=="usda:173905"}.name) }
    @Test fun emptyQueryIsLimitedAndCustomFoodsComeFirst() {
        val custom=Food("custom:1","Лаваш Bakı",per100g=Nutrients(250.0))
        val result=ids("",listOf(custom));assertEquals(50,result.size);assertEquals("custom:1",result.first())
        assertTrue(ids("бессмыслица-xyz").isEmpty())
    }
}
