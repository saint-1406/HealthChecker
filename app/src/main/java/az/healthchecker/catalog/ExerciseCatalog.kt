package az.healthchecker.catalog
import android.content.Context
import az.healthchecker.domain.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/** Words people type for a muscle group, direction or equipment, in both languages. */
private val MUSCLE_WORDS=mapOf(
    Muscle.LEGS to "ноги нога бедра бедро икры квадрицепс legs leg thighs calves quads",
    Muscle.GLUTES to "ягодицы ягодичные попа glutes glute butt",
    Muscle.CHEST to "грудь грудные chest pecs",
    Muscle.BACK to "спина поясница осанка back posture",
    Muscle.SHOULDERS to "плечи дельты shoulders delts",
    Muscle.ARMS to "руки бицепс трицепс arms biceps triceps",
    Muscle.CORE to "пресс кор живот abs core belly")
private val CATEGORY_WORDS=mapOf(
    ExerciseCategory.WARMUP to "разминка разогрев warm-up warmup",
    ExerciseCategory.STRENGTH to "силовые силовое сила strength",
    ExerciseCategory.AEROBIC to "аэробика кардио aerobics aerobic cardio",
    ExerciseCategory.ENDURANCE to "выносливость endurance stamina",
    ExerciseCategory.HIIT to "интервальная интервалы табата hiit intervals",
    ExerciseCategory.STRETCH to "растяжка заминка stretch stretching cooldown",
    ExerciseCategory.YOGA to "йога пилатес yoga pilates",
    ExerciseCategory.BALANCE to "баланс равновесие balance")
private const val DUMBBELL_WORDS="гантели гантель гантелями dumbbell dumbbells"

/** One exercise prepared for search: titles (Russian, English, video names) rank above the extra search words. */
class ExerciseSearchEntry(val exercise:Exercise) {
    private val ru=searchWords(exercise.name);private val en=searchWords(exercise.nameEn?:"");private val video=exercise.videoNames.map(::searchWords)
    private val titles=ru+en+video.flatten()
    private val extra=(exercise.aliases+exercise.keywords+listOfNotNull(CATEGORY_WORDS[exercise.category],if(exercise.equipment==Equipment.DUMBBELLS)DUMBBELL_WORDS else null)+exercise.muscles.mapNotNull{MUSCLE_WORDS[it]}).flatMap(::searchWords)
    /** The whole query typed as a title, e.g. a video caption: "jog on the spot". */
    fun exactTitle(query:String):Boolean = listOf(ru,en).plus(video).any{it.isNotEmpty()&&it==searchWords(query)}
    fun leads(term:String):Boolean = listOf(ru,en).plus(video).any{it.firstOrNull()?.startsWith(term)==true}
    /** 0 a title word starts with the term, 1 a search word does, 2 another form of the word, 3 inside a word; null no match. */
    fun match(term:String,stem:String):Int? = when {
        titles.any{it.startsWith(term)} -> 0
        extra.any{it.startsWith(term)} -> 1
        stem!=term&&(titles.any{it.startsWith(stem)}||extra.any{it.startsWith(stem)}) -> 2
        term.length>=3&&(titles.any{term in it}||extra.any{term in it}) -> 3
        else -> null
    }
}

/** Every query word must match; exact titles first, then by match quality, title start, level and name length. */
fun rankExercises(query:String,entries:List<ExerciseSearchEntry>):List<Exercise> {
    val terms=searchWords(query)
    if(terms.isEmpty())return entries.map{it.exercise}.sortedWith(compareBy({it.category.ordinal},{it.level},{it.displayName}))
    val stems=terms.map(::searchStem)
    data class Ranked(val e:Exercise,val exact:Int,val score:Int,val lead:Int)
    return entries.mapNotNull{entry->
        var score=0
        for(i in terms.indices)score+=entry.match(terms[i],stems[i])?:return@mapNotNull null
        Ranked(entry.exercise,if(entry.exactTitle(query))0 else 1,score,if(entry.leads(terms[0])||entry.leads(stems[0]))0 else 1)
    }.sortedWith(compareBy({it.exact},{it.score},{it.lead},{it.e.level},{it.e.displayName.length})).map{it.e}
}

class ExerciseCatalog(context:Context) {
    val exercises:List<Exercise> = context.assets.open("exercises/exercises.json").bufferedReader().use{Json.decodeFromString(it.readText())}
    private val ids=exercises.associateBy{it.id}
    /** Original motion schemes drawn by the app (tools/exercises/figures.py), one per exercise. */
    private val figures:Map<String,FigureMotion> = context.assets.open("exercises/figures.json").bufferedReader().use{Json.decodeFromString<List<FigureMotion>>(it.readText())}.associateBy{it.id}
    private val index=exercises.map(::ExerciseSearchEntry)
    fun byId(id:String):Exercise? = ids[id]
    fun figure(id:String):FigureMotion? = figures[id]
    fun search(query:String,muscle:Muscle?,category:ExerciseCategory?,dumbbells:Boolean):List<Exercise> =
        rankExercises(query,index.filter{e->(muscle==null||muscle in e.exercise.muscles)&&(category==null||e.exercise.category==category)&&(dumbbells||e.exercise.equipment==Equipment.NONE)})
    companion object {
        @Volatile private var loaded:ExerciseCatalog?=null
        fun shared(context:Context):ExerciseCatalog = loaded?:synchronized(this){loaded?:ExerciseCatalog(context.applicationContext).also{loaded=it}}
    }
}
