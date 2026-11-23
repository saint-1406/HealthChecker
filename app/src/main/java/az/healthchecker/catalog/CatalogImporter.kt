package az.healthchecker.catalog
import android.content.Context
import az.healthchecker.domain.Food
import az.healthchecker.domain.FoodFilter
import az.healthchecker.domain.FoodCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
class CatalogImporter(context:Context) {
    val foods:List<Food> = context.assets.open("catalog/foods.json").bufferedReader().use{Json.decodeFromString(it.readText())}
    /** Hidden foods stay resolvable by id for old diary entries, favourites and meals, but search does not offer them. */
    private val index=foods.mapIndexedNotNull{i,f->if(f.hidden)null else SearchEntry(f,i,f.common)}
    private val ids=foods.associateBy{it.id}
    fun byId(id:String):Food? = ids[id]
    /** The user's own foods come first for an empty query and rank with everyday foods otherwise. */
    /** [category] narrows the search to one shelf; the user's own foods have none, so they drop out of it. */
    fun search(query:String,custom:List<Food>,limit:Int=50,filter:FoodFilter=FoodFilter.ALL,favorites:Set<String> = emptySet(),category:FoodCategory?=null):List<Food> {
        val own=custom.mapIndexed{i,f->SearchEntry(f,i-custom.size,true)}
        fun List<SearchEntry>.shelf()=if(category==null)this else filter{it.food.category==category.id}
        // A favourite stays listed even if the catalog later hides that food from general search.
        if(filter==FoodFilter.FAVORITES)return rankSearch(query,(own+foods.mapIndexed{i,f->SearchEntry(f,i,f.common)}).filter{it.food.id in favorites}.shelf(),limit)
        val pool=own+(if(filter==FoodFilter.OWN)emptyList()else if(category==null)index else byCategory[category.id].orEmpty())
        return rankSearch(query,pool.shelf(),limit)
    }
    /** Each shelf's entries, so tapping a category does not scan the whole catalog. */
    private val byCategory:Map<String,List<SearchEntry>> = index.groupBy{it.food.category.orEmpty()}
    companion object {
        @Volatile private var loaded:CatalogImporter?=null
        // The bundled catalog is immutable: parse it once per process instead of on every new screen owner.
        fun shared(context:Context):CatalogImporter = loaded?:synchronized(this){loaded?:CatalogImporter(context.applicationContext).also{loaded=it}}
    }
}
