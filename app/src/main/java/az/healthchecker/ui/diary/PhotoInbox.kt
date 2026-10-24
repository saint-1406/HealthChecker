package az.healthchecker.ui.diary
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** "13:05, 24 Oct" of the moment a photo was taken. */
fun photoTime(photo:InboxPhoto):String {
    val t=Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault())
    return shortDate(t.toLocalDate().toString())+", "+t.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** Camera and gallery for photos for later. Kept outside lazy lists, so a result coming back from the camera is never lost. */
class PhotoCapture(val shoot:(PhotoKind)->Unit,val pick:()->Unit)
@Composable fun rememberPhotoCapture(vm:AppViewModel):PhotoCapture {
    var kind by rememberSaveable{mutableStateOf(PhotoKind.FOOD.name)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)vm.keepPhoto(vm.cameraTarget(),PhotoKind.valueOf(kind))}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let{vm.keepPhoto(it,PhotoKind.FOOD)}}
    return remember(camera,gallery){PhotoCapture(
        shoot={k->kind=k.name;try{camera.launch(vm.cameraTarget())}catch(e:ActivityNotFoundException){vm.error.value=tr("На телефоне нет приложения камеры. Добавьте фото из галереи.","There is no camera app on this phone. Add a photo from the gallery.")}},
        pick={gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))})}
}

