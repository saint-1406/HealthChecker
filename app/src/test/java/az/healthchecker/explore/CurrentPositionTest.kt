package az.healthchecker.explore

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.domain.TrackPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CurrentPositionTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = app.getSystemService(LocationManager::class.java)

    @Before fun reset() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).apply {
            setLocationEnabled(true)
            setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        }
    }
    @After fun resetMain() = Dispatchers.resetMain()

    private fun precise(gps: Boolean = true, network: Boolean = false) {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, gps)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, network)
    }

    private fun fix(provider: String, accuracy: Float, lat: Double = 40.41, ageMs: Long = 0) = Location(provider).apply {
        latitude = lat; longitude = 49.87; this.accuracy = accuracy; time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageMs * 1_000_000
    }

    private fun deliver(location: Location) {
        shadowOf(manager).simulateLocation(location)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun noRequestsLeft() {
        assertTrue(shadowOf(manager).getLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
        assertTrue(shadowOf(manager).getLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
    }

    @Test fun noFixReportsAnActionableErrorAfterTwentySeconds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        precise()
        var failure: Throwable? = null
        launch { failure = runCatching { WalkTracking.currentPosition(app) }.exceptionOrNull() }
        advanceUntilIdle()
        assertEquals(WalkTracking.LOCATE_TIMEOUT_MS, currentTime)
        assertTrue("Timeout must reach the UI error handler, not be treated as caller cancellation: $failure", failure is IllegalStateException && failure !is CancellationException)
        assertTrue(failure?.message.orEmpty().contains("20"))
        noRequestsLeft()
    }

    @Test fun indoorFixIsReturnedWithItsAccuracyInsteadOfAnError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        precise(gps = true, network = true)
        var result: TrackPoint? = null
        launch { result = WalkTracking.currentPosition(app) }
        runCurrent()
        deliver(fix(LocationManager.NETWORK_PROVIDER, 80f))
        deliver(fix(LocationManager.NETWORK_PROVIDER, 120f, lat = 40.5))
        runCurrent()
        assertNull("An inaccurate fix waits for a better GPS fix", result)
        advanceUntilIdle()
        assertEquals(80.0, result!!.accuracyMeters, 0.01)
        assertEquals(40.41, result!!.point.latitude, 1e-9)
        noRequestsLeft()
    }

    @Test fun accurateGpsFixReturnsImmediately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        precise()
        var result: TrackPoint? = null
        launch { result = WalkTracking.currentPosition(app) }
        runCurrent()
        deliver(fix(LocationManager.GPS_PROVIDER, 12f))
        runCurrent()
        assertEquals(12.0, result!!.accuracyMeters, 0.01)
        assertTrue(currentTime < 1_000)
        noRequestsLeft()
    }

    @Test fun approximatePermissionUsesTheFirstNetworkFix() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        var result: TrackPoint? = null
        launch { result = WalkTracking.currentPosition(app) }
        runCurrent()
        deliver(fix(LocationManager.NETWORK_PROVIDER, 900f))
        runCurrent()
        assertEquals(900.0, result!!.accuracyMeters, 0.01)
        noRequestsLeft()
    }

    @Test fun recentAccurateCachedFixNeedsNoWaiting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        precise()
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix(LocationManager.GPS_PROVIDER, 20f, ageMs = 30_000))
        val result = WalkTracking.currentPosition(app)
        assertEquals(20.0, result.accuracyMeters, 0.01)
        assertEquals(0L, currentTime)
        noRequestsLeft()
    }

    @Test fun staleCachedFixIsIgnored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        precise()
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix(LocationManager.GPS_PROVIDER, 20f, ageMs = 10 * 60_000))
        val failure = async { runCatching { WalkTracking.currentPosition(app) }.exceptionOrNull() }
        advanceUntilIdle()
        assertTrue(failure.await() is IllegalStateException)
    }

    @Test fun noPermissionIsASecurityError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        assertTrue(runCatching { WalkTracking.currentPosition(app) }.exceptionOrNull() is SecurityException)
    }

    @Test fun disabledLocationAsksToTurnItOn() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        precise(gps = false, network = false)
        assertTrue(runCatching { WalkTracking.currentPosition(app) }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun callerCancellationRemainsCancellation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        precise(gps = true, network = true)
        var failure: Throwable? = null
        val request = launch {
            try { WalkTracking.currentPosition(app) }
            catch (error: Throwable) { failure = error; throw error }
        }
        runCurrent()
        request.cancel()
        request.join()
        assertTrue(failure is CancellationException)
        assertTrue(request.isCancelled)
        noRequestsLeft()
    }
}
