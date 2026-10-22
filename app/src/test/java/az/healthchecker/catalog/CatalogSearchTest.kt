package az.healthchecker.catalog
import az.healthchecker.domain.*
import org.junit.Test
import org.junit.Assert.*
class CatalogSearchTest {
    @Test fun russianAndAzerbaijaniVariants() {
        assertEquals(normalizeSearch("İŞIQ"),normalizeSearch("ışıq"));assertEquals(normalizeSearch("Орёх"),normalizeSearch("орех"))
        // Azerbaijani letters typed on a keyboard without them still match.
        assertEquals(normalizeSearch("Xəngəl düşbərə çay"),normalizeSearch("xengel dusbere cay"))
    }
    @Test fun stemsKeepShortWordsAndCutCommonEndings() {
        assertEquals("грудк",searchStem("грудки"));assertEquals("варен",searchStem(normalizeSearch("варёная")));assertEquals("яйц",searchStem("яйца"))
        assertEquals("рис",searchStem("рис"));assertEquals("egg",searchStem("eggs"));assertEquals("tomato",searchStem("tomatoes"));assertEquals("berr",searchStem("berries"));assertEquals("glass",searchStem("glass"))
    }
    private fun food(id:String,ru:String,en:String?=null,aliases:List<String> = emptyList(),common:Boolean=false)=Food(id,ru,aliases,Nutrients(100.0),nameEn=en,common=common)
    private val foods=listOf(
        food("usda:1","Индейка, грудка, запечённая","Turkey, breast, roasted"),
        food("usda:2","Куриная грудка без кожи, отварная","Chicken, breast, meat only, stewed",listOf("курица варёная toyuq"),common=true),
        food("usda:3","Курица, бройлеры, грудка, сырая","Chicken, broilers, breast, raw"),
        food("usda:4","Кофе эспрессо","Beverages, coffee, brewed, espresso",listOf("espresso эспрессо"),common=true),
        food("usda:5","Напиток, кофе, эспрессо без кофеина","Beverages, coffee, brewed, espresso, decaffeinated"))
    private fun search(q:String)=rankSearch(q,foods.mapIndexed{i,f->SearchEntry(f,i,f.common)},50).map{it.id}
    @Test fun findsByRussianEnglishAndOtherWordForms() {
        assertEquals("usda:2",search("грудка варёная").first())
        assertEquals("usda:2",search("куриные грудки").first())
        assertEquals(listOf("usda:2","usda:3"),search("chicken breast"))
        assertTrue("usda:1" in search("turkey"))
        assertTrue(search("salmon").isEmpty())
    }
    @Test fun everydayFoodsAndNameStartsComeFirst() {
        assertEquals(listOf("usda:4","usda:5"),search("эспрессо"))
        assertEquals("usda:4",search("espresso").first())
        assertEquals("usda:3",search("курица").first())
    }
}
