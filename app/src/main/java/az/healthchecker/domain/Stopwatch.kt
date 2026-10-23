package az.healthchecker.domain

/** Stopwatch on a monotonic clock (milliseconds); pausing keeps the time, so sets can be timed with breaks between. */
data class Stopwatch(val accumulatedMs:Long=0,val startedAt:Long?=null) {
    val running:Boolean get() = startedAt!=null
    fun elapsed(now:Long):Long = accumulatedMs+(startedAt?.let{(now-it).coerceAtLeast(0)}?:0)
    fun start(now:Long):Stopwatch = if(running)this else copy(startedAt=now)
    fun pause(now:Long):Stopwatch = if(running)Stopwatch(elapsed(now),null) else this
    fun reset():Stopwatch = Stopwatch()
}

/** 0:07.3, 12:05.0 or 1:02:03 once past an hour. */
fun formatStopwatch(ms:Long):String {
    val total=ms.coerceAtLeast(0)/100;val tenths=total%10;val s=total/10
    return if(s>=3600)String.format(java.util.Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60) else String.format(java.util.Locale.ROOT,"%d:%02d.%d",s/60,s%60,tenths)
}
