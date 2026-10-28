package az.healthchecker.catalog
import android.content.Context
import az.healthchecker.domain.WorkoutVideo
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/** The built-in workout videos with their exercise timelines (tools/exercises/videos.py). */
class VideoLibrary(context:Context) {
    val videos:List<WorkoutVideo> = context.assets.open("videos/library.json").bufferedReader().use{Json.decodeFromString(it.readText())}
    companion object {
        @Volatile private var loaded:VideoLibrary?=null
        fun shared(context:Context):VideoLibrary = loaded?:synchronized(this){loaded?:VideoLibrary(context.applicationContext).also{loaded=it}}
    }
}
