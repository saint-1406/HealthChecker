package az.healthchecker.ui.training
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Decoded pictures by file, version and size, so scrolling lists do not decode the same JPEG again. */
private object ExerciseImageCache {
    private val cache=LruCache<String,ImageBitmap>(32)
    fun load(file:File,version:Long,maxPx:Int):ImageBitmap? {
        val key="${file.path}:$version:$maxPx";cache.get(key)?.let{return it}
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
        if(bounds.outWidth<=0)return null
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/(sample*2)>=maxPx)sample*=2
        return BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})?.asImageBitmap()?.also{cache.put(key,it)}
    }
}

@Composable private fun rememberExerciseImage(vm:AppViewModel,id:String,maxSize:Dp):ImageBitmap? {
    val versions by vm.imageVersions.collectAsStateWithLifecycle()
    val version=versions[id]?:return null
    val px=with(LocalDensity.current){maxSize.roundToPx()}
    val image by produceState<ImageBitmap?>(null,id,version,px){value=withContext(Dispatchers.IO){ExerciseImageCache.load(vm.imageFile(id),version,px)}}
    return image
}

/** Small square picture for lists: the user's own picture, otherwise a still of the app's motion scheme. */
@Composable fun ExerciseThumb(vm:AppViewModel,id:String,size:Dp=52.dp) {
    val image=rememberExerciseImage(vm,id,size)
    if(image!=null)Image(image,null,Modifier.size(size).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
    else vm.figure(id)?.let{m->ExerciseFigure(m,vm.exercise(id)?.equipment==Equipment.DUMBBELLS,null,Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(3.dp),animate=false)}
}

/** Large picture of an exercise: the user's own picture (tap for full screen) or else the animated motion scheme. */
@Composable fun ExerciseIllustration(vm:AppViewModel,exercise:Exercise,modifier:Modifier=Modifier,height:Dp=200.dp) {
    val image=rememberExerciseImage(vm,exercise.id,360.dp)
    var full by remember{mutableStateOf(false)}
    if(image!=null){
        Image(image,tr("Картинка к упражнению ","Picture for ")+exercise.displayName,modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)).clickable{full=true},contentScale=ContentScale.Crop)
        if(full)Dialog(onDismissRequest={full=false}){Image(image,null,Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable{full=false},contentScale=ContentScale.Fit)}
    } else vm.figure(exercise.id)?.let{m->
        ExerciseFigure(m,exercise.equipment==Equipment.DUMBBELLS,tr("Схема движения: ","Movement: ")+exercise.displayName,
            modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(12.dp).testTag("exerciseFigure"))
    }
}

/** The user's own picture for an exercise: add from the gallery, view full screen, replace or remove. */
@Composable fun ExercisePicture(vm:AppViewModel,exercise:Exercise) {
    val image=rememberExerciseImage(vm,exercise.id,360.dp)
    val busy by vm.busy.collectAsStateWithLifecycle()
    var full by remember{mutableStateOf(false)}
    // The system photo picker needs no storage permission.
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let{vm.setExerciseImage(exercise.id,it)}}
    fun pick()=picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    if(image!=null) {
        Image(image,tr("Картинка к упражнению ","Picture for ")+exercise.displayName,Modifier.fillMaxWidth().heightIn(max=260.dp).clip(RoundedCornerShape(18.dp)).clickable{full=true}.testTag("exercisePicture"),contentScale=ContentScale.Fit)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick=::pick,enabled=!busy){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text(tr("Заменить","Replace"))}
            TextButton(onClick={vm.removeExerciseImage(exercise.id)},enabled=!busy){Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(6.dp));Text(tr("Убрать картинку","Remove picture"))}
        }
    } else {
        // Without the user's picture the app's own motion scheme shows the movement.
        ExerciseIllustration(vm,exercise)
        Surface(onClick=::pick,enabled=!busy,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.fillMaxWidth().testTag("addExercisePicture")) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                Icon(Icons.Outlined.AddPhotoAlternate,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
                Column{Text(tr("Добавить свою картинку","Add your own picture"),fontWeight=FontWeight.SemiBold);Text(tr("Скриншот из видео или фото — заменит схему","A video screenshot or photo — replaces the scheme"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    }
    if(full&&image!=null)Dialog(onDismissRequest={full=false}){Image(image,null,Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable{full=false},contentScale=ContentScale.Fit)}
}

/**
 * The app's stopwatch. It keeps the screen on while running, survives moving between screens, and [use] puts the
 * measured time into the exercise form.
 */
@Composable fun StopwatchPanel(vm:AppViewModel,use:((Long)->Unit)?=null,useLabel:String="") {
    val sw by vm.stopwatch.collectAsStateWithLifecycle()
    var now by remember{mutableLongStateOf(vm.stopwatchMs())}
    LaunchedEffect(sw){now=vm.stopwatchMs();while(sw.running){delay(100);now=vm.stopwatchMs()}}
    val view=LocalView.current
    DisposableEffect(sw.running){view.keepScreenOn=sw.running;onDispose{view.keepScreenOn=false}}
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(Icons.Outlined.Timer,null,tint=MaterialTheme.colorScheme.primary);Text(tr("Секундомер","Stopwatch"),style=MaterialTheme.typography.titleMedium)
        }
        Text(formatStopwatch(now),style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("stopwatchTime"))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(sw.running)FilledTonalButton(onClick=vm::pauseStopwatch,modifier=Modifier.testTag("stopwatchPause")){Icon(Icons.Outlined.Pause,null);Spacer(Modifier.width(6.dp));Text(tr("Пауза","Pause"))}
            else Button(onClick=vm::startStopwatch,modifier=Modifier.testTag("stopwatchStart")){Icon(Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(6.dp));Text(if(now>0)tr("Продолжить","Resume") else tr("Старт","Start"))}
            OutlinedButton(onClick=vm::resetStopwatch,enabled=now>0&&!sw.running){Text(tr("Сброс","Reset"))}
        }
        if(use!=null)TextButton(onClick={use(vm.stopwatchMs())},enabled=now>=1000,modifier=Modifier.testTag("stopwatchUse")){Icon(Icons.Outlined.Input,null);Spacer(Modifier.width(6.dp));Text(useLabel)}
    }
}
