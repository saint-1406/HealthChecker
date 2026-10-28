package az.healthchecker.domain

/** Stopwatch on a monotonic clock (milliseconds); pausing keeps the time, so sets can be timed with breaks between. */
data class Stopwatch(val accumulatedMs:Long=0,val startedAt:Long?=null) {
    val running:Boolean get() = startedAt!=null
    fun elapsed(now:Long):Long = accumulatedMs+(startedAt?.let{(now-it).coerceAtLeast(0)}?:0)
    fun start(now:Long):Stopwatch = if(running)this else copy(startedAt=now)
    fun pause(now:Long):Stopwatch = if(running)Stopwatch(elapsed(now),null) else this
    fun reset():Stopwatch = Stopwatch()
    /** Jumps to [ms] and keeps running or stays paused, as before (following a video from a chosen exercise). */
    fun seek(now:Long,ms:Long):Stopwatch = Stopwatch(ms.coerceAtLeast(0),if(running)now else null)
}

/** 0:07.3, 12:05.0 or 1:02:03 once past an hour. */
fun formatStopwatch(ms:Long):String {
    val total=ms.coerceAtLeast(0)/100;val tenths=total%10;val s=total/10
    return if(s>=3600)String.format(java.util.Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60) else String.format(java.util.Locale.ROOT,"%d:%02d.%d",s/60,s%60,tenths)
}

/**
 * Rest or hold timer on the same monotonic clock: [endsAt] is set while it runs, [leftMs] keeps the time while paused.
 * [label] says what is being timed ("Rest", "Plank · 30 s").
 */
data class Countdown(val totalMs:Long,val leftMs:Long=totalMs,val endsAt:Long?=null,val label:String="") {
    val running:Boolean get() = endsAt!=null
    fun remaining(now:Long):Long = endsAt?.let{(it-now).coerceAtLeast(0)}?:leftMs
    fun finished(now:Long):Boolean = remaining(now)==0L
    fun pause(now:Long):Countdown = if(running)copy(leftMs=remaining(now),endsAt=null) else this
    fun resume(now:Long):Countdown = if(running||leftMs<=0)this else copy(endsAt=now+leftMs)
    /** Adds (or with a negative [ms] takes away) time; never below zero. */
    fun plus(now:Long,ms:Long):Countdown {
        val left=(remaining(now)+ms).coerceAtLeast(0)
        return copy(totalMs=(totalMs+ms).coerceAtLeast(left),leftMs=left,endsAt=endsAt?.let{now+left})
    }
    companion object {
        fun start(now:Long,ms:Long,label:String=""):Countdown { require(ms>0);return Countdown(ms,ms,now+ms,label) }
    }
}

/** Whole seconds rounded up, so a timer shows 0:01 until the very end: 1:30, 0:05. */
fun formatCountdown(ms:Long):String { val s=(ms.coerceAtLeast(0)+999)/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60) }