/** Diary card: take a photo of a meal, a label or a vitamin now, and log it from the photo later. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PhotoInboxCard(vm:AppViewModel,capture:PhotoCapture,open:(InboxPhoto)->Unit) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    Panel(Modifier.testTag("photoInbox")) {
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Outlined.PhotoCamera,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){
                Text(tr("Фото на потом","Photos for later"),style=MaterialTheme.typography.titleMedium)
                Text(if(photos.isEmpty())tr("Сфотографируйте еду, этикетку или витамины сейчас — внесите позже","Photograph a meal, a label or vitamins now — log them later")
                    else tr("Нажмите на фото, чтобы внести его · ","Tap a photo to log it · ")+photos.size,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(photos.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(photos,key={it.fileName}){p->InboxThumb(vm,p){open(p)}}}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilledTonalButton(onClick={capture.shoot(PhotoKind.FOOD)},modifier=Modifier.testTag("shootFood")){Icon(Icons.Outlined.PhotoCamera,null);Spacer(Modifier.width(6.dp));Text(tr("Еда","Food"))}
            FilledTonalButton(onClick={capture.shoot(PhotoKind.MEDICINE)},modifier=Modifier.testTag("shootMedicine")){Icon(Icons.Outlined.Medication,null);Spacer(Modifier.width(6.dp));Text(tr("Лекарство","Medicine"))}
            OutlinedButton(onClick=capture.pick,modifier=Modifier.testTag("photoFromGallery")){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text(tr("Из галереи","From gallery"))}
        }
    }
}

@Composable private fun InboxThumb(vm:AppViewModel,photo:InboxPhoto,open:()->Unit) {
    val food=photo.kind==PhotoKind.FOOD
    Box(Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick=open).testTag("inboxPhoto")) {
        rememberPicture(vm.photoFile(photo),photo.takenAt,88.dp)?.let{Image(it,(if(food)tr("Фото еды, ","Food photo, ") else tr("Фото лекарства, ","Medicine photo, "))+photoTime(photo),Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
        Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=0.88f),modifier=Modifier.align(Alignment.BottomStart).padding(4.dp)){
            Row(Modifier.padding(horizontal=4.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(if(food)Icons.Outlined.Restaurant else Icons.Outlined.Medication,null,Modifier.size(12.dp))
                Spacer(Modifier.width(2.dp));Text(shortDate(photo.date(ZoneId.systemDefault())),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Half the screen shows the photo (pinch or double-tap to zoom), the other half the usual form: find the food or create
 * it from the label and log it, or for a medicine mark the dose or create its card. Everything goes on the day the photo
 * was taken. When done, the photo can be deleted or kept.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PhotoEntryScreen(vm:AppViewModel,snapshot:UserSnapshot,photo:InboxPhoto,back:()->Unit,moved:(InboxPhoto)->Unit) {
    val date=photo.date(ZoneId.systemDefault())
    var mode by rememberSaveable(photo.takenAt){mutableStateOf(if(photo.kind==PhotoKind.FOOD)"search" else "med")}
    var foodId by rememberSaveable{mutableStateOf("")}
    var draft by rememberSaveable{mutableStateOf("")}
    var drafts by rememberSaveable{mutableStateOf("")}
    val openedAt=rememberSaveable{System.currentTimeMillis()}
    var large by rememberSaveable{mutableStateOf(false)}
    var finishing by remember{mutableStateOf(false)}
    val logged=snapshot.entries.count{it.id in drafts.split(',')}+snapshot.medicationIntakes.count{it.localDate==date&&Instant.parse(it.instant).toEpochMilli()>=openedAt}
    fun finish(){if(logged>0)finishing=true else back()}
    BackHandler(mode=="portion"||mode=="custom"||mode=="med-new"){mode=if(mode=="med-new")"med" else "search"}
    BackHandler(mode=="search"||mode=="med"){finish()}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start=4.dp,end=4.dp,top=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=::finish){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            Column(Modifier.weight(1f)){
                Text(tr("Фото на потом","Photo for later"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text(tr("Снято ","Taken ")+photoTime(photo)+tr(" · запись на этот день"," · logged on that day"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick={large=!large}){Icon(if(large)Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,if(large)tr("Уменьшить фото","Shrink the photo") else tr("Увеличить фото","Enlarge the photo"))}
            IconButton(onClick={vm.deletePhoto(photo,back)},modifier=Modifier.testTag("deletePhoto")){Icon(Icons.Outlined.DeleteOutline,tr("Удалить фото","Delete the photo"))}
        }
        ZoomablePhoto(vm.photoFile(photo),photo.takenAt,tr("Фото, снятое ","Photo taken ")+photoTime(photo),Modifier.fillMaxWidth().weight(if(large)0.72f else 0.42f))
        Row(Modifier.padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(selected=photo.kind==PhotoKind.FOOD,onClick={if(photo.kind!=PhotoKind.FOOD)vm.setPhotoKind(photo,PhotoKind.FOOD){moved(it);mode="search"}},label={Text(tr("Еда","Food"))},leadingIcon={Icon(Icons.Outlined.Restaurant,null)})
            FilterChip(selected=photo.kind==PhotoKind.MEDICINE,onClick={if(photo.kind!=PhotoKind.MEDICINE)vm.setPhotoKind(photo,PhotoKind.MEDICINE){moved(it);mode="med"}},label={Text(tr("Лекарство","Medicine"))},leadingIcon={Icon(Icons.Outlined.Medication,null)},modifier=Modifier.testTag("photoKindMedicine"))
            Spacer(Modifier.weight(1f))
            Button(onClick=::finish,modifier=Modifier.testTag("photoDone")){Text(tr("Готово","Done"))}
        }
        if(logged>0)Text(tr("Внесено с этого фото: ","Logged from this photo: ")+logged,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=16.dp).testTag("photoLogged"))
        Box(Modifier.fillMaxWidth().weight(if(large)0.28f else 0.58f)) {
            when(mode) {
                "portion" -> {
                    val food=vm.food(foodId)
                    if(food!=null)EntryEditor(vm,food,null,null,draft,date){mode="search"} else LaunchedEffect(Unit){mode="search"}
                }
                "custom" -> CustomFoodEditor(vm){mode="search"}
                "med" -> MedicinePicker(vm,snapshot,date){mode="med-new"}
                "med-new" -> MedicationEditor(vm,null,date){mode="med"}
                else -> FoodSearchScreen(vm,tr("Что на фото?","What is in the photo?"),::finish,{f->foodId=f.id;draft=UUID.randomUUID().toString();drafts=drafts+","+draft;mode="portion"},{mode="custom"},null,{})
            }
        }
    }
    if(finishing)AlertDialog(onDismissRequest={finishing=false},title={Text(tr("Удалить фото?","Delete the photo?"))},
        text={Text(tr("С этого фото внесено: ","Logged from this photo: ")+logged+tr(". Удалить его из «Фото на потом» или оставить?",". Delete it from “Photos for later” or keep it?"))},
        confirmButton={TextButton(onClick={finishing=false;vm.deletePhoto(photo,back)},modifier=Modifier.testTag("confirmDeletePhoto")){Text(tr("Удалить","Delete"))}},
        dismissButton={TextButton(onClick={finishing=false;back()}){Text(tr("Оставить","Keep"))}})
}

/** Medicine cards to mark a dose on the photo's day, or a new card made from the photo. */
@Composable private fun MedicinePicker(vm:AppViewModel,snapshot:UserSnapshot,date:String,new:()->Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionTitle(tr("Что на фото?","What is in the photo?"),tr("Отметьте приём за ","Mark a dose for ")+dayMonth(date)+tr(" или создайте карту препарата по фото"," or create the card from the photo"))
        Button(onClick=new,modifier=Modifier.fillMaxWidth().testTag("photoNewMedicine")){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(tr("Новый препарат по фото","New card from the photo"))}
        snapshot.medications.filter{!it.archived}.sortedBy{it.name.lowercase()}.forEach{m->
            val taken=snapshot.medicationIntakes.count{it.medicationId==m.id&&it.localDate==date}
            Panel{Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(m.name,fontWeight=FontWeight.SemiBold);Text(tr("Принято: ","Taken: ")+"$taken "+tr("из","of")+" ${m.timesPerDay}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                FilledTonalButton(onClick={vm.takeMedication(m,date)},enabled=!busy,modifier=Modifier.testTag("photoTake")){Text(tr("Принято","Taken"))}
            }}
        }
    }
}

