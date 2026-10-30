package az.healthchecker.explore

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import az.healthchecker.MainActivity
import az.healthchecker.R
import az.healthchecker.data.ExploreRepository
import az.healthchecker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Explicit, user-started GNSS session. Never restarted by Android or a boot receiver. */
class WalkTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val termination = CompletableDeferred<Unit>()
    private lateinit var repository: ExploreRepository
    private lateinit var locations: LocationManager
    private var walk: Walk? = null
    private var filter = GpsTrackFilter()
    private val clock = RecordingClock()
    @Volatile private var active = false
    @Volatile private var destroyed = false
    private var ticker: Job? = null
    private var foregroundError: Throwable? = null
    private var requestedStop = false
    private var shutdownRequest = 0L
    private var shutdownError: Throwable? = null
    private var currentRequest = 0L
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { commands.trySend(Command(fix = location)) }
        override fun onProviderDisabled(provider: String) { commands.trySend(Command(action = LOST)) }
    }
    private data class Command(val action: String = "", val request: Long = 0, val walkId: String? = null, val fix: Location? = null)

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        repository = ExploreRepository(applicationContext)
        locations = getSystemService(LocationManager::class.java)
        WalkTracking.drained = termination
        WalkTracking.running = true
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION, notification())
        } catch (error: Exception) { foregroundError = error }
        scope.launch {
          try {
            for (command in commands) {
                currentRequest = command.request
                var failed = false
                try {
                    foregroundError?.let { throw it }
                    when {
                        command.fix != null -> record(command.fix)
                        command.action == START -> {
                            // A cancelled caller must not accidentally start a new recording later.
                            if (command.request != 0L && !WalkTracking.isPending(command.request)) continue
                            startSession(command.walkId)
                        }
                        command.action == LOST -> {
                            filter.breakSegment()
                            WalkTracking.publish(WalkTracking.state.value.copy(waitingForFix = true, message = tr("Ожидаем GPS", "Waiting for GPS")))
                        }
                        command.action == PAUSE -> checkpoint(WalkStatus.PAUSED)
                        command.action == FINISH -> checkpoint(WalkStatus.FINISHED)
                        command.action == INTERRUPT -> checkpoint(WalkStatus.INTERRUPTED)
                    }
                    if (command.action != FINISH && command.action != INTERRUPT) WalkTracking.complete(command.request, walk?.id)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    failed = true
                    runCatching { checkpoint(WalkStatus.INTERRUPTED) }
                    WalkTracking.publish(WalkTracking.state.value.copy(message = error.message, waitingForFix = false))
                    shutdownError = error
                }
                if ((!active && command.action in listOf(FINISH, INTERRUPT)) || (!active && walk == null && command.action == PAUSE) || foregroundError != null || failed) {
                    requestedStop = true
                    shutdownRequest = command.request
                    withContext(Dispatchers.Main.immediate) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                    if (destroyed) {
                        WalkTracking.complete(shutdownRequest, walk?.id, shutdownError)
                        break
                    }
                }
                if (destroyed && command.action == INTERRUPT) { commands.close(); break }
            }
          } finally {
              WalkTracking.complete(currentRequest, null, IllegalStateException(tr("Запись остановлена", "Recording stopped")))
              while (true) {
                  val pending = commands.tryReceive().getOrNull() ?: break
                  WalkTracking.complete(pending.request, null, IllegalStateException(tr("Запись остановлена", "Recording stopped")))
              }
              commands.close()
              termination.complete(Unit)
              scope.cancel()
          }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (requestedStop || destroyed) {
            WalkTracking.complete(intent?.getLongExtra("request", 0) ?: 0, null, IllegalStateException(tr("Запись останавливается; повторите действие", "Recording is stopping; try again")))
            return START_NOT_STICKY
        }
        if (intent?.action in listOf(START, PAUSE, FINISH, INTERRUPT)) {
            commands.trySend(Command(intent!!.action!!, intent.getLongExtra("request", 0), intent.getStringExtra("walkId")))
        } else stopSelf()
        return START_NOT_STICKY
    }

    private suspend fun startSession(id: String?) {
        WalkTracking.requireFineLocation(this)
        check(locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) { tr("Включите GPS в настройках телефона", "Enable GPS in phone settings") }
        val existing = walk
        if (active && existing != null) return
        val selected = (if (id == null) repository.beginWalk() else repository.exportSnapshot().walks.firstOrNull { it.id == id })
            ?: error(tr("Прогулка не найдена", "Walk not found"))
        val resumed = when (selected.status) {
            WalkStatus.PAUSED, WalkStatus.INTERRUPTED -> repository.resumeWalk(selected.id)
            WalkStatus.ACTIVE -> selected
            WalkStatus.FINISHED -> error(tr("Завершённую прогулку нельзя продолжить", "A finished walk cannot be resumed"))
        }
        walk = resumed
        filter = GpsTrackFilter()
        clock.start(resumed.durationMs, SystemClock.elapsedRealtime())
        active = true
        WalkTracking.publish(WalkRecordingState(resumed.id, resumed.lastPoint, resumed.durationMs, true))
        withContext(Dispatchers.Main.immediate) {
            check(!destroyed)
            // Recheck at the actual registration boundary, including revocation while switching dispatchers.
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                throw SecurityException(tr("Разрешите точную геолокацию для GPS", "Allow precise location for GPS"))
            try {
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener, Looper.getMainLooper())
            } catch (denied: SecurityException) {
                throw SecurityException(tr("Разрешите точную геолокацию для GPS", "Allow precise location for GPS"), denied)
            }
        }
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && active) {
                delay(1_000)
                if (active) WalkTracking.publish(WalkTracking.state.value.copy(durationMs = clock.duration(SystemClock.elapsedRealtime())))
            }
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private suspend fun record(location: Location) {
        if (!active) return
        val currentWalk = walk ?: return
        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        if (age !in 0..15_000_000_000L || !location.hasAccuracy()) { filter.breakSegment(); return }
        val accepted = filter.accept(GpsFix(location.latitude, location.longitude, location.time, location.elapsedRealtimeNanos, location.accuracy.toDouble()))
        if (accepted == null) {
            if (location.accuracy > 35f) WalkTracking.publish(WalkTracking.state.value.copy(waitingForFix = true, message = tr("GPS пока неточный", "Waiting for an accurate GPS fix")))
            return
        }
        val point = TrackPoint(GeoPoint(location.latitude, location.longitude), location.time, location.accuracy.toDouble(), accepted.breakBefore)
        val saved = repository.appendPoint(currentWalk.id, point, clock.duration(SystemClock.elapsedRealtime()))
        if (saved.pointCount <= currentWalk.pointCount) { filter.breakSegment(); return }
        walk = saved
        WalkTracking.publish(WalkRecordingState(saved.id, saved.lastPoint, saved.durationMs, false))
    }

    private suspend fun checkpoint(status: WalkStatus) {
        active = false
        ticker?.cancel(); ticker = null
        withContext(Dispatchers.Main.immediate) { locations.removeUpdates(listener) }
        filter.breakSegment()
        val previous = walk
        if (previous != null && (previous.status == WalkStatus.ACTIVE || status == WalkStatus.FINISHED)) {
            val duration = if (previous.status == WalkStatus.ACTIVE) clock.pause(SystemClock.elapsedRealtime()) else previous.durationMs
            walk = repository.updateWalk(previous.id, status, duration)
        }
        WalkTracking.publish(WalkRecordingState())
        if (status == WalkStatus.PAUSED) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    override fun onDestroy() {
        destroyed = true
        active = false
        ticker?.cancel()
        locations.removeUpdates(listener)
        WalkTracking.running = false
        // Drain accepted fixes already queued, then checkpoint if Android stops this live process.
        // A killed process is reconciled to INTERRUPTED on the next explicit UI visit.
        if (requestedStop) {
            WalkTracking.complete(shutdownRequest, walk?.id, shutdownError)
            while (true) {
                val pending = commands.tryReceive().getOrNull() ?: break
                WalkTracking.complete(pending.request, null, IllegalStateException(tr("Запись остановлена", "Recording stopped")))
            }
            commands.close()
            scope.cancel()
        } else commands.trySend(Command(action = INTERRUPT))
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Прогулки", "Walks"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String, code: Int) = PendingIntent.getService(this, code, Intent(this, WalkTrackingService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_steps).setOngoing(true).setShowWhen(false).setContentIntent(open)
            .setContentTitle(if (walk?.status == WalkStatus.PAUSED) tr("Прогулка на паузе", "Walk paused") else tr("Записываем прогулку", "Recording walk"))
            .setContentText(if (walk?.status == WalkStatus.PAUSED) tr("Продолжить можно в приложении", "Resume in the app") else tr("GPS работает и при выключенном экране", "GPS stays on with the screen off"))
            .addAction(Notification.Action.Builder(null, tr("Пауза", "Pause"), action(PAUSE, 21)).build())
            .addAction(Notification.Action.Builder(null, tr("Завершить", "Finish"), action(FINISH, 22)).build()).build()
    }

    companion object {
        internal const val START = "az.healthchecker.walk.START"
        internal const val PAUSE = "az.healthchecker.walk.PAUSE"
        internal const val FINISH = "az.healthchecker.walk.FINISH"
        internal const val INTERRUPT = "az.healthchecker.walk.INTERRUPT"
        private const val LOST = "az.healthchecker.walk.LOST"
        private const val CHANNEL = "walk-recording"
        private const val NOTIFICATION = 8
    }
}
