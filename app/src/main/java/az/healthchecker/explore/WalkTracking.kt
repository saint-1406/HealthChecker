package az.healthchecker.explore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import az.healthchecker.data.ExploreRepository
import az.healthchecker.domain.GeoPoint
import az.healthchecker.domain.TrackPoint
import az.healthchecker.domain.WalkStatus
import az.healthchecker.domain.tr
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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

    /** Fresh GNSS only. Cancellation/timeout always unregisters the listener; no cached or network fix. */
    suspend fun currentPosition(context: Context): GeoPoint {
        requireFineLocation(context)
        val app = context.applicationContext
        val manager = app.getSystemService(LocationManager::class.java)
            ?: error(tr("GPS недоступен", "GPS is unavailable"))
        check(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { tr("Включите GPS в настройках телефона", "Enable GPS in phone settings") }
        return withTimeoutOrNull(25_000) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    // Listener identity is captured directly by a holder to allow removal inside its callback.
                    var registered: LocationListener? = null
                    val callback = LocationListener { location ->
                        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                        if (location.hasAccuracy() && location.accuracy in 0f..35f && age in 0..15_000_000_000L && continuation.isActive) {
                            registered?.let(manager::removeUpdates)
                            continuation.resumeWith(Result.success(GeoPoint(location.latitude, location.longitude)))
                        }
                    }
                    registered = callback
                    continuation.invokeOnCancellation { manager.removeUpdates(callback) }
                    try {
                        // Check again at registration: permission may have changed during the dispatch to Main.
                        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            throw SecurityException(tr("Разрешите точную геолокацию для GPS", "Allow precise location for GPS"))
                        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, callback, Looper.getMainLooper())
                        if (!continuation.isActive) manager.removeUpdates(callback)
                    }
                    catch (denied: SecurityException) {
                        manager.removeUpdates(callback)
                        if (continuation.isActive) continuation.resumeWith(Result.failure(SecurityException(tr("Разрешите точную геолокацию для GPS", "Allow precise location for GPS"), denied)))
                    }
                    catch (error: Exception) { manager.removeUpdates(callback); if (continuation.isActive) continuation.resumeWith(Result.failure(error)) }
                }
            }
        } ?: error(tr("GPS не дал точную позицию за 25 секунд. Выйдите на открытое место и повторите.", "No accurate GPS fix in 25 seconds. Move outdoors and try again."))
    }
}
