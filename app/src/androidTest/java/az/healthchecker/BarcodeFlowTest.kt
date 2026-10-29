package az.healthchecker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import az.healthchecker.barcode.*
import az.healthchecker.domain.*
import az.healthchecker.ui.plain
import az.healthchecker.data.HealthRepository
import az.healthchecker.data.encodeProductPhoto
import az.healthchecker.backup.BackupCodec
import kotlinx.coroutines.runBlocking
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.*

class BarcodeFlowTest {
    @get:Rule val app=createEmptyComposeRule()
    private var scenario:ActivityScenario<MainActivity>?=null
    @Before fun reset(){ApplicationProvider.getApplicationContext<Context>().deleteDatabase("healthchecker.db")}
    @After fun close(){scenario?.close()}
    private fun waitTag(tag:String)=app.waitUntil(60000){app.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun waitText(text:String)=app.waitUntil(60000){app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun launch(){scenario?.close();scenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun open(){app.onNodeWithTag("logFoodButton").performScrollTo().performClick();app.onNodeWithTag("barcodeAction").performClick();waitTag("barcodeInput")}
    @Test fun bundledProductIsFoundAndLoggedWithNoInternetPermission() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val permissions=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        Assert.assertFalse(permissions.contains(android.Manifest.permission.INTERNET))
        launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор");open()
        app.onNodeWithTag("barcodeInput").performTextInput("3017620422003")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick()
        app.waitUntil(120000){app.onAllNodesWithTag("barcodeReview").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithTag("barcodeStatus").assertTextEquals("Товар найден во встроенной базе")
        app.onNodeWithTag("barcodeOnline").assertDoesNotExist()
        val catalog=OfflineBarcodeCatalog.shared(context)
        Assert.assertTrue(catalog.manifest.products>4_000_000)
        val expected=catalog.lookup("3017620422003")!!
        Assert.assertNotNull(expected.nutrients.kcal)
        val nar=catalog.lookup("4760007300133")!!
        Assert.assertTrue(nar.name.contains("NAR"))
        Assert.assertEquals(48.4,nar.nutrients.kcal!!,0.001)
        Assert.assertEquals(0.0,catalog.lookup("4760023500012")!!.nutrients.kcal!!,0.0)
        app.onNodeWithTag("barcodeReview").performScrollTo().performClick();waitTag("barcodeName")
        app.onNodeWithTag("barcodeName").assert(hasText(expected.name))
        app.onNodeWithTag("barcodeKcal").performScrollTo().assert(hasText(plain(expected.nutrients.kcal!!)))
        app.onNodeWithTag("barcodeSaveFood").performScrollTo().performClick();waitTag("quantity")
        app.onNodeWithTag("quantity").performTextReplacement("50")
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick();waitText("Обзор")
        runBlocking {
            val entry=HealthRepository(context).exportSnapshot().entries.single()
            Assert.assertEquals(expected.nutrients.kcal!!/2,entry.nutrients.kcal!!,0.001)
        }
    }
    @Test fun manualBarcodeCardSurvivesRestartAndOpensOfflineWithoutNetwork() {
        launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор");open()
        app.onNodeWithTag("barcodeInput").performTextInput("4006381333932")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick();waitTag("barcodeMessage")
        app.onNodeWithTag("barcodeInput").performTextReplacement("4006381333931")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick();waitTag("barcodeManual")
        app.onNodeWithTag("barcodeManual").performScrollTo().performClick();waitTag("barcodeName")
        app.onNodeWithTag("productPhotoCamera").assertExists()
        app.onNodeWithTag("productPhotoGallery").assertExists()
        app.onNodeWithTag("barcodeName").performTextInput("Тестовый товар")
        val context=ApplicationProvider.getApplicationContext<Context>()
        val picked=java.io.File(context.cacheDir,"camera/picked-product.jpg").apply{parentFile!!.mkdirs()}
        val image=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN)}
        picked.outputStream().use{image.compress(Bitmap.CompressFormat.JPEG,90,it)};image.recycle()
        val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.photos",picked)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val filter=android.content.IntentFilter(android.provider.MediaStore.ACTION_PICK_IMAGES).apply{addDataType("image/*")}
        val monitor=instrumentation.addMonitor(filter,android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,android.content.Intent().setData(uri)),true)
        try {
            app.onNodeWithTag("productPhotoGallery").performScrollTo().performClick();waitTag("productPhoto")
        } finally {instrumentation.removeMonitor(monitor);picked.delete()}
        scenario!!.recreate();waitTag("barcodeName")
        app.onNodeWithTag("barcodeName").assert(hasText("Тестовый товар"))
        waitTag("productPhoto")
        app.onNodeWithTag("productPhoto").performScrollTo()
        instrumentation.uiAutomation.takeScreenshot()?.let{shot->
            java.io.File(context.cacheDir,"product-editor-test.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)};shot.recycle()
        }
        app.onNodeWithTag("barcodeKcal").performScrollTo().performTextInput("200")
        app.onNodeWithTag("barcodeSaveFood").performScrollTo().performClick();waitTag("quantity")
        app.onNodeWithTag("quantity").performTextReplacement("50")
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick();waitText("Обзор")
        launch();waitText("Обзор");app.onNodeWithText("100").assertExists();open()
        app.onNodeWithTag("barcodeInput").performTextInput("04006381333931")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick();waitTag("quantity")
        app.onNodeWithTag("quantity").assert(hasText("50"))
        app.onNodeWithText("Тестовый товар").assertExists()
        waitTag("productPhoto")
    }
    @Test fun realProductPhotoIsBoundedBackedUpAndVisibleAfterRestartAndOfflineScan() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val source=java.io.File(context.cacheDir,"product-photo-test.jpg")
        val bitmap=Bitmap.createBitmap(1600,1200,Bitmap.Config.ARGB_8888)
        val random=java.util.Random(42)
        bitmap.setPixels(IntArray(1600*1200){android.graphics.Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256))},0,1600,0,0,1600,1200)
        source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)};bitmap.recycle()
        val encoded=encodeProductPhoto(context,android.net.Uri.fromFile(source));source.delete()
        val bytes=java.util.Base64.getDecoder().decode(encoded)
        Assert.assertTrue(bytes.size<=PRODUCT_PHOTO_MAX_BYTES)
        val photo=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        Assert.assertTrue(maxOf(photo.width,photo.height)<=PRODUCT_PHOTO_MAX_PX);photo.recycle()
        val food=Food("custom:barcode:04006381333931","Товар с фото",per100g=Nutrients(200.0,10.0),barcode=normalizeBarcode("4006381333931"),productPhoto=encoded)
        runBlocking {
            val repository=HealthRepository(context)
            repository.saveFood(food)
            Assert.assertEquals(food,BackupCodec.decode(BackupCodec.encode(repository.exportSnapshot())).customFoods.single())
        }
        launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор");open()
        app.onNodeWithTag("barcodeInput").performTextInput("4006381333931")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick();waitTag("quantity");waitTag("productPhoto")
        app.onNodeWithText("Товар с фото").assertExists()
        scenario!!.recreate();waitTag("productPhoto")
        launch();waitText("Обзор");open()
        app.onNodeWithTag("barcodeInput").performTextInput("04006381333931")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick();waitTag("productPhoto")
        app.onNodeWithText("Товар с фото").assertExists()
    }
    @Test fun decoderReadsRealEanPixelsAndRotationAndExpandsUpce() {
        val bits=MultiFormatWriter().encode("4006381333931",BarcodeFormat.EAN_13,800,350)
        val image=Bitmap.createBitmap(800,350,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(800*350){i->if(bits[i%800,i/800])android.graphics.Color.BLACK else android.graphics.Color.WHITE}
        image.setPixels(pixels,0,800,0,0,800,350)
        Assert.assertEquals(normalizeBarcode("4006381333931"),decodeBarcodeImage(image))
        val rotated=Bitmap.createBitmap(image,0,0,image.width,image.height,Matrix().apply{postRotate(90f)},true)
        Assert.assertEquals(normalizeBarcode("4006381333931"),decodeBarcodeImage(rotated))
        Assert.assertEquals(normalizeBarcode("042100005264"),normalizeScannedCode("04252614","UPC_E"))
    }
    @Test fun photosForLaterRequestCameraPermissionBeforeLaunchingTheCamera() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        Assume.assumeTrue(context.checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
        // Repeated denials on a reused AVD suppress the dialog; start this prompt test from an askable state.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm clear-permission-flags ${context.packageName} android.permission.CAMERA user-set user-fixed"
        )).use{it.readBytes()}
        launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор")
        app.onNodeWithText("Дневник").performClick()
        app.onNodeWithTag("photoMenu").performScrollTo().performClick()
        app.onNodeWithTag("shootFood").performClick()
        fun denyNode(node:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.viewIdResourceName?.endsWith("/permission_deny_button")==true)return node
            for(i in 0 until node.childCount)denyNode(node.getChild(i))?.let{return it}
            return null
        }
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo=automation.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS}
        app.waitUntil(10000){denyNode(automation.rootInActiveWindow)!=null}
        Assert.assertTrue(denyNode(automation.rootInActiveWindow)!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        waitText("Для фотографии нужен доступ к камере. Можно добавить фото из галереи.")
    }
}
