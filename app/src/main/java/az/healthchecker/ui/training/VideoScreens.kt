package az.healthchecker.ui.training
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import kotlinx.coroutines.delay

/** The workout videos: the built-in ones with their exercise timelines, then the user's own links and files. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun VideoLibraryScreen(vm:AppViewModel,snapshot:UserSnapshot,back:()->Unit,open:(String)->Unit) {
    val library by vm.libraryVideos.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var adding by rememberSaveable{mutableStateOf(false)}
    var query by rememberSaveable{mutableStateOf("")}
    var tag by rememberSaveable{mutableStateOf("")}
    var length by rememberSaveable{mutableStateOf("")}
    var ownOnly by rememberSaveable{mutableStateOf(false)}
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{vm.attachVideoFile(it,null,open)}}
    val videos=remember(library,snapshot.videos){vm.videoList(snapshot)}
    val shown=remember(videos,query,tag,length,ownOnly){
        filterVideos(videos,query,tag.ifBlank{null},VideoLength.entries.find{it.name==length},ownOnly){id->vm.exercise(id)?.let{it.name+" "+(it.nameEn?:"")}.orEmpty()}
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start=8.dp,end=16.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            Text(tr("Видеотренировки","Workout videos"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("videoList"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{Text(tr("Занимайтесь по видео: приложение показывает, какое упражнение идёт и сколько осталось, и одной кнопкой записывает всю тренировку в дневник.",
                "Train along with a video: the app shows which exercise is on and how long is left, and logs the whole workout to the diary in one tap."),style=MaterialTheme.typography.bodyMedium)}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={adding=true},modifier=Modifier.testTag("addVideoLink")){Icon(Icons.Outlined.Link,null);Spacer(Modifier.width(6.dp));Text(tr("Ссылка YouTube","YouTube link"))}
                OutlinedButton(onClick={filePicker.launch(arrayOf("video/*"))}){Icon(Icons.Outlined.VideoFile,null);Spacer(Modifier.width(6.dp));Text(tr("Файл с телефона","File on the phone"))}
            }}
            item{Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                OutlinedTextField(query,{query=it.take(60)},label={Text(tr("Название или упражнение","Name or exercise"))},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("videoSearch"))
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FilterChip(selected=tag.isBlank()&&!ownOnly,onClick={tag="";ownOnly=false},label={Text(tr("Все","All"))})
                    VIDEO_TAGS.forEach{t->FilterChip(selected=tag==t,onClick={tag=if(tag==t)"" else t;ownOnly=false},label={Text(videoTagLabel(t))},modifier=Modifier.testTag("videoTag_$t"))}
                    FilterChip(selected=ownOnly,onClick={ownOnly=!ownOnly;tag=""},label={Text(tr("Мои видео","My videos"))})
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    VideoLength.entries.forEach{l->FilterChip(selected=length==l.name,onClick={length=if(length==l.name)"" else l.name},label={Text(videoLengthLabel(l))})}
                }
                Text(tr("Найдено: ","Found: ")+shown.size,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("videoCount"))
            }}
            items(shown,key={it.id}){v->VideoCard(vm,v,settings.videoFiles[v.id]){open(v.id)}}
            item{Text(tr("Видео с YouTube загружается из интернета только когда вы нажмёте «Загрузить видео». Скачивать ролики с YouTube приложение не будет: это запрещают правила YouTube. Чтобы заниматься без интернета, прикрепите свой видеофайл с телефона — он играет прямо здесь, вместе с разметкой упражнений.",
                "A YouTube video loads from the internet only when you tap “Load the video”. The app does not download videos from YouTube: YouTube's terms forbid it. To train offline, attach your own video file from the phone — it plays right here, with the exercise timeline."),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
    if(adding)AddVideoDialog(vm,{adding=false}){adding=false;open(it.id)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun VideoCard(vm:AppViewModel,v:WorkoutVideo,file:VideoFile?,open:()->Unit) {
    Panel(Modifier.clickable(onClick=open).testTag("videoCard")) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center){
                Icon(if(v.youtubeId!=null)Icons.Outlined.SmartDisplay else Icons.Outlined.VideoFile,null,tint=MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)){
                Text(v.title,style=MaterialTheme.typography.titleMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                if(v.channel.isNotBlank())Text(v.channel,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(videoSummary(vm,v),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            if(v.youtubeId!=null)AssistChip(onClick=open,label={Text(if(v.embeddable)"YouTube" else tr("YouTube · только в приложении YouTube","YouTube · in the YouTube app only"))})
            if(file!=null)AssistChip(onClick=open,label={Text(tr("Свой файл","Own file"))},leadingIcon={Icon(Icons.Outlined.OfflinePin,null,Modifier.size(18.dp))})
        }
    }
}

/** Kinds of workout the library filters by, in this order. */
private val VIDEO_TAGS=listOf("full","abs","cardio","legs","upper","dumbbells","nojump","warmup")
private fun videoTagLabel(tag:String):String = when(tag){"full"->tr("Всё тело","Full body");"abs"->tr("Пресс","Abs");"cardio"->tr("Кардио","Cardio");"legs"->tr("Ноги","Legs")
    "upper"->tr("Верх тела","Upper body");"dumbbells"->tr("Гантели","Dumbbells");"nojump"->tr("Без прыжков","No jumping");"warmup"->tr("Разминка","Warm-up");else->tag}
