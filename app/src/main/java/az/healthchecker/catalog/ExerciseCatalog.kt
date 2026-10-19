package az.healthchecker.catalog
import android.content.Context
import az.healthchecker.domain.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
class ExerciseCatalog(context:Context) {
    val exercises:List<Exercise> = context.assets.open("exercises/exercises.json").bufferedReader().use{Json.decodeFromString(it.readText())}
    private val ids=exercises.associateBy{it.id}
    private val index=exercises.map{it to normalizeSearch(it.name+" "+it.aliases.joinToString(" "))}
    fun byId(id:String):Exercise? = ids[id]
    fun search(query:String,muscle:Muscle?,kind:ExerciseKind?,dumbbells:Boolean):List<Exercise> {
        val terms=normalizeSearch(query).split(Regex("\\s+")).filter{it.isNotEmpty()}
        return index.filter{(e,text)->terms.all{text.contains(it)}&&(muscle==null||muscle in e.muscles)&&(kind==null||e.kind==kind)&&(dumbbells||e.equipment==Equipment.NONE)}
            .map{it.first}.sortedWith(compareBy({it.level},{it.name}))
    }
    companion object {
        @Volatile private var loaded:ExerciseCatalog?=null
        fun shared(context:Context):ExerciseCatalog = loaded?:synchronized(this){loaded?:ExerciseCatalog(context.applicationContext).also{loaded=it}}
    }
}
