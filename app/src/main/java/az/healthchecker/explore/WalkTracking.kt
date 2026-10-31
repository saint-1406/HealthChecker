package az.healthchecker.explore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import az.healthchecker.data.ExploreRepository
import az.healthchecker.domain.GeoPoint
import az.healthchecker.domain.TrackPoint
import az.healthchecker.domain.WalkStatus
import az.healthchecker.domain.tr
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WalkRecordingState(
    val walkId: String? = null,
    val latestPoint: TrackPoint? = null,
    val durationMs: Long = 0,
    val waitingForFix: Boolean = false,
    val message: String? = null
)

object WalkTracking {
    private val mutableState = MutableStateFlow(WalkRecordingState())
    val state: StateFlow<WalkRecordingState> = mutableState.asStateFlow()
    private val requests = ConcurrentHashMap<Long, CompletableDeferred<String?>>()
    private val sequence = AtomicLong()
    private val lifecycle = Mutex()
    @Volatile internal var running = false
    @Volatile internal var drained: Deferred<Unit> = CompletableDeferred(Unit)

    internal fun publish(value: WalkRecordingState) { mutableState.value = value }
    internal fun complete(id: Long, result: String?, error: Throwable? = null) {
        requests.remove(id)?.let { if (error == null) it.complete(result) else it.completeExceptionally(error) }
    }
    internal fun isPending(id: Long) = requests.containsKey(id)

    /** Called when opening Explore, never starts a service or GPS. A dead process cannot silently resume. */
    suspend fun reconcile(context: Context) = lifecycle.withLock {
        if (!running && requests.isEmpty()) {
            drained.await()
            val repository = ExploreRepository(context.applicationContext)
            withContext(Dispatchers.IO) {
                repository.exportSnapshot().walks.filter { it.status == WalkStatus.ACTIVE }.forEach {
                    repository.updateWalk(it.id, WalkStatus.INTERRUPTED, it.durationMs)
                }
            }
        }
    }

    /** Must be invoked by a visible user action after precise foreground permission is granted. */
    suspend fun start(context: Context, walkId: String? = null): String = lifecycle.withLock {
        requireFineLocation(context)
        if (!running) drained.await()
        request(context, WalkTrackingService.START, walkId, foreground = true)!!
    }

    suspend fun pause(context: Context) = lifecycle.withLock {
        if (running) request(context, WalkTrackingService.PAUSE)
        else { drained.await(); updateStopped(context, WalkStatus.PAUSED) }
        Unit
    }

    suspend fun finish(context: Context) = lifecycle.withLock {
        if (running) request(context, WalkTrackingService.FINISH)
        else { drained.await(); updateStopped(context, WalkStatus.FINISHED) }
        Unit
    }

    /** Ack arrives after all queued writes, stopping GPS and the final durable checkpoint. */
    suspend fun stopForRestore(context: Context) = lifecycle.withLock {
        if (running) request(context, WalkTrackingService.INTERRUPT)
        else { drained.await(); updateStopped(context, WalkStatus.INTERRUPTED) }
        drained.await()
        Unit
    }

    private suspend fun updateStopped(context: Context, status: WalkStatus) = withContext(Dispatchers.IO) {
        val repo = ExploreRepository(context.applicationContext)
        repo.exportSnapshot().walks.filter { it.status == WalkStatus.ACTIVE || (status == WalkStatus.FINISHED && it.status == WalkStatus.PAUSED) }.forEach {
            repo.updateWalk(it.id, status, it.durationMs)
        }
        publish(WalkRecordingState())
    }

    private suspend fun request(context: Context, action: String, walkId: String? = null, foreground: Boolean = false): String? {
        val id = sequence.incrementAndGet()
        val answer = CompletableDeferred<String?>()
        requests[id] = answer
        try {
            val intent = Intent(context.applicationContext, WalkTrackingService::class.java).setAction(action).putExtra("request", id).putExtra("walkId", walkId)
            if (foreground) context.applicationContext.startForegroundService(intent) else context.applicationContext.startService(intent)
            return answer.await()
        } finally { requests.remove(id) }
    }

