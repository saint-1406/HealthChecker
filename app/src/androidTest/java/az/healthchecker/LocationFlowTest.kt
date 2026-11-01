package az.healthchecker

import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import az.healthchecker.explore.WalkTracking
import kotlinx.coroutines.*
import org.junit.*
import java.io.File

/** Uses Android's real location service with isolated test providers, never a network request. */
@SdkSuppress(minSdkVersion=31)
class LocationFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val manager get()=context.getSystemService(LocationManager::class.java)
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private var scenario:ActivityScenario<MainActivity>?=null
    private val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER,LocationManager.FUSED_PROVIDER)

    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use{it.readText()}
    @Suppress("DEPRECATION")
    @Before fun reset() {
        context.deleteDatabase("healthchecker.db")
        automation.grantRuntimePermission(context.packageName,Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(context.packageName,Manifest.permission.ACCESS_FINE_LOCATION)
        shell("appops set ${context.packageName} android:mock_location allow")
        shell("cmd location set-location-enabled false")
        providers.forEach { name ->
            manager.addTestProvider(name,false,name==LocationManager.GPS_PROVIDER,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(name,name==LocationManager.GPS_PROVIDER)
        }
        shell("cmd location set-location-enabled true")
    }
    @After fun close() {
        scenario?.close()
        providers.forEach{runCatching{manager.removeTestProvider(it)}}
        shell("appops set ${context.packageName} android:mock_location default")
    }
    private fun fix(provider:String,accuracy:Float=80f)=Location(provider).apply {
        latitude=40.4093;longitude=49.8671;this.accuracy=accuracy
        time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
    }
    private fun waitTag(tag:String)=app.waitUntil(60000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun screenshot(name:String) {
        automation.takeScreenshot()?.let { image ->
            File(context.cacheDir,name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            image.recycle()
        }
    }
    private fun launchMap() {
        scenario=ActivityScenario.launch(MainActivity::class.java)
        app.waitUntil(60000){app.onAllNodesWithText("Начать без расчёта").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithText("Начать без расчёта").performScrollTo().performClick()
        app.waitUntil(30000){app.onAllNodesWithText("Находки").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithText("Находки").performClick();waitTag("exploreTab1")
        app.onNodeWithTag("exploreTab1").performClick()
    }

    @Test fun firstGpsFixAfterTwentyFiveSecondsStillCompletes()=runBlocking {
        scenario=ActivityScenario.launch(MainActivity::class.java)
        val request=async(Dispatchers.Default){WalkTracking.currentPosition(context)}
        try {
            delay(25_000)
            Assert.assertFalse("A cold start must survive the former 20-second timeout",request.isCompleted)
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,fix(LocationManager.GPS_PROVIDER,12f))
            val result=withTimeout(10_000){request.await()}
            Assert.assertEquals(40.4093,result.point.latitude,1e-9)
            Assert.assertEquals(12.0,result.accuracyMeters,0.01)
        } finally {request.cancelAndJoin()}
    }

    @Test fun fusedSourceReturnsApproximatePositionWithoutSatelliteFix()=runBlocking {
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,false)
        manager.setTestProviderEnabled(LocationManager.FUSED_PROVIDER,true)
        scenario=ActivityScenario.launch(MainActivity::class.java)
        val request=async(Dispatchers.Default){WalkTracking.currentPosition(context)}
        try {
            delay(1500)
            manager.setTestProviderLocation(LocationManager.FUSED_PROVIDER,fix(LocationManager.FUSED_PROVIDER,120f))
            val result=withTimeout(10_000){request.await()}
            Assert.assertEquals(120.0,result.accuracyMeters,0.01)
        } finally {request.cancelAndJoin()}
    }

    @Test fun searchCanBeCancelledAndRetriedFromMap() {
        launchMap()
        app.onNodeWithText("Моё место").performClick();waitTag("locationSearch")
        screenshot("location-search-test.png")
        app.onNodeWithTag("locationCancel").performClick()
        app.waitUntil(10000){app.onAllNodesWithTag("locationSearch").fetchSemanticsNodes().isEmpty()}
        app.onNodeWithText("Моё место").assertIsEnabled().performClick();waitTag("locationSearch")
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,fix(LocationManager.GPS_PROVIDER,80f))
        waitTag("locationAccuracy")
        app.onNodeWithTag("locationAccuracy").assertTextContains("80",substring=true)
        screenshot("location-position-test.png")
        app.onNodeWithText("Моё место").assertIsEnabled()
    }
}
