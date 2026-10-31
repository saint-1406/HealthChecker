package az.healthchecker.data
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import az.healthchecker.domain.EXERCISE_ID
import az.healthchecker.domain.tr
import java.io.ByteArrayOutputStream
import java.io.File

/** The user's pictures for exercises: one JPEG per exercise id in the app's private files. */
class ExerciseImageStore(context:Context) {
    private val dir=File(context.applicationContext.filesDir,"exercise-images")
    fun file(id:String):File { require(EXERCISE_ID.matches(id));return File(dir,"$id.jpg") }
    fun versions():Map<String,Long> = dir.listFiles{f->f.name.endsWith(".jpg")}?.associate{it.name.removeSuffix(".jpg") to it.lastModified()}.orEmpty().filterKeys{EXERCISE_ID.matches(it)}
    fun read(id:String):ByteArray? = file(id).takeIf{it.isFile}?.readBytes()
    /** Written to a temporary file first, so a failed write never leaves half a picture. */
    fun write(id:String,jpeg:ByteArray) {
        dir.mkdirs();val target=file(id);val tmp=File(dir,"$id.tmp")
        tmp.writeBytes(jpeg)
        if(!tmp.renameTo(target)){target.delete();check(tmp.renameTo(target)){tr("Не удалось сохранить картинку","Could not save the picture")}}
    }
    fun delete(id:String) { file(id).delete() }
    fun all():Map<String,ByteArray> = versions().keys.mapNotNull{id->read(id)?.let{id to it}}.toMap()
}

const val EXERCISE_IMAGE_MAX_PX=1280
/** Photos for later keep more detail, so the small print of a label stays readable when zoomed. */
const val PHOTO_MAX_PX=2048
/** A background fills a phone screen; larger pictures only cost memory. */
const val BACKGROUND_MAX_PX=1920

fun encodeExerciseImage(context:Context,uri:Uri):ByteArray = encodeImage(context,uri,EXERCISE_IMAGE_MAX_PX,85)

/** Reads a picked photo, turns it upright by its EXIF tag and stores it as JPEG at most [maxPx] on the long side. */
fun encodeImage(context:Context,uri:Uri,maxPx:Int,quality:Int):ByteArray {
    val resolver=context.contentResolver
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
    require(bounds.outWidth>0&&bounds.outHeight>0){tr("Это не картинка или файл недоступен","This is not a picture or the file is unavailable")}
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/(sample*2)>=maxPx)sample*=2
    val decoded=resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}
        ?:throw IllegalArgumentException(tr("Не удалось прочитать картинку","Could not read the picture"))
    val rotation=runCatching{resolver.openInputStream(uri)?.use{ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}}.getOrNull()
    val degrees=when(rotation){ExifInterface.ORIENTATION_ROTATE_90->90f;ExifInterface.ORIENTATION_ROTATE_180->180f;ExifInterface.ORIENTATION_ROTATE_270->270f;else->0f}
    val scale=minOf(1f,maxPx.toFloat()/maxOf(decoded.width,decoded.height))
    val matrix=Matrix().apply{postScale(scale,scale);postRotate(degrees)}
    val upright=if(scale<1f||degrees!=0f)Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true) else decoded
    // Full-size bitmaps are released right away instead of waiting for GC; a few large photos in a row could otherwise run out of memory.
    if(upright!==decoded)decoded.recycle()
    try { return ByteArrayOutputStream().use{out->upright.compress(Bitmap.CompressFormat.JPEG,quality,out);out.toByteArray()} }
    finally { upright.recycle() }
}
