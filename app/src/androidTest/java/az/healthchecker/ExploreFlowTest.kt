package az.healthchecker

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import az.healthchecker.data.ExploreRepository
import az.healthchecker.data.HealthRepository
import az.healthchecker.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.util.UUID

class ExploreFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset(){context.deleteDatabase("healthchecker.db")}
    @After fun close(){scenario?.close()}
    private fun waitTag(tag:String)=app.waitUntil(90000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun launch(){scenario=ActivityScenario.launch(MainActivity::class.java);app.waitUntil(60000){app.onAllNodesWithText("Начать без расчёта").fetchSemanticsNodes().isNotEmpty()};app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();app.waitUntil(30000){app.onAllNodesWithText("Находки").fetchSemanticsNodes().isNotEmpty()};app.onNodeWithText("Находки").performClick();waitTag("findingAdd")}
    @Test fun nonFoodPhotoShopAndFavoriteSurviveRecreationWithoutDiaryEntry() {
        launch();app.onNodeWithTag("findingAdd").performClick();waitTag("findingName")
        app.onNodeWithTag("findingName").performTextInput("Baku care")
        app.onNodeWithTag("findingCategoryCARE").performClick()
        app.onNodeWithTag("findingBarcode").performScrollTo().performTextInput("4006381333931")
        val picked=File(context.cacheDir,"camera/explore-picked.jpg").apply{parentFile!!.mkdirs()}
        Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.CYAN);picked.outputStream().use{compress(Bitmap.CompressFormat.JPEG,90,it)};recycle()}
        val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.photos",picked)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val filter=android.content.IntentFilter(android.provider.MediaStore.ACTION_PICK_IMAGES).apply{addDataType("image/*")}
        val monitor=instrumentation.addMonitor(filter,android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,android.content.Intent().setData(uri)),true)
        try{app.onNodeWithTag("productPhotoGallery").performScrollTo().performClick();waitTag("productPhoto")}finally{instrumentation.removeMonitor(monitor);picked.delete()}
        app.onNodeWithTag("findingNotes").performScrollTo().performTextInput("Для следующей покупки")
        app.onNodeWithTag("findingNewPlace").performScrollTo().performClick();waitTag("placeName")
        app.onNodeWithTag("placeName").performTextInput("Тестовый магазин")
        app.onNodeWithTag("placeLat").performScrollTo().performTextInput("40.4093")
        app.onNodeWithTag("placeLon").performScrollTo().performTextInput("49.8671")
        app.onNodeWithTag("placeSave").performScrollTo().performClick()
        app.waitUntil(30000){app.onAllNodesWithTag("placeName").fetchSemanticsNodes().isEmpty()}
        scenario!!.recreate();waitTag("findingName")
        app.onNodeWithTag("findingName").assert(hasText("Baku care"))
        app.onNodeWithTag("findingSave").performScrollTo().performClick();waitTag("findingDetailName")
        app.onNodeWithTag("findingDetailName").assertTextEquals("Baku care")
        app.onNodeWithText("Тестовый магазин").performScrollTo().assertExists()
        app.onNodeWithContentDescription("Избранное").performScrollTo().performClick()
        val savedRepository=ExploreRepository(context)
        app.waitUntil(30000){runBlocking{savedRepository.exportSnapshot().findings.singleOrNull()?.favorite==true}}
        scenario!!.recreate();waitTag("findingDetailName");waitTag("findingPhoto")
        runBlocking{
            val data=ExploreRepository(context).exportSnapshot();val f=data.findings.single()
            Assert.assertEquals(FindingCategory.CARE,f.category);Assert.assertTrue(f.favorite);Assert.assertEquals(data.places.single().id,f.offers.single().placeId)
            Assert.assertTrue(ExploreRepository(context).photoFile(f)!!.length()>0)
            val core=HealthRepository(context).exportSnapshot();Assert.assertTrue(core.entries.isEmpty());Assert.assertTrue(core.customFoods.isEmpty())
        }
    }
    @Test fun bundledMapRendersAndWalkHistoryRemainsOffline() {
        val repo=ExploreRepository(context)
        runBlocking {
            repo.savePlace(SavedPlace(UUID.randomUUID().toString(),"Baku pin",GeoPoint(40.4093,49.8671)))
            val walk=repo.beginWalk("Тестовая прогулка",1_700_000_000_000)
            repo.appendPoint(walk.id,TrackPoint(GeoPoint(40.4093,49.8671),1_700_000_000_000,5.0),0)
            repo.appendPoint(walk.id,TrackPoint(GeoPoint(40.4103,49.8671),1_700_000_020_000,5.0),20000)
            repo.updateWalk(walk.id,WalkStatus.FINISHED,20000,1_700_000_020_000)
        }
        launch();app.onNodeWithTag("exploreTab2").performClick();app.onNodeWithText("Тестовая прогулка").assertExists()
        app.onNodeWithTag("walkShowMap").performClick();waitTag("offlineMapReady")
        Thread.sleep(3000)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{shot->
            File(context.cacheDir,"explore-map-test.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)}
            val locate=app.onNodeWithText("Моё место").fetchSemanticsNode().boundsInWindow
            val start=app.onNodeWithTag("walkStart").fetchSemanticsNode().boundsInWindow
            Assert.assertTrue(locate.width>100)
            Assert.assertEquals("Map must not draw over the location button",shot.getPixel((start.left+45).toInt(),start.center.y.toInt()),shot.getPixel((locate.left+45).toInt(),locate.center.y.toInt()))
            shot.recycle()
        }
        val permissions=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        Assert.assertFalse(permissions.contains(android.Manifest.permission.INTERNET))
        Assert.assertFalse(permissions.contains(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }
    @Test fun explicitWalkKeepsForegroundServiceAndExcludesPausedTime() {
        val repository=ExploreRepository(context)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_FINE_LOCATION)
        if(android.os.Build.VERSION.SDK_INT>=33)automation.grantRuntimePermission(context.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        try {
            launch();app.onNodeWithTag("exploreTab2").performClick();app.onNodeWithTag("walkStart").performClick();waitTag("walkPause")
            scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);Thread.sleep(2200)
            scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);waitTag("walkPause")
            app.onNodeWithTag("walkPause").performClick()
            app.waitUntil(30000){runBlocking{repository.exportSnapshot().walks.singleOrNull()?.status==WalkStatus.PAUSED}}
            val paused=runBlocking{repository.exportSnapshot().walks.single()}
            Assert.assertTrue(paused.durationMs>=2000)
            Thread.sleep(1800);app.onNodeWithTag("walkFinish").performClick()
            app.waitUntil(30000){runBlocking{repository.exportSnapshot().walks.single().status==WalkStatus.FINISHED}}
            val ended=runBlocking{repository.exportSnapshot().walks.single()}
            Assert.assertEquals(paused.durationMs,ended.durationMs)
        } finally {runBlocking{az.healthchecker.explore.WalkTracking.stopForRestore(context)}}
    }
}
