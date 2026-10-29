package az.healthchecker.catalog
import android.content.Context
import az.healthchecker.domain.Food
import az.healthchecker.domain.FoodFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
class CatalogImporter(context:Context) {
    val foods:List<Food> = context.assets.open("catalog/foods.json").bufferedReader().use{Json.decodeFromString(it.readText())}
    private val index=foods.mapIndexed{i,f->SearchEntry(f,i,f.common)}
    private val ids=foods.associateBy{it.id}
    fun byId(id:String):Food? = ids[id]
    /** The user's own foods come first for an empty query and rank with everyday foods otherwise. */
    fun search(query:String,custom:List<Food>,limit:Int=50,filter:FoodFilter=FoodFilter.ALL,favorites:Set<String> = emptySet()):List<Food> {
        val pool=custom.mapIndexed{i,f->SearchEntry(f,i-custom.size,true)}+(if(filter==FoodFilter.OWN)emptyList()else index)
        return rankSearch(query,if(filter==FoodFilter.FAVORITES)pool.filter{it.food.id in favorites}else pool,limit)
    }
    companion object {
        @Volatile private var loaded:CatalogImporter?=null
        // The bundled catalog is immutable: parse it once per process instead of on every new screen owner.
        fun shared(context:Context):CatalogImporter = loaded?:synchronized(this){loaded?:CatalogImporter(context.applicationContext).also{loaded=it}}
    }
}
