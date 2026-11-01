package az.healthchecker.explore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.GnssStatus
import android.os.Build
import android.os.Bundle
import android.os.Handler
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

data class LocationSearchProgress(
    val elapsedSeconds: Int = 0,
    val providers: List<String> = emptyList(),
    val visibleSatellites: Int? = null,
    val usedSatellites: Int? = null
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

    internal const val LOCATE_TIMEOUT_MS = 120_000L
    private const val CACHED_NANOS = 120_000_000_000L

    private fun ageNanos(location: Location) = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
    private fun usable(location: Location, maxAgeNanos: Long) =
        location.hasAccuracy() && location.accuracy.isFinite() && location.accuracy >= 0f &&
            location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
            location.longitude.isFinite() && location.longitude in -180.0..180.0 &&
            ageNanos(location) in 0..maxAgeNanos
    private fun Location.toTrackPoint() = TrackPoint(GeoPoint(latitude, longitude), time, accuracy.toDouble())

    /**
     * One map position, including approximate fixes. This never feeds the walk distance filter.
     * Query the platform fused provider when available, GPS and network independently. A recent fix
     * returns immediately with its real accuracy and timestamp. A cold GNSS start gets up to two minutes.
     * All registrations are removed on success, timeout or caller cancellation.
     */
    suspend fun currentPosition(context: Context, onProgress: (LocationSearchProgress) -> Unit = {}): TrackPoint {
        val app = context.applicationContext
        val fine = granted(app, Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(app, Manifest.permission.ACCESS_COARSE_LOCATION))
            throw SecurityException(tr("Разрешите доступ к геолокации", "Allow location access"))
        val manager = app.getSystemService(LocationManager::class.java)
            ?: error(tr("Геолокация недоступна", "Location is unavailable"))
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false))
                add(LocationManager.FUSED_PROVIDER)
            // Before Android 12 the GPS provider throws without precise permission; from 12 on it returns fuzzed fixes.
            if ((fine || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false))
                add(LocationManager.GPS_PROVIDER)
            if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false))
                add(LocationManager.NETWORK_PROVIDER)
        }
        check(providers.isNotEmpty()) { tr("Включите геолокацию в настройках телефона", "Turn on location in phone settings") }
        val cached = (providers + LocationManager.PASSIVE_PROVIDER).distinct().mapNotNull {
            try { manager.getLastKnownLocation(it) }
            catch (_: SecurityException) { null } // Permission can be revoked after the initial check.
            catch (_: IllegalArgumentException) { null } // A provider may disappear while locating.
        }
            .filter { usable(it, CACHED_NANOS) }.minByOrNull { it.accuracy }
        if (cached != null) return cached.toTrackPoint()
        return withContext(Dispatchers.Main.immediate) {
            val started = SystemClock.elapsedRealtime()
            val progress = AtomicReference(LocationSearchProgress(providers = providers))
            var callback: LocationListener? = null
            var gnssRegistered = false
            val gnss = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    progress.updateAndGet { it.copy(visibleSatellites = status.satelliteCount,
                        usedSatellites = (0 until status.satelliteCount).count { n -> status.usedInFix(n) }) }
                }
            }
            fun diagnostic(): String {
                val p = progress.get()
                return tr("Android не передал координаты за 120 секунд. Выйдите на открытое место и проверьте геолокацию в настройках телефона.",
                    "Android did not deliver a location within 120 seconds. Move outdoors and check phone location settings.") +
                    "\n" + tr("Источники: ", "Sources: ") + p.providers.joinToString() +
                    "\n" + tr("Спутников видно / используется: ", "Satellites visible / used: ") + "${p.visibleSatellites ?: "?"} / ${p.usedSatellites ?: "?"}" +
                    "\n${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); ${Build.DISPLAY}"
            }
            try {
                if (LocationManager.GPS_PROVIDER in providers && app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try { gnssRegistered = manager.registerGnssStatusCallback(gnss, Handler(Looper.getMainLooper())) }
                    catch (_: SecurityException) { /* Position requests below recheck revocable permissions. */ }
                    catch (_: RuntimeException) { /* Satellite diagnostics are optional on OEM providers. */ }
                }
                coroutineScope {
                    val ticker = launch {
                        while (isActive) {
                            val value = progress.updateAndGet { it.copy(elapsedSeconds = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()) }
                            onProgress(value)
                            delay(1000)
                        }
                    }
                    try {
                        val found = withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
                            suspendCancellableCoroutine<Location> { continuation ->
                                val listener = object : LocationListener {
                                    override fun onLocationChanged(location: Location) {
                                        if (usable(location, CACHED_NANOS) && continuation.isActive)
                                            continuation.resumeWith(Result.success(Location(location)))
                                    }
                                    override fun onProviderEnabled(provider: String) {}
                                    override fun onProviderDisabled(provider: String) {}
                                    @Deprecated("Required on API < 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                                }
                                callback = listener
                                val registered = mutableListOf<String>()
                                providers.forEach { provider ->
                                    if (continuation.isActive) {
                                        try {
                                            if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                                                app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                                                throw SecurityException("Location permission was revoked")
                                            manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
                                            registered += provider
                                        } catch (_: SecurityException) { /* Other providers may still be usable with coarse permission. */ }
                                        catch (_: IllegalArgumentException) { /* Do not let one missing OEM provider cancel the others. */ }
                                    }
                                }
                                progress.updateAndGet { it.copy(providers = registered) }
                                if (registered.isEmpty() && continuation.isActive) continuation.resumeWith(Result.failure(
                                    IllegalStateException(tr("Не удалось включить источники геолокации. Проверьте разрешение и настройки телефона.",
                                        "Could not start location providers. Check permission and phone settings."))))
                            }
                        }
                        found?.toTrackPoint() ?: error(diagnostic())
                    } finally { ticker.cancel() }
                }
            } finally {
                callback?.let { manager.removeUpdates(it) }
                if (gnssRegistered) manager.unregisterGnssStatusCallback(gnss)
            }
        }
    }
}
