package az.healthchecker.domain
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * One exercise as a workout video runs it, from [start] to [end] seconds; the gaps between segments are the video's rests.
 * [work] is the exercise's own time when the segment also holds a rest (timed by the author's chapters); null — all of it.
 */
@Serializable data class VideoSegment(val start:Int,val end:Int,val exerciseId:String,val title:String,val section:String="",val sectionEn:String="",val note:String="",val noteEn:String="",val work:Int?=null) {
    val seconds:Int get() = end-start
    val workSeconds:Int get() = work?:seconds
}
/**
 * A workout video: on YouTube, a file on the phone, or both. The built-in videos carry their exercise timeline; a video the
 * user adds has none and is logged as one video workout. [embeddable] false: the owner does not allow the video in other apps.
 */
@Serializable data class WorkoutVideo(val id:String,val title:String,val channel:String="",val youtubeId:String?=null,val durationSec:Int?=null,val embeddable:Boolean=true,
    val about:String="",val aboutEn:String="",val segments:List<VideoSegment> = emptyList(),val addedAt:String?=null,
    /** "exact" — timed from the video itself; "chapters" — from the author's chapter list in the description. */
    val timing:String="exact",
    /** What kind of workout it is, for the library filter: full, abs, cardio, legs, upper, dumbbells, nojump, warmup. */
    val tags:List<String> = emptyList()) {
    val own:Boolean get() = id.startsWith("video:")
    val byChapters:Boolean get() = timing=="chapters"
}
/** The user's own copy of a video on this phone: a document they picked, kept by its persistable link. */
@Serializable data class VideoFile(val uri:String,val name:String)

val VideoSegment.displaySection:String get() = if(Lang.en)sectionEn.ifBlank{section} else section
val VideoSegment.displayNote:String get() = if(Lang.en)noteEn.ifBlank{note} else note
val WorkoutVideo.displayAbout:String get() = if(Lang.en)aboutEn.ifBlank{about} else about

private val YOUTUBE_ID=Regex("[A-Za-z0-9_-]{11}")
/** The video id from a YouTube link (watch, youtu.be, shorts, embed, live) or a bare id; null for anything else. */
fun youtubeId(text:String):String? {
    val t=text.trim()
    if(YOUTUBE_ID.matches(t))return t
    val uri=runCatching{java.net.URI(if(t.contains("://"))t else "https://$t")}.getOrNull()?:return null
    val host=uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("music.")?:return null
    val path=uri.path.orEmpty().trim('/').split('/')
    val id=when(host){
        "youtu.be"->path.firstOrNull()
        "youtube.com","youtube-nocookie.com"->when(path.firstOrNull()){
            "watch"->uri.rawQuery.orEmpty().split('&').firstOrNull{it.startsWith("v=")}?.removePrefix("v=")
            "shorts","embed","live","v"->path.getOrNull(1)
            else->null
        }
        else->null
    }
    return id?.takeIf{YOUTUBE_ID.matches(it)}
}
/** Opens the video in YouTube (the app or the browser) at [second]. */
fun youtubeWatchUrl(id:String,second:Int=0):String = "https://www.youtube.com/watch?v=$id"+if(second>0)"&t=${second}s" else ""

/** Where a video is at a second: the exercise running and the time it has left, or the rest before [next]. */
data class VideoMoment(val current:VideoSegment?,val next:VideoSegment?,val index:Int,val left:Int) {
    val finished:Boolean get() = current==null&&next==null
}
fun WorkoutVideo.moment(second:Int):VideoMoment {
    val i=segments.indexOfFirst{second<it.end}
    if(i<0)return VideoMoment(null,null,segments.size,0)
    val s=segments[i]
    return if(second>=s.start)VideoMoment(s,segments.getOrNull(i+1),i,s.end-second) else VideoMoment(null,s,i,s.start-second)
}
val WorkoutVideo.workSeconds:Int get() = segments.sumOf{it.workSeconds}

/** Lengths the library filters by, in whole minutes of the video. */
enum class VideoLength(val minutes:IntRange) { SHORT(0..12), MEDIUM(13..22), LONG(23..1440) }
/**
 * The library filter: every word of [query] in the title or in an exercise of the video ([exerciseNames] gives an exercise's
 * names in both languages), a kind of workout from [tag], a length, only the user's own videos.
 */
fun filterVideos(videos:List<WorkoutVideo>,query:String,tag:String?,length:VideoLength?,ownOnly:Boolean,exerciseNames:(String)->String):List<WorkoutVideo> {
    val words=query.lowercase().split(' ').filter{it.isNotBlank()}
    return videos.filter{v->
        (!ownOnly||v.own)&&(tag==null||tag in v.tags)&&(length==null||v.durationSec?.let{(it+30)/60 in length.minutes}==true)&&
            words.all{w->v.title.lowercase().contains(w)||v.segments.any{s->s.title.lowercase().contains(w)||exerciseNames(s.exerciseId).lowercase().contains(w)}}
    }
}