/** A photo that zooms with two fingers (up to 6×) around the fingers, pans when zoomed and toggles 2.5× on a double tap. */
@Composable fun ZoomablePhoto(file:File,version:Long,description:String,modifier:Modifier=Modifier) {
    val image=rememberPicture(file,version,PHOTO_VIEW_DP.dp)
    var scale by remember(file){mutableFloatStateOf(1f)}
    var offset by remember(file){mutableStateOf(Offset.Zero)}
    var box by remember{mutableStateOf(IntSize.Zero)}
    fun clamp(o:Offset,s:Float)=Offset(o.x.coerceIn(-(s-1)*box.width/2,(s-1)*box.width/2),o.y.coerceIn(-(s-1)*box.height/2,(s-1)*box.height/2))
    Box(modifier.clipToBounds().background(Color.Black).onSizeChanged{box=it}
        .pointerInput(file){detectTransformGestures{centroid,pan,zoom,_->
            // Keeps the point under the fingers in place while zooming.
            val c=centroid-Offset(box.width/2f,box.height/2f);val next=(scale*zoom).coerceIn(1f,6f)
            offset=clamp(c-(c-offset)*(next/scale)+pan,next);scale=next
        }}
        .pointerInput(file){detectTapGestures(onDoubleTap={p->
            if(scale>1f){scale=1f;offset=Offset.Zero} else {val c=p-Offset(box.width/2f,box.height/2f);scale=2.5f;offset=clamp(c-c*2.5f,2.5f)}
        })}
        .semantics{contentDescription=description}.testTag("photoView"),contentAlignment=Alignment.Center) {
        if(image==null)CircularProgressIndicator(color=Color.White)
        else Image(image,null,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},contentScale=ContentScale.Fit)
        if(scale==1f&&image!=null)Text(tr("Двумя пальцами или двойным касанием — приблизить","Pinch or double-tap to zoom"),style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=0.8f),modifier=Modifier.align(Alignment.BottomCenter).padding(6.dp))
    }
}
/** Decode size for the zoomable photo; the stored photo is at most 2048 px, so this keeps it whole. */
private const val PHOTO_VIEW_DP=1400
