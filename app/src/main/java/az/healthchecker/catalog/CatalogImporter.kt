package az.healthchecker.catalog
import android.content.Context
import az.healthchecker.domain.Food
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
class CatalogImporter(context:Context) {
    val foods:List<Food> = context.assets.open("catalog/foods.json").bufferedReader().use{Json.decodeFromString(it.readText())}
    private val index=foods.map{it to normalizeSearch(it.name+" "+it.aliases.joinToString(" "))}
    fun search(query:String,custom:List<Food>,limit:Int=50):List<Food> {
        val terms=normalizeSearch(query).split(Regex("\\s+")).filter{it.isNotEmpty()}
        return (custom.map{it to normalizeSearch(it.name+" "+it.aliases.joinToString(" "))}+index).asSequence().filter{(_,text)->terms.all{text.contains(it)}}.take(limit).map{it.first}.toList()
    }
}
