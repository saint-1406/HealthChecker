package az.healthchecker.ui
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decoded pictures by file, version and size, so scrolling lists do not decode the same JPEG again. */
object PictureCache {
    private val cache=LruCache<String,ImageBitmap>(40)
    fun load(file:File,version:Long,maxPx:Int):ImageBitmap? {
        val key="${file.path}:$version:$maxPx";cache.get(key)?.let{return it}
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
        if(bounds.outWidth<=0)return null
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/(sample*2)>=maxPx)sample*=2
        return BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})?.asImageBitmap()?.also{cache.put(key,it)}
    }
}

/** A JPEG from the app's files decoded off the main thread to about [maxSize]; null while loading or when unreadable. */
@Composable fun rememberPicture(file:File,version:Long,maxSize:Dp):ImageBitmap? {
    val px=with(LocalDensity.current){maxSize.roundToPx()}
    val image by produceState<ImageBitmap?>(null,file,version,px){value=withContext(Dispatchers.IO){PictureCache.load(file,version,px)}}
    return image
}
