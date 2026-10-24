package az.healthchecker.domain
import java.time.Instant
import java.time.ZoneId

/** What a kept photo is for: a food or a medicine / vitamin to log later. */
enum class PhotoKind { FOOD, MEDICINE }

/**
 * A photo kept for later ("Photos for later"). Its file name carries the kind and the moment it was taken, so the inbox
 * needs no database rows: food-1761300000000.jpg.
 */
data class InboxPhoto(val kind:PhotoKind,val takenAt:Long) {
    val fileName:String get() = (if(kind==PhotoKind.FOOD)"food" else "med")+"-$takenAt.jpg"
    /** The day the photo was taken: what was photographed is logged on that day. */
    fun date(zone:ZoneId):String = Instant.ofEpochMilli(takenAt).atZone(zone).toLocalDate().toString()
    companion object {
        private val NAME=Regex("""(food|med)-(\d{1,15})\.jpg""")
        fun parse(name:String):InboxPhoto? = NAME.matchEntire(name)?.let{InboxPhoto(if(it.groupValues[1]=="food")PhotoKind.FOOD else PhotoKind.MEDICINE,it.groupValues[2].toLong())}
    }
}
