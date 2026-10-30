package az.healthchecker.explore

import android.Manifest
import android.app.Application
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CurrentPositionTest {
    private fun gpsContext(): Application {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app.getSystemService(LocationManager::class.java)).apply {
            setLocationEnabled(true)
            setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        }
        return app
    }

    @Test fun noGpsFixReportsAnActionableErrorAfterTwentyFiveSeconds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val app = gpsContext()
            var failure: Throwable? = null
            launch { failure = runCatching { WalkTracking.currentPosition(app) }.exceptionOrNull() }
            advanceUntilIdle()
            assertEquals(25_000L, currentTime)
            assertTrue("Timeout must reach the UI error handler, not be treated as caller cancellation: $failure", failure is IllegalStateException && failure !is CancellationException)
            assertTrue(failure?.message.orEmpty().contains("25"))
            assertTrue(shadowOf(app.getSystemService(LocationManager::class.java)).getLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun callerCancellationRemainsCancellation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val app = gpsContext()
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
            assertTrue(shadowOf(app.getSystemService(LocationManager::class.java)).getLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