/** An exercise of a video workout as it goes to the diary: its segments up to a point, added together. */
data class VideoWork(val exerciseId:String,val parts:Int,val seconds:Int)
/** A segment cut off sooner than this does not count as done. */
const val MIN_PART_SECONDS=10
/**
 * The exercises done in the video up to [upTo] seconds (the whole video when null), in order of first appearance; an
 * exercise the video repeats (jumping jacks in the warm-up and in the cardio) becomes one entry with several sets.
 */
fun WorkoutVideo.workDone(upTo:Int?=null):List<VideoWork> {
    val done=LinkedHashMap<String,MutableList<Int>>()
    for(s in segments){
        // Of a segment that also holds a rest, only its work counts, however far into it the video got.
        val seconds=minOf(s.workSeconds,if(upTo==null)s.seconds else minOf(s.end,upTo)-s.start)
        if(seconds>=minOf(MIN_PART_SECONDS,s.workSeconds))done.getOrPut(s.exerciseId){mutableListOf()}+=seconds
    }
    return done.map{(id,parts)->VideoWork(id,parts.size,parts.sum())}
}
/**
 * A diary entry for an exercise done along with a video. The time is the video's work time (rests are not counted);
 * repetitions are estimated from the exercise tempo, so the entry stays editable like any other.
 */
fun videoWorkout(id:String,exercise:Exercise,work:VideoWork,date:String,now:Instant,zone:ZoneId,bodyWeightKg:Double?):WorkoutEntry {
    val each=work.seconds.toDouble()/work.parts;val minutes=work.seconds/60.0
    return when(exercise.measure){
        ExerciseMeasure.REPS->makeWorkout(id,exercise,work.parts,(each/(exercise.secondsPerRep?:3.0)).roundToInt().coerceIn(1,1000),null,null,minutes,date,null,now,zone,bodyWeightKg)
        ExerciseMeasure.SECONDS->makeWorkout(id,exercise,work.parts,null,each.roundToInt().coerceIn(1,3600),null,minutes,date,null,now,zone,bodyWeightKg)
        ExerciseMeasure.MINUTES->makeWorkout(id,exercise,null,null,null,null,minutes,date,null,now,zone,bodyWeightKg)
    }
}
/** "1:05", "30:12". */
fun formatVideoTime(seconds:Int):String { val s=seconds.coerceAtLeast(0);return if(s>=3600)String.format(java.util.Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60) else String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60) }

/** The user's own video as it is saved: a title, and a YouTube link or nothing (then a file on the phone plays it). */
fun makeOwnVideo(id:String,title:String,link:String,addedAt:Instant):WorkoutVideo {
    val name=title.trim();val yt=link.takeIf{it.isNotBlank()}?.let{youtubeId(it)?:throw IllegalArgumentException(tr("Это не похоже на ссылку YouTube","This does not look like a YouTube link"))}
    require(name.isNotEmpty()){tr("Назовите видео","Give the video a name")}
    require(name.length<=200){tr("Название слишком длинное","The name is too long")}
    return WorkoutVideo(id,name,youtubeId=yt,addedAt=addedAt.toString())
}
/**
 * A built-in video as the user changed it: their title, description and link over the bundled timeline. A new link
 * points at another video, whose embedding is unknown, so the player is tried.
 */
fun WorkoutVideo.withEdits(edit:WorkoutVideo?):WorkoutVideo = if(edit==null)this else
    copy(title=edit.title,about=edit.about,aboutEn=edit.aboutEn,youtubeId=edit.youtubeId,embeddable=if(edit.youtubeId==youtubeId)embeddable else true)
/** The user's title, description and YouTube link (blank removes it) on a video, checked like a new one; one text serves both languages. */
fun editVideo(video:WorkoutVideo,title:String,about:String,link:String):WorkoutVideo {
    val name=title.trim();val text=about.trim()
    require(name.isNotEmpty()){tr("Назовите видео","Give the video a name")}
    require(name.length<=200){tr("Название слишком длинное","The name is too long")}
    require(text.length<=2000){tr("Описание слишком длинное","The description is too long")}
    val yt=link.takeIf{it.isNotBlank()}?.let{youtubeId(it)?:throw IllegalArgumentException(tr("Это не похоже на ссылку YouTube","This does not look like a YouTube link"))}
    return video.copy(title=name,about=text,aboutEn=text,youtubeId=yt)
}
/** Ids of the built-in videos; the user's changes to one are saved under the same id. */
private val BUILT_IN_VIDEO_ID=Regex("yt:[A-Za-z0-9_-]{11}")
fun validateVideo(v:WorkoutVideo) {
    require((v.id.startsWith("video:")||BUILT_IN_VIDEO_ID.matches(v.id))&&v.id.length<=100&&v.title.isNotBlank()&&v.title.length<=200&&v.channel.length<=200&&v.about.length<=2000&&v.aboutEn.length<=2000)
    require(v.youtubeId==null||YOUTUBE_ID.matches(v.youtubeId));require(v.durationSec==null||v.durationSec in 1..86400)
    require(v.segments.size<=500&&v.segments.all{it.start>=0&&it.end>it.start&&it.end<=86400&&EXERCISE_ID.matches(it.exerciseId)&&it.title.length<=200&&(it.work==null||it.work in 1..it.seconds)})
    require(v.timing.length<=20&&v.tags.size<=20&&v.tags.all{it.length<=20})
    v.addedAt?.let(Instant::parse)
}
