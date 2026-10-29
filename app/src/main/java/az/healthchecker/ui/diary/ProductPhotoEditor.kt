package az.healthchecker.ui.diary

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import az.healthchecker.data.encodeProductPhoto
import az.healthchecker.domain.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Base64

@Composable fun ProductPhotoPreview(data:String?,modifier:Modifier=Modifier) {
    if(data==null)return
    val bitmap by produceState<android.graphics.Bitmap?>(null,data) {
        value=withContext(Dispatchers.Default){runCatching {
            validateProductPhoto(data)
            val bytes=Base64.getDecoder().decode(data)
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>PRODUCT_PHOTO_MAX_PX)sample*=2
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})
        }.getOrNull()}
    }
    bitmap?.let{Image(it.asImageBitmap(),tr("Фото товара","Product photo"),modifier.fillMaxWidth().heightIn(max=220.dp).testTag("productPhoto"),contentScale=ContentScale.Fit)}
}

@Composable fun ProductPhotoEditor(data:String?,enabled:Boolean,onBusy:(Boolean)->Unit,change:(String?)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val cameraFile=remember{File(context.cacheDir,"camera/product.jpg")}
    fun cameraUri():android.net.Uri {
        cameraFile.parentFile?.mkdirs()
        return FileProvider.getUriForFile(context,"${context.packageName}.photos",cameraFile)
    }
    fun read(uri:android.net.Uri,fromCamera:Boolean=false) {
        loading=true;onBusy(true);error=null
        scope.launch {
            try {change(withContext(Dispatchers.IO){encodeProductPhoto(context,uri)})}
            catch(e:CancellationException){throw e}
            catch(e:Exception){error=tr("Не удалось добавить фото. Выберите другое или продолжите без фото.","Could not add the photo. Choose another or continue without one.")}
            finally {if(fromCamera)cameraFile.delete();loading=false;onBusy(false)}
        }
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)read(cameraUri(),true)else cameraFile.delete()}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let{read(it)}}
    fun shoot(){runCatching{camera.launch(cameraUri())}.onFailure{cameraFile.delete();error=tr("Камера недоступна. Можно выбрать фото из галереи.","Camera unavailable. You can choose a photo from the gallery.")}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted)shoot()else error=tr("Доступ к камере не разрешён. Можно выбрать фото из галереи.","Camera permission denied. You can choose a photo from the gallery.")
    }
    Text(tr("Фото товара (необязательно)","Product photo (optional)"),style=MaterialTheme.typography.titleSmall)
    ProductPhotoPreview(data)
    OutlinedButton(onClick={
        error=null
        if(context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)shoot()
        else permission.launch(Manifest.permission.CAMERA)
    },enabled=enabled&&!loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("productPhotoCamera")){Text(tr("Сфотографировать товар","Take a product photo"))}
    OutlinedButton(onClick={runCatching{gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}.onFailure{error=tr("Галерея недоступна","Gallery unavailable")}},enabled=enabled&&!loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("productPhotoGallery")){Text(tr("Выбрать фото товара","Choose a product photo"))}
    if(data!=null)TextButton(onClick={change(null);error=null},enabled=enabled&&!loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("productPhotoRemove")){Text(tr("Удалить фото","Remove photo"))}
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    Text(tr("Фото сохранится в вашей базе и резервной копии. Данные с этикетки введите ниже.","The photo stays in your database and backup. Enter the label values below."),style=MaterialTheme.typography.bodySmall)
}
