package az.healthchecker.watch
import android.content.Context
import androidx.core.content.edit

/**
 * Days whose scale weigh-in was already taken from Health Connect. A device setting like the Health Connect access
 * itself: after a restore on another phone, days with no weight are filled from that phone's Health Connect again.
 */
object ScaleImport {
    private const val PREFS="watch";private const val SEEN="scaleWeightDays"
    fun seen(c:Context):Set<String> = c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getStringSet(SEEN,null)?.toSet()?:emptySet()
    /** Older days drop out: Health Connect never gives more than [HealthConnectSource.WEIGHT_DAYS] back anyway. */
    fun remember(c:Context,days:Collection<String>,today:java.time.LocalDate=java.time.LocalDate.now()) {
        val keep=today.minusDays(60).toString()
        val all=(seen(c)+days).filter{it>=keep}.toSet()
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit{putStringSet(SEEN,all)}
    }
}