private fun videoLengthLabel(l:VideoLength):String = when(l){VideoLength.SHORT->tr("до 12 мин","up to 12 min");VideoLength.MEDIUM->tr("13–22 мин","13–22 min");VideoLength.LONG->tr("от 23 мин","23 min and more")}

/** "30 min · 34 exercises" for a video with a timeline, otherwise what kind of video it is. */
private fun videoSummary(vm:AppViewModel,v:WorkoutVideo):String = listOfNotNull(
    v.durationSec?.let{"${(it+30)/60} ${tr("мин","min")}"},
    if(v.segments.isNotEmpty())count(v.segments.size,"упражнение","упражнения","упражнений","exercise","exercises")+" · "+count(v.workDone().size,"вид","вида","видов","kind","kinds") else tr("Своё видео без разметки","Your own video, no timeline"),
    if(v.byChapters)tr("по главам автора","by the author's chapters") else null
).joinToString(" · ")

@Composable private fun AddVideoDialog(vm:AppViewModel,close:()->Unit,added:(WorkoutVideo)->Unit) {
    var title by rememberSaveable{mutableStateOf("")}
    var link by rememberSaveable{mutableStateOf("")}
    val busy by vm.busy.collectAsStateWithLifecycle()
    val check=runCatching{makeOwnVideo("video:check",title,link,java.time.Instant.now())}
    AlertDialog(onDismissRequest=close,title={Text(tr("Своё видео с YouTube","Your own YouTube video"))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(link,{link=it.take(300)},label={Text(tr("Ссылка на видео","Link to the video"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("videoLink"))
        OutlinedTextField(title,{title=it.take(200)},label={Text(tr("Название","Name"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("videoTitle"))
        if(link.isNotBlank()||title.isNotBlank())check.exceptionOrNull()?.let{Text(it.message?:"",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        Text(tr("Скопируйте ссылку в приложении YouTube: «Поделиться» → «Копировать ссылку». Разметки упражнений у своего видео нет: его можно записать в дневник как видеотренировку по времени.",
            "Copy the link in the YouTube app: Share → Copy link. Your own video has no exercise timeline: it is logged to the diary as a timed video workout."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }},confirmButton={TextButton(onClick={vm.addVideo(title,link,added)},enabled=check.isSuccess&&link.isNotBlank()&&!busy,modifier=Modifier.testTag("saveVideo")){Text(tr("Добавить","Add"))}},
        dismissButton={TextButton(onClick=close){Text(tr("Отмена","Cancel"))}})
}

/** Name, description and YouTube link of a video; a built-in one keeps its timeline and can go back to how it came. */
@Composable private fun EditVideoDialog(vm:AppViewModel,video:WorkoutVideo,edited:Boolean,close:()->Unit,saved:()->Unit) {
    var title by rememberSaveable{mutableStateOf(video.title)}
    var about by rememberSaveable{mutableStateOf(video.displayAbout)}
    var link by rememberSaveable{mutableStateOf(video.youtubeId?.let{youtubeWatchUrl(it)}.orEmpty())}
    val busy by vm.busy.collectAsStateWithLifecycle()
    val check=runCatching{editVideo(video,title,about,link)}
    AlertDialog(onDismissRequest=close,title={Text(tr("Видео","Video"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(title,{title=it.take(200)},label={Text(tr("Название","Name"))},modifier=Modifier.fillMaxWidth().testTag("editVideoTitle"))
        OutlinedTextField(about,{about=it.take(2000)},label={Text(tr("Описание","Description"))},minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth().testTag("editVideoAbout"))
        OutlinedTextField(link,{link=it.take(300)},label={Text(tr("Ссылка YouTube","YouTube link"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("editVideoLink"))
        check.exceptionOrNull()?.let{Text(it.message?:"",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        Text(if(video.segments.isNotEmpty())tr("Разметка упражнений остаётся прежней — меняйте ссылку на то же видео, например на копию, которую разрешено встраивать.","The exercise timeline stays the same — point the link at the same workout, for example a copy that may be embedded.")
            else tr("Пустая ссылка убирает YouTube: видео будет играть только из своего файла.","An empty link removes YouTube: the video then plays only from your own file."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(edited)TextButton(onClick={vm.resetVideoDetails(video.id,saved)},enabled=!busy,modifier=Modifier.testTag("resetVideo")){Text(tr("Вернуть исходные","Restore the original"))}
    }},confirmButton={TextButton(onClick={vm.editVideoDetails(video,title,about,link,saved)},enabled=check.isSuccess&&!busy,modifier=Modifier.testTag("saveVideoEdit")){Text(tr("Сохранить","Save"))}},
        dismissButton={TextButton(onClick=close){Text(tr("Отмена","Cancel"))}})
}

/** Lets the timeline move whichever player is showing. */
private class PlayerControl { var seek:(Int)->Unit = {} }
private enum class Source { CLOCK, FILE, YOUTUBE }

/**
 * One workout video: the player (the user's own file, YouTube on demand, or nothing while it plays elsewhere), the exercise
 * running now and the whole timeline; a tap on an exercise jumps there. Without a player a stopwatch follows the timeline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun VideoScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,id:String,back:()->Unit) {
    val library by vm.libraryVideos.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val video=remember(library,snapshot.videos,id){vm.videoList(snapshot).find{it.id==id}}
    if(video==null){TextButton(onClick=back){Text(tr("Видео недоступно. Вернуться","The video is unavailable. Go back"))};return}
    val context=LocalContext.current
    val view=LocalView.current
    DisposableEffect(Unit){view.keepScreenOn=true;onDispose{view.keepScreenOn=false}}
    val linked=settings.videoFiles[id]
    val file=remember(linked){linked?.takeIf(vm::videoFileReadable)}
    var chosen by rememberSaveable(id){mutableStateOf(if(file!=null)Source.FILE else Source.CLOCK)}
    // A file that went away (reinstall, deleted) falls back to the timer.
    val source=if(chosen==Source.FILE&&file==null)Source.CLOCK else chosen
    var youtubeError by rememberSaveable(id){mutableStateOf<Int?>(null)}
    var full by rememberSaveable{mutableStateOf(false)}
    var logging by rememberSaveable{mutableStateOf(false)}
    var editing by rememberSaveable{mutableStateOf(false)}
    var position by remember(id){mutableIntStateOf(vm.videoPositions.value[id]?:0)}
    val control=remember(id){PlayerControl()}
    val clock by vm.videoClock.collectAsStateWithLifecycle()
    val clockRunning=clock?.first==id&&clock?.second?.running==true
    LaunchedEffect(source,clock){
        if(source!=Source.CLOCK)return@LaunchedEffect
        while(true){position=(vm.videoClockMs(id)/1000).toInt();if(!clockRunning)break;delay(250)}
    }
    fun moved(second:Int){position=second;vm.videoPosition(id,second)}
    fun seek(second:Int){if(source==Source.CLOCK)vm.seekVideoClock(id,second) else control.seek(second);moved(second)}
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{vm.attachVideoFile(it,id){chosen=Source.FILE}}}
    val player:@Composable (Modifier)->Unit = {m->when(source){
        Source.FILE->FilePlayer(file!!.uri,position,control,m,::moved){chosen=Source.CLOCK;vm.error.value=tr("Файл не открывается. Прикрепите его заново.","The file does not open. Attach it again.")}
        Source.YOUTUBE->YouTubePlayer(video.youtubeId!!,position,control,m,::moved){code->youtubeError=code;chosen=Source.CLOCK}
        Source.CLOCK->PlayerPlaceholder(video,file!=null,linked!=null&&file==null,youtubeError,m,{youtubeError=null;chosen=Source.YOUTUBE},{if(!openYoutube(context,video.youtubeId!!,position))vm.notice.value=Notice(tr("На телефоне нет приложения, которое откроет YouTube","No app on the phone can open YouTube"))},{chosen=Source.FILE}){filePicker.launch(arrayOf("video/*"))}
    }}
    // Both views call the player from the same place, so switching keeps it playing instead of loading it again.
    Column(Modifier.fillMaxSize().then(if(full)Modifier.background(Color.Black) else Modifier)) {
        if(!full)Row(Modifier.padding(start=8.dp,end=8.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            Column(Modifier.weight(1f)){
                Text(video.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(video.channel.isNotBlank())Text(video.channel,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(source!=Source.CLOCK)IconButton(onClick={full=true}){Icon(Icons.Outlined.Fullscreen,tr("На весь экран","Full screen"))}
            IconButton(onClick={editing=true},modifier=Modifier.testTag("editVideo")){Icon(Icons.Outlined.Edit,tr("Изменить название, описание и ссылку","Edit the name, description and link"))}
        }
        player(if(full)Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(16f/9f))
        if(full)Surface(color=MaterialTheme.colorScheme.surfaceContainer){Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.weight(1f)){NowLine(vm,video,position)}
            IconButton(onClick={full=false}){Icon(Icons.Outlined.FullscreenExit,tr("Обычный вид","Normal view"))}
        }}
        else if(video.segments.isNotEmpty())Surface(color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            NowLine(vm,video,position)
            if(source==Source.CLOCK)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                if(clockRunning)FilledTonalIconButton(onClick=vm::pauseVideoClock,modifier=Modifier.testTag("videoClockPause")){Icon(Icons.Outlined.Pause,tr("Пауза","Pause"))}
                else FilledIconButton(onClick={vm.startVideoClock(id)},modifier=Modifier.testTag("videoClockStart")){Icon(Icons.Outlined.PlayArrow,tr("Старт по разметке","Start the timeline"))}
                Text("${formatVideoTime(position)} / ${formatVideoTime(video.durationSec?:video.segments.last().end)}",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f).testTag("videoClockTime"))
                TextButton(onClick={seek((position-10).coerceAtLeast(0))}){Text("−10 ${tr("с","s")}")}
                TextButton(onClick={seek(position+10)}){Text("+10 ${tr("с","s")}")}
            }
        }}
        if(!full)VideoTimeline(vm,snapshot,date,video,position,source,linked,file!=null,{seek(it)},{logging=true},{filePicker.launch(arrayOf("video/*"))},back)
    }
    // A new link is another video: drop the old player and its error, the placeholder offers the new one.
    if(editing)EditVideoDialog(vm,video,vm.videoEdited(id),{editing=false}){editing=false;youtubeError=null;if(chosen==Source.YOUTUBE)chosen=Source.CLOCK}
    if(logging)LogVideoDialog(vm,video,position,snapshot.bodyWeight(date),{logging=false}){upTo,minutes,vigorous->
        logging=false
        if(video.segments.isEmpty())vm.logVideoAsWorkout(minutes!!,vigorous,date) else vm.logVideo(video,upTo,date)
    }
}

/** Opens the video in the YouTube app (or a browser) at [second]; false when the phone has nothing to open it with. */
private fun openYoutube(context:Context,youtubeId:String,second:Int):Boolean =
    try{context.startActivity(Intent(Intent.ACTION_VIEW,youtubeWatchUrl(youtubeId,second).toUri()));true}catch(_:ActivityNotFoundException){false}

/** What runs at this second: the exercise and its time left, or the rest and what comes next. */
@Composable private fun NowLine(vm:AppViewModel,video:WorkoutVideo,position:Int) {
    if(video.segments.isEmpty()){Text(formatVideoTime(position),style=MaterialTheme.typography.titleMedium,color=Color.Unspecified);return}
    val m=video.moment(position)
    fun name(s:VideoSegment)=vm.exercise(s.exerciseId)?.displayName?:s.title
    // Read out as one line by TalkBack: "Now · Squat Knee to Elbow, Squat with knee to elbow, 0:23".
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically,modifier=Modifier.semantics(mergeDescendants=true){}.testTag("videoNow")){
        (m.current?:m.next)?.let{ExerciseThumb(vm,it.exerciseId,44.dp)}
        Column(Modifier.weight(1f)){
            val s=m.current
            when {
                s!=null->{Text(tr("Сейчас · ","Now · ")+s.title,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(name(s),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)}
                m.finished->Text(tr("Видео пройдено — запишите тренировку в дневник","The video is done — log the workout to the diary"),style=MaterialTheme.typography.titleSmall)
                else->{Text(if(m.index==0)tr("Скоро начало","Starting soon") else tr("Отдых","Rest"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(tr("Далее: ","Next: ")+name(m.next!!),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
        }
        if(!m.finished)Text(formatVideoTime(m.left),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
    }
}

@Composable private fun PlayerPlaceholder(video:WorkoutVideo,hasFile:Boolean,lostFile:Boolean,youtubeError:Int?,modifier:Modifier,load:()->Unit,openYoutube:()->Unit,playFile:()->Unit,attach:()->Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),contentAlignment=Alignment.Center){
        Column(Modifier.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
            val yt=video.youtubeId
            val message=when {
                youtubeError!=null->youtubeErrorText(youtubeError)
                lostFile->tr("Прикреплённый файл больше недоступен (приложение переустановлено или файл удалён). Прикрепите его заново.","The attached file is no longer available (the app was reinstalled or the file was removed). Attach it again.")
                yt!=null&&!video.embeddable->tr("Автор этого видео запретил показ в других приложениях. Откройте его в YouTube — разметка ниже будет идти по таймеру — или прикрепите свой файл.","The owner of this video does not allow it in other apps. Open it in YouTube — the timeline below follows along on a timer — or attach your own file.")
                yt!=null->tr("Видео с YouTube загрузится из интернета только по нажатию.","The YouTube video loads from the internet only when you tap.")
                else->tr("Прикрепите видеофайл с телефона.","Attach a video file from the phone.")
            }
            Text(message,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("playerMessage"))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                when {
                    hasFile->Button(onClick=playFile){Icon(Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(4.dp));Text(tr("Свой файл","Own file"))}
                    yt!=null&&video.embeddable&&youtubeError==null->Button(onClick=load,modifier=Modifier.testTag("loadYoutube")){Icon(Icons.Outlined.CloudDownload,null);Spacer(Modifier.width(4.dp));Text(tr("Загрузить видео","Load the video"))}
                    else->OutlinedButton(onClick=attach){Text(tr("Прикрепить файл","Attach a file"))}
                }
                if(yt!=null)OutlinedButton(onClick=openYoutube,modifier=Modifier.testTag("openYoutube")){Text(tr("В YouTube","In YouTube"))}
            }
            if(yt!=null&&!video.embeddable&&youtubeError==null)TextButton(onClick=load){Text(tr("Всё равно попробовать здесь","Try here anyway"),style=MaterialTheme.typography.bodySmall)}
        }
    }
}

private fun youtubeErrorText(code:Int):String = when(code) {
    101,150->tr("Автор запретил показ этого видео в других приложениях (ошибка $code). Откройте его в YouTube или прикрепите свой файл.","The owner does not allow this video in other apps (error $code). Open it in YouTube or attach your own file.")
    100->tr("Видео удалено или скрыто автором.","The video was removed or made private.")
    2->tr("YouTube не принял ссылку на видео.","YouTube did not accept the video link.")
    0->tr("YouTube не загрузился. Проверьте интернет и попробуйте снова.","YouTube did not load. Check the internet connection and try again.")
    else->tr("YouTube не смог показать видео (ошибка $code). Откройте его в YouTube.","YouTube could not play the video (error $code). Open it in YouTube.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun VideoTimeline(vm:AppViewModel,snapshot:UserSnapshot,date:String,video:WorkoutVideo,position:Int,source:Source,linked:VideoFile?,readable:Boolean,seek:(Int)->Unit,log:()->Unit,attach:()->Unit,back:()->Unit) {
    val weight=snapshot.bodyWeight(date)
    val moment=video.moment(position)
    val list=rememberLazyListState()
    // The running exercise stays in view as the video goes on.
    LaunchedEffect(moment.index,source){if(source!=Source.CLOCK||vm.videoClock.value?.second?.running==true)list.animateScrollToItem((moment.index+1).coerceAtMost(video.segments.size+1))}
    LazyColumn(state=list,modifier=Modifier.fillMaxSize().testTag("videoTimeline"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item{Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            if(video.displayAbout.isNotBlank())Text(video.displayAbout,style=MaterialTheme.typography.bodyMedium)
            if(video.segments.isNotEmpty()){
                val kcal=video.segments.sumOf{s->vm.exercise(s.exerciseId)?.let{burnedKcal(it.met,weight,s.workSeconds/60.0)}?:0.0}
                Text(listOfNotNull(count(video.segments.size,"упражнение","упражнения","упражнений","exercise","exercises"),tr("работа ","work ")+"${number(video.workSeconds/60.0,1)} ${tr("мин","min")}",if(weight!=null)"≈ ${number(kcal)} $KCAL" else null).joinToString(" · "),
                    style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("videoTotals"))
                Text(tr("Нажмите на упражнение, чтобы перейти к нему.","Tap an exercise to jump to it."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(video.byChapters)Text(tr("Разметка — по главам из описания автора: отрезок от главы до главы включает отдых рядом с упражнением, поэтому время работы оценено (≈ на 10–20 с меньше отрезка).",
                    "The timeline follows the author's chapters in the description: a chapter also holds the rest next to the exercise, so the work time is estimated (≈ 10–20 s less than the chapter)."),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("chaptersNote"))
            }
        }}
        item{Button(onClick=log,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("logVideo")){Icon(Icons.Outlined.Check,null);Spacer(Modifier.width(8.dp));Text(tr("Записать тренировку в дневник","Log the workout to the diary"))}}
        itemsIndexed(video.segments){i,s->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(s.displaySection.isNotBlank()&&(i==0||video.segments[i-1].section!=s.section))Text(s.displaySection.uppercase(Lang.locale),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp))
            val now=moment.current===s
            Surface(shape=RoundedCornerShape(16.dp),color=if(now)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().clickable{seek(s.start)}.testTag("segment")){
                Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(formatVideoTime(s.start),style=MaterialTheme.typography.labelLarge,modifier=Modifier.width(44.dp))
                    ExerciseThumb(vm,s.exerciseId,44.dp)
                    Column(Modifier.weight(1f)){
                        Text(s.title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                        Text(listOfNotNull(vm.exercise(s.exerciseId)?.displayName,s.displayNote.takeIf{it.isNotBlank()}).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text((if(s.work!=null)"≈ " else "")+"${s.workSeconds} ${tr("с","s")}",style=MaterialTheme.typography.labelLarge)
                }
            }
        }}
        item{Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=8.dp)){
            if(video.segments.isNotEmpty())Text(tr("Записывается время работы по разметке, отдых между упражнениями не считается; повторения оцениваются по темпу упражнения. Каждую запись можно изменить.",
                "The work time from the timeline is logged, rests between exercises are not counted; repetitions are estimated from the exercise tempo. Every entry can be edited."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when{linked==null&&video.segments.isEmpty()->tr("Свой видеофайл с телефона играет здесь без интернета.","Your own video file from the phone plays here offline.")
                linked==null->tr("Свой видеофайл с телефона играет здесь без интернета, вместе с разметкой.","Your own video file from the phone plays here offline, with the timeline.")
                readable->tr("Свой файл: ","Own file: ")+linked.name
                else->tr("Файл «${linked.name}» больше недоступен.","The file “${linked.name}” is no longer available.")},style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick=attach){Icon(Icons.Outlined.VideoFile,null);Spacer(Modifier.width(6.dp));Text(if(linked==null)tr("Прикрепить свой файл","Attach your own file") else tr("Заменить файл","Replace the file"))}
                if(linked!=null)TextButton(onClick={vm.removeVideoFile(video.id)}){Text(tr("Открепить файл","Detach the file"))}
                if(video.own)TextButton(onClick={vm.deleteVideo(video,back)}){Text(tr("Удалить видео","Delete the video"),color=MaterialTheme.colorScheme.error)}
            }
        }}
    }
}

@Composable private fun LogVideoDialog(vm:AppViewModel,video:WorkoutVideo,position:Int,weight:Double?,close:()->Unit,confirm:(Int?,Double?,Boolean)->Unit) {
    val end=video.segments.lastOrNull()?.end?:video.durationSec
    val partial=video.segments.isNotEmpty()&&position>0&&end!=null&&position<end&&video.workDone(position).isNotEmpty()
    var upToNow by rememberSaveable{mutableStateOf(false)}
    var minutes by rememberSaveable{mutableStateOf(if(position>=60)plain(Math.round(position/6.0)/10.0) else video.durationSec?.let{plain(Math.round(it/6.0)/10.0)}?:"")}
    var vigorous by rememberSaveable{mutableStateOf(false)}
    fun summary(upTo:Int?):String {
        val work=video.workDone(upTo);val seconds=work.sumOf{it.seconds}
        val kcal=work.sumOf{w->vm.exercise(w.exerciseId)?.let{burnedKcal(it.met,weight,w.seconds/60.0)}?:0.0}
        return listOfNotNull(count(work.size,"упражнение","упражнения","упражнений","exercise","exercises"),"${number(seconds/60.0,1)} ${tr("мин","min")}",if(weight!=null)"≈ ${number(kcal)} $KCAL" else null).joinToString(" · ")
    }
    val typed=runCatching{parseQuantity(minutes)}.getOrNull()?.takeIf{it>0&&it<=600}
    AlertDialog(onDismissRequest=close,title={Text(tr("Записать тренировку?","Log the workout?"))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(video.segments.isNotEmpty()){
            Row(Modifier.fillMaxWidth().clickable{upToNow=false},verticalAlignment=Alignment.CenterVertically){RadioButton(selected=!upToNow,onClick={upToNow=false},modifier=Modifier.testTag("logWholeVideo"));Column{Text(tr("Всё видео","The whole video"));Text(summary(null),style=MaterialTheme.typography.bodySmall)}}
            if(partial)Row(Modifier.fillMaxWidth().clickable{upToNow=true},verticalAlignment=Alignment.CenterVertically){RadioButton(selected=upToNow,onClick={upToNow=true},modifier=Modifier.testTag("logUpToNow"));Column{Text(tr("До ","Up to ")+formatVideoTime(position));Text(summary(position),style=MaterialTheme.typography.bodySmall)}}
            Text(tr("Упражнения попадут в «Выполненные» за выбранный день; повтор одного упражнения в видео станет ещё одним подходом.","The exercises go to “Completed” for the selected day; an exercise the video repeats becomes another set."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(tr("У своего видео нет разметки упражнений, поэтому оно записывается как видеотренировка по времени (Compendium 2024).","Your own video has no exercise timeline, so it is logged as a timed video workout (Compendium 2024)."),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(minutes,{minutes=it.take(6)},label={Text(tr("Сколько минут занимались","Minutes trained"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("videoMinutes"))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=!vigorous,onClick={vigorous=false},label={Text(tr("Умеренно","Moderate"))})
                FilterChip(selected=vigorous,onClick={vigorous=true},label={Text(tr("Интенсивно","Vigorous"))})
            }
        }
    }},confirmButton={TextButton(onClick={confirm(if(upToNow)position else null,typed,vigorous)},enabled=video.segments.isNotEmpty()||typed!=null,modifier=Modifier.testTag("confirmLogVideo")){Text(tr("Записать","Log"))}},
        dismissButton={TextButton(onClick=close){Text(tr("Отмена","Cancel"))}})
}

/** The user's own file, played by the system's player with simple controls; nothing leaves the phone. */
@Composable private fun FilePlayer(uri:String,start:Int,control:PlayerControl,modifier:Modifier,moved:(Int)->Unit,failed:()->Unit) {
    var player by remember{mutableStateOf<VideoView?>(null)}
    var playing by remember{mutableStateOf(false)}
    var duration by remember{mutableIntStateOf(0)}
    var at by remember{mutableIntStateOf(start)}
    val onMoved by rememberUpdatedState(moved);val onFailed by rememberUpdatedState(failed)
    Column(modifier.background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth().clickable{player?.let{if(it.isPlaying)it.pause() else it.start();playing=it.isPlaying}},contentAlignment=Alignment.Center){
            AndroidView(factory={ctx->VideoView(ctx).apply{
                setOnPreparedListener{duration=it.duration/1000;seekTo(start*1000)}
                setOnErrorListener{_,_,_->onFailed();true}
                setOnCompletionListener{playing=false}
                setVideoURI(uri.toUri())
                control.seek={s->seekTo(s*1000);at=s;onMoved(s)}
                player=this
            }},onRelease={it.stopPlayback()})
        }
        Row(Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={player?.let{if(it.isPlaying)it.pause() else it.start();playing=it.isPlaying}},modifier=Modifier.testTag("filePlay")){Icon(if(playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing)tr("Пауза","Pause") else tr("Играть","Play"),tint=Color.White)}
            Text(formatVideoTime(at),color=Color.White,style=MaterialTheme.typography.labelMedium)
            Slider(value=at.toFloat().coerceIn(0f,duration.toFloat().coerceAtLeast(1f)),onValueChange={at=it.toInt()},onValueChangeFinished={player?.seekTo(at*1000);onMoved(at)},valueRange=0f..duration.toFloat().coerceAtLeast(1f),modifier=Modifier.weight(1f).padding(horizontal=8.dp))
            Text(formatVideoTime(duration),color=Color.White,style=MaterialTheme.typography.labelMedium)
        }
    }
    LaunchedEffect(player){while(true){player?.let{if(it.isPlaying){at=it.currentPosition/1000;onMoved(at)};playing=it.isPlaying};delay(500)}}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP){player?.pause();playing=false}
}

/** The site address the YouTube player is told it is embedded in: YouTube asks apps to send their app id as the Referer. */
private const val YOUTUBE_ORIGIN="https://az.healthchecker.app"
private fun youtubePage(id:String,start:Int):String = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style></head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script><script>
var player;
function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',
 playerVars:{playsinline:1,rel:0,fs:0,autoplay:1,start:$start,origin:'$YOUTUBE_ORIGIN'},
 events:{onReady:function(){Android.ready()},onError:function(e){Android.error(e.data)}}});
 setInterval(function(){if(player&&player.getCurrentTime)Android.time(player.getCurrentTime())},500)}
function seek(s){if(player&&player.seekTo)player.seekTo(s,true)}
function pause(){if(player&&player.pauseVideo)player.pauseVideo()}
</script></body></html>"""

/**
 * The official YouTube IFrame player in a WebView, loaded only after the user asked for it. The page's base address
 * carries the app id, which YouTube requires as the Referer of embedded players (otherwise error 152/153).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable private fun YouTubePlayer(youtubeId:String,start:Int,control:PlayerControl,modifier:Modifier,moved:(Int)->Unit,failed:(Int)->Unit) {
    val onMoved by rememberUpdatedState(moved);val onFailed by rememberUpdatedState(failed)
    var ready by remember{mutableStateOf(false)}
    var web by remember{mutableStateOf<WebView?>(null)}
    Box(modifier.background(Color.Black),contentAlignment=Alignment.Center){
        AndroidView(factory={ctx->WebView(ctx).apply{
            settings.javaScriptEnabled=true
            settings.mediaPlaybackRequiresUserGesture=false
            settings.allowFileAccess=false;settings.allowContentAccess=false
            setBackgroundColor(android.graphics.Color.BLACK)
            val main=Handler(Looper.getMainLooper())
            addJavascriptInterface(object {
                @JavascriptInterface fun time(seconds:Double){main.post{onMoved(seconds.toInt())}}
                @JavascriptInterface fun ready(){main.post{ready=true}}
                @JavascriptInterface fun error(code:Int){main.post{onFailed(code)}}
            },"Android")
            webViewClient=object:WebViewClient(){
                // "Watch on YouTube" and other links leave the player for the YouTube app or the browser.
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    if(!request.isForMainFrame)return false
                    try{ctx.startActivity(Intent(Intent.ACTION_VIEW,request.url))}catch(_:ActivityNotFoundException){}
                    return true
                }
            }
            control.seek={s->evaluateJavascript("seek($s)",null)}
            loadDataWithBaseURL("$YOUTUBE_ORIGIN/",youtubePage(youtubeId,start),"text/html","utf-8",null)
            web=this
        }},onRelease={it.destroy()},modifier=Modifier.fillMaxSize())
        if(!ready)CircularProgressIndicator()
    }
    // Without the internet the player never gets ready; say so instead of spinning forever.
    LaunchedEffect(Unit){delay(25000);if(!ready)onFailed(0)}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP){web?.evaluateJavascript("pause()",null)}
}
