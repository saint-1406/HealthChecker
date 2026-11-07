package az.healthchecker.catalog
import android.content.Context
import az.healthchecker.domain.Food
import az.healthchecker.domain.FoodFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
class CatalogImporter(context:Context) {
    val foods:List<Food> = context.assets.open("catalog/foods.json").bufferedReader().use{Json.decodeFromString(it.readText())}
    /** Hidden foods stay resolvable by id for old diary entries, favourites and meals, but search does not offer them. */
    private val index=foods.mapIndexedNotNull{i,f->if(f.hidden)null else SearchEntry(f,i,f.common)}
    private val ids=foods.associateBy{it.id}
    fun byId(id:String):Food? = ids[id]
    /** The user's own foods come first for an empty query and rank with everyday foods otherwise. */
    fun search(query:String,custom:List<Food>,limit:Int=50,filter:FoodFilter=FoodFilter.ALL,favorites:Set<String> = emptySet()):List<Food> {
        val own=custom.mapIndexed{i,f->SearchEntry(f,i-custom.size,true)}
        // A favourite stays listed even if the catalog later hides that food from general search.
        if(filter==FoodFilter.FAVORITES)return rankSearch(query,(own+foods.mapIndexed{i,f->SearchEntry(f,i,f.common)}).filter{it.food.id in favorites},limit)
        return rankSearch(query,own+(if(filter==FoodFilter.OWN)emptyList()else index),limit)
    }
    companion object {
        @Volatile private var loaded:CatalogImporter?=null
        // The bundled catalog is immutable: parse it once per process instead of on every new screen owner.
        fun shared(context:Context):CatalogImporter = loaded?:synchronized(this){loaded?:CatalogImporter(context.applicationContext).also{loaded=it}}
    }
}
