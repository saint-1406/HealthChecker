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
        assertEquals(7834,catalog.foods.size);assertEquals(catalog.foods.size,catalog.foods.map{it.id}.toSet().size)
        val water=catalog.foods.single{it.isWater};assertEquals("usda:173647",water.id);assertEquals(1.0,water.density!!,0.0)
    }
    @Test fun azerbaijaniAndRussianSpellingsFindTheSameEgg() {
        listOf("yumurta","YUMURTA","вареное яйцо","варёное яйцо").forEach{assertTrue(it,"usda:173424" in ids(it))}
        assertTrue("usda:171284" in ids("QATIQ"))
    }
    @Test fun rawAndCookedOatsStaySeparate() { val oats=ids("овсяные хлопья");assertTrue("usda:173904" in oats);assertFalse(catalog.foods.single{it.id=="usda:173904"}.name==catalog.foods.single{it.id=="usda:173905"}.name) }
    @Test fun everyFoodHasRussianAndEnglishNames() {
        val cyrillic=Regex("[а-яё]",RegexOption.IGNORE_CASE)
        catalog.foods.forEach{f->assertTrue(f.id,cyrillic.containsMatchIn(f.name));assertFalse(f.id,f.nameEn.isNullOrBlank());f.portions.forEach{p->assertFalse(f.id,p.nameEn.isNullOrBlank())}}
        assertTrue(catalog.foods.count{it.per100g.fiber!=null}>7000)
    }
    @Test fun searchWorksInBothLanguagesWithEverydayFoodsFirst() {
        assertTrue("usda:173424" in ids("boiled egg"));assertTrue("usda:173424" in ids("hard-boiled eggs"))
        assertEquals("usda:171478",ids("грудка варёная").first());assertEquals("usda:171478",ids("отварная куриная грудка").first())
        assertEquals(listOf("usda:171891","local:espresso-double"),ids("эспрессо").take(2))
        assertEquals("local:espresso-double",ids("эспрессо двойной").first())
        assertTrue(ids("avocado").first().let{catalog.byId(it)!!.name.startsWith("Авокадо")})
        assertTrue(ids("говядина").isNotEmpty()&&ids("beef").isNotEmpty())
    }
    @Test fun bakuRestaurantDishesKeepTheGivenNumbers() {
        val khinkali=catalog.byId("local:xengel-soyutma")!!
        val one=calculate(khinkali,Quantity(1.0,Measure.PIECES,85.0)).nutrients
        assertEquals(170.0,one.kcal!!,0.01);assertEquals(7.8,one.protein!!,0.01);assertEquals(6.3,one.fat!!,0.01);assertTrue(one.carbs!!>15&&one.fiber!!>0)
        val tenderloin=calculate(catalog.byId("local:can-eti-nar-qovurma")!!,Quantity(325.0,Measure.GRAMS)).nutrients
        assertEquals(550.0,tenderloin.kcal!!,0.1);assertEquals(48.0,tenderloin.protein!!,0.1);assertEquals(30.0,tenderloin.fat!!,0.1)
        assertEquals(60.0,calculate(catalog.byId("local:tempura-krevetka")!!,Quantity(1.0,Measure.PIECES,25.0)).nutrients.kcal!!,0.01)
        assertEquals(171.0,calculate(catalog.byId("local:puste-antep")!!,Quantity(30.0,Measure.GRAMS)).nutrients.kcal!!,0.01)
        assertEquals(140.0,calculate(catalog.byId("local:sosiska-assorti")!!,Quantity(50.0,Measure.GRAMS)).nutrients.kcal!!,0.01)
        listOf("xəngəl","хинкали","Can əti nar qovurma","düşbərə","дюшбара","popkorn","темпура","sosiska","püstə","фисташки антеп").forEach{q->assertTrue(q,ids(q).any{it.startsWith("local:")})}
    }
    @Test fun sushiPiecesKeepTheGivenEstimates() {
        fun piece(id:String):Nutrients { val f=catalog.byId(id)!!;return calculate(f,Quantity(1.0,Measure.PIECES,f.portions.first().grams)).nutrients }
        // Middle of the user's ranges per piece; fat is what the energy leaves after protein and carbohydrate.
        val philadelphia=piece("local:sushi-philadelphia")
        assertEquals(57.5,philadelphia.kcal!!,0.01);assertEquals(2.5,philadelphia.protein!!,0.01);assertEquals(6.0,philadelphia.carbs!!,0.01)
        assertEquals((57.5-4*2.5-4*6.0)/9,philadelphia.fat!!,0.01)
        assertEquals(30.0,piece("local:sushi-sake-maki").kcal!!,0.01);assertEquals(80.0,piece("local:sushi-hot-salmon").kcal!!,0.01)
        assertEquals(45.0,piece("local:sushi-chuka").kcal!!,0.01);assertEquals(1.0,piece("local:sushi-chuka").protein!!,0.01)
        // A whole roll is eight pieces.
        val roll=catalog.byId("local:sushi-california")!!
        assertEquals(8*50.0,calculate(roll,Quantity(1.0,Measure.PIECES,roll.portions[1].grams)).nutrients.kcal!!,0.1)
        assertEquals(32,catalog.foods.count{it.id.startsWith("local:sushi-")})
        catalog.foods.filter{it.id.startsWith("local:sushi-")}.forEach{assertNotNull(it.id,it.per100g.fiber);assertTrue(it.id,it.common)}
    }
    @Test fun sushiIsFoundInRussianEnglishAndAzerbaijani() {
        assertEquals("local:sushi-philadelphia",ids("филадельфия").first());assertEquals("local:sushi-philadelphia",ids("filadelfiya").first())
        assertEquals("local:sushi-california",ids("kaliforniya").first());assertEquals("local:sushi-hot-ebi",ids("горячий эби").first())
        assertTrue(ids("суши").take(20).all{it.startsWith("local:sushi-")})
        assertTrue(ids("ролл").first().startsWith("local:sushi-"))
        listOf("гункан","сашими лосось","нигири","маки","tekka","мисо суп","чука").forEach{q->assertTrue(q,ids(q).first().startsWith("local:sushi-"))}
        assertEquals("usda:174277",ids("соевый соус").first());assertEquals("usda:171831",ids("васаби").first())
    }
    @Test fun emptyQueryIsLimitedAndCustomFoodsComeFirst() {
        val custom=Food("custom:1","Лаваш Bakı",per100g=Nutrients(250.0))
        val result=ids("",listOf(custom));assertEquals(50,result.size);assertEquals("custom:1",result.first())
        assertTrue(ids("бессмыслица-xyz").isEmpty())
    }
}
