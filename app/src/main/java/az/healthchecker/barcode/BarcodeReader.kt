package az.healthchecker.barcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import az.healthchecker.domain.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.UPCEReader
import com.journeyapps.barcodescanner.ScanOptions
import java.io.ByteArrayOutputStream

fun normalizeScannedCode(value:String,format:String?):String = normalizeBarcode(if(format=="UPC_E")UPCEReader.convertUPCEtoUPCA(value)else value)

/** Which camera/photo decoder a scan uses. Product barcodes return normalized GTIN-14; QR returns its raw text. */
enum class ScanKind { BARCODE,QR }

val PRODUCT_SCAN_FORMATS=listOf(ScanOptions.EAN_13,ScanOptions.EAN_8,ScanOptions.UPC_A,ScanOptions.UPC_E,ScanOptions.ITF)

/** Camera options for one scan kind; both kinds stay fully offline. */
fun scanOptions(kind:ScanKind,prompt:String=when(kind){ScanKind.BARCODE->tr("Наведите камеру на штрихкод упаковки","Point the camera at the product barcode");ScanKind.QR->tr("Наведите камеру на QR-код","Point the camera at the QR code")}):ScanOptions =
    ScanOptions().setDesiredBarcodeFormats(if(kind==ScanKind.QR)listOf(ScanOptions.QR_CODE)else PRODUCT_SCAN_FORMATS)
        .setPrompt(prompt).setBeepEnabled(false).setOrientationLocked(false).setTimeout(60000)

/** Same formats as the camera; images and codes never leave this device. */
fun decodeBarcodeImage(image:Bitmap,kind:ScanKind=ScanKind.BARCODE):String {
    val formats=if(kind==ScanKind.QR)listOf(BarcodeFormat.QR_CODE)else listOf(BarcodeFormat.EAN_8,BarcodeFormat.EAN_13,BarcodeFormat.UPC_A,BarcodeFormat.UPC_E,BarcodeFormat.ITF)
    for(rotate in listOf(false,true)) {
        val bitmap=if(rotate)Bitmap.createBitmap(image,0,0,image.width,image.height,Matrix().apply{postRotate(90f)},true)else image
        try {
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val source=RGBLuminanceSource(bitmap.width,bitmap.height,pixels)
            for(invert in listOf(false,true)) {
                try {
                    val result=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(if(invert)source.invert()else source)),mapOf(DecodeHintType.POSSIBLE_FORMATS to formats,DecodeHintType.TRY_HARDER to true))
                    return if(kind==ScanKind.QR)result.text else normalizeScannedCode(result.text,result.barcodeFormat.name)
                } catch(_:ReaderException) { /* Try the next orientation/polarity. */ }
            }
        }finally{if(rotate)bitmap.recycle()}
    }
    throw IllegalArgumentException(if(kind==ScanKind.QR)tr("QR-код не найден. Выберите чёткое фото кода.","No QR code found. Choose a clear photo of the code.")
        else tr("Штрихкод не найден. Выберите чёткое фото кода или введите цифры.","No barcode found. Choose a clear barcode photo or enter the digits."))
}

/** kind=null reads either: a product barcode first (normalized digits), otherwise a QR code's raw text. */
suspend fun readBarcodePhoto(context:Context,uri:Uri,kind:ScanKind?=ScanKind.BARCODE):String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
    context.contentResolver.openInputStream(uri)?.use{input->while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=12*1024*1024){tr("Фотография слишком большая","The photo is too large")};out.write(buffer,0,n)}}?:error(tr("Не удалось открыть фотографию","Could not open the photo"))
    val bytes=out.toByteArray();val size=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,size)
    require(size.outWidth>0&&size.outHeight>0){tr("Не удалось прочитать изображение","Could not read the image")}
    var sample=1;while(size.outWidth/sample>2048||size.outHeight/sample>2048)sample*=2
    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?:error(tr("Не удалось прочитать изображение","Could not read the image"))
    try{if(kind!=null)decodeBarcodeImage(bitmap,kind)
        else try{decodeBarcodeImage(bitmap,ScanKind.BARCODE)}catch(_:IllegalArgumentException){
            try{decodeBarcodeImage(bitmap,ScanKind.QR)}catch(_:IllegalArgumentException){throw IllegalArgumentException(tr("Штрихкод или QR-код не найден. Выберите чёткое фото кода.","No barcode or QR code found. Choose a clear photo of the code."))}
        }}finally{bitmap.recycle()}
}
