package az.healthchecker.explore

import kotlin.math.*

/** Elapsed GNSS timestamp drives continuity; epoch time is only for displaying recorded points. */
data class GpsFix(val latitude: Double, val longitude: Double, val timeMillis: Long, val elapsedNanos: Long, val accuracyMeters: Double)
data class AcceptedGpsFix(val fix: GpsFix, val breakBefore: Boolean)

class GpsTrackFilter {
    private var previous: GpsFix? = null
    private var lastElapsed = Long.MIN_VALUE
    private var interrupted = true

    fun breakSegment() { interrupted = true }

    fun accept(fix: GpsFix): AcceptedGpsFix? {
        if (!fix.latitude.isFinite() || fix.latitude !in -90.0..90.0 || !fix.longitude.isFinite() || fix.longitude !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() || fix.accuracyMeters !in 0.0..35.0 || fix.elapsedNanos <= lastElapsed || fix.timeMillis <= 0) {
            breakSegment(); return null
        }
        val gap = lastElapsed != Long.MIN_VALUE && fix.elapsedNanos - lastElapsed > 30_000_000_000L
        lastElapsed = fix.elapsedNanos
        val old = previous
        if (old != null && !interrupted && !gap) {
            val distance = distance(old.latitude, old.longitude, fix.latitude, fix.longitude)
            val seconds = (fix.elapsedNanos - old.elapsedNanos) / 1e9
            if (seconds <= 0 || distance / seconds > 8.0) { breakSegment(); return null }
            // Keep the motion anchor fixed while stationary; discarded jitter never accumulates distance.
            if (distance < max(8.0, old.accuracyMeters + fix.accuracyMeters)) return null
        }
        val accepted = AcceptedGpsFix(fix, old == null || interrupted || gap)
        previous = fix
        interrupted = false
        return accepted
    }

    private fun distance(a: Double, b: Double, c: Double, d: Double): Double {
        val lat = Math.toRadians(c - a); val lon = Math.toRadians(d - b)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a)) * cos(Math.toRadians(c)) * sin(lon / 2).pow(2)
        return 12_742_000 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

class RecordingClock {
    private var saved = 0L
    private var started: Long? = null
    @Synchronized fun start(savedDurationMs: Long, elapsedMs: Long) { saved = savedDurationMs; started = elapsedMs }
    @Synchronized fun duration(elapsedMs: Long): Long = saved + (started?.let { (elapsedMs - it).coerceAtLeast(0) } ?: 0)
    @Synchronized fun pause(elapsedMs: Long): Long { saved = duration(elapsedMs); started = null; return saved }
}