    internal fun requireFineLocation(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException(tr("Разрешите точную геолокацию для GPS", "Allow precise location for GPS"))
    }

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** "My location" also works with Android's approximate permission; walk recording still needs precise GPS. */
    fun hasAnyLocation(context: Context) =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    internal const val LOCATE_TIMEOUT_MS = 20_000L
    private const val ACCURATE_METERS = 35f
    private const val FRESH_NANOS = 15_000_000_000L
    private const val CACHED_NANOS = 120_000_000_000L

    private fun ageNanos(location: Location) = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
    private fun usable(location: Location, maxAgeNanos: Long) =
        location.hasAccuracy() && location.accuracy.isFinite() && location.accuracy >= 0f &&
            location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
            location.longitude.isFinite() && location.longitude in -180.0..180.0 &&
            ageNanos(location) in 0..maxAgeNanos
    private fun Location.toTrackPoint() = TrackPoint(GeoPoint(latitude, longitude), System.currentTimeMillis(), accuracy.toDouble())

    /**
     * One position for "My location" and place pins. GPS and Wi-Fi/cell (network) providers are queried together.
     * An accurate fix (≤35 m) or, with approximate permission, the first usable fix returns at once; a recent accurate
     * cached fix returns without waiting. Otherwise the best fresh fix seen within the timeout is returned with its
     * accuracy, so indoors the user still gets a point instead of an error. The listener is always unregistered.
     */
    suspend fun currentPosition(context: Context): TrackPoint {
        val app = context.applicationContext
        val fine = granted(app, Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(app, Manifest.permission.ACCESS_COARSE_LOCATION))
            throw SecurityException(tr("Разрешите доступ к геолокации", "Allow location access"))
        val manager = app.getSystemService(LocationManager::class.java)
            ?: error(tr("Геолокация недоступна", "Location is unavailable"))
        val providers = buildList {
            // Before Android 12 the GPS provider throws without precise permission; from 12 on it returns fuzzed fixes.
            if ((fine || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false))
                add(LocationManager.GPS_PROVIDER)
            if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false))
                add(LocationManager.NETWORK_PROVIDER)
        }
        check(providers.isNotEmpty()) { tr("Включите геолокацию в настройках телефона", "Turn on location in phone settings") }
        val cached = providers.mapNotNull {
            try { manager.getLastKnownLocation(it) }
            catch (_: SecurityException) { null } // Permission can be revoked after the initial check.
            catch (_: IllegalArgumentException) { null } // A provider may disappear while locating.
        }
            .filter { usable(it, CACHED_NANOS) }.minByOrNull { it.accuracy }
        if (cached != null && (cached.accuracy <= ACCURATE_METERS || !fine)) return cached.toTrackPoint()
        val best = AtomicReference<Location?>(null)
        val found = withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<Location> { continuation ->
                    val callback = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (!usable(location, FRESH_NANOS)) return
                            best.updateAndGet { old -> if (old == null || location.accuracy < old.accuracy) location else old }
                            if ((location.accuracy <= ACCURATE_METERS || !fine) && continuation.isActive) {
                                manager.removeUpdates(this)
                                continuation.resumeWith(Result.success(location))
                            }
                        }
                        // Older Android versions call these; without overrides a disabled provider would crash on API < 30.
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                        @Deprecated("Required on API < 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(callback) }
                    try {
                        if (!hasAnyLocation(app)) throw SecurityException(tr("Разрешите доступ к геолокации", "Allow location access"))
                        providers.forEach { manager.requestLocationUpdates(it, 1_000L, 0f, callback, Looper.getMainLooper()) }
                        if (!continuation.isActive) manager.removeUpdates(callback)
                    }
                    catch (denied: SecurityException) {
                        manager.removeUpdates(callback)
                        if (continuation.isActive) continuation.resumeWith(Result.failure(SecurityException(tr("Разрешите доступ к геолокации", "Allow location access"), denied)))
                    }
                    catch (error: Exception) { manager.removeUpdates(callback); if (continuation.isActive) continuation.resumeWith(Result.failure(error)) }
                }
            }
        }
        return (found ?: best.get() ?: cached)?.toTrackPoint()
            ?: error(tr("Не удалось определить место за 20 секунд. Выйдите на открытое место или включите Wi-Fi и повторите.",
                "Could not find your location in 20 seconds. Move outdoors or turn on Wi-Fi and try again."))
    }
}
