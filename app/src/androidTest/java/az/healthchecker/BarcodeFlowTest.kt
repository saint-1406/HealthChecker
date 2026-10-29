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
    @Test fun manualBarcodeCardSurvivesRestartAndOpensOfflineWithoutNetwork() {
        launch();waitText("Начать без расчёта");app.onNodeWithText("Начать без расчёта").performScrollTo().performClick();waitText("Обзор");open()
        app.onNodeWithTag("barcodeInput").performTextInput("4006381333932")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick();waitTag("barcodeMessage")
        app.onNodeWithTag("barcodeInput").performTextReplacement("4006381333931")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick();waitTag("barcodeManual")
        app.onNodeWithTag("barcodeManual").performScrollTo().performClick();waitTag("barcodeName")
        app.onNodeWithTag("barcodeName").performTextInput("Тестовый товар")
        app.onNodeWithTag("barcodeKcal").performScrollTo().performTextInput("200")
        app.onNodeWithTag("barcodeSaveFood").performScrollTo().performClick();waitTag("quantity")
        app.onNodeWithTag("quantity").performTextReplacement("50")
        app.onNodeWithText("Добавить в дневник").performScrollTo().performClick();waitText("Обзор")
        launch();waitText("Обзор");app.onNodeWithText("100").assertExists();open()
        app.onNodeWithTag("barcodeInput").performTextInput("04006381333931")
        app.onNodeWithTag("barcodeFind").performScrollTo().performClick();waitTag("quantity")
        app.onNodeWithTag("quantity").assert(hasText("50"))
        app.onNodeWithText("Тестовый товар").assertExists()
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
