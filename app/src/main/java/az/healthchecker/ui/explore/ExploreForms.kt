package az.healthchecker.ui.explore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import az.healthchecker.data.ExploreRepository
import az.healthchecker.explore.WalkTracking
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.diary.ProductPhotoEditor
import java.util.UUID
import kotlin.math.roundToInt
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val offerSaver=Saver<List<FindOffer>,String>(save={Json.encodeToString(it)},restore={Json.decodeFromString(it)})

fun FindingCategory.title()=when(this){FindingCategory.FOOD->tr("Еда","Food");FindingCategory.CARE->tr("Уход","Care");FindingCategory.OTHER->tr("Другое","Other")}
fun PlaceKind.title()=when(this){PlaceKind.SHOP->tr("Магазин","Shop");PlaceKind.INTEREST->tr("Интересное место","Interesting place");PlaceKind.OTHER->tr("Другое","Other")}

@Composable fun FindingPicture(repo:ExploreRepository,f:Finding,modifier:Modifier=Modifier,detail:Boolean=false) {
    val file=remember(f.photoFile){repo.photoFile(f)}
    val picture=file?.let{rememberPicture(it,it.lastModified(),if(detail)640.dp else 180.dp)}
    Box(modifier,contentAlignment=Alignment.Center){
        if(picture!=null)Image(picture,tr("Фото находки","Finding photo"),Modifier.fillMaxSize().testTag("findingPhoto"),contentScale=if(detail)ContentScale.Fit else ContentScale.Crop)
        else Icon(Icons.Outlined.PhotoCamera,tr("Без фото","No photo"),Modifier.size(32.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** precise=true (walk recording) requires precise location; precise=false ("My location", place pins) also accepts approximate. */
@Composable fun LocationActionButton(text:String,enabled:Boolean=true,notification:Boolean=false,modifier:Modifier=Modifier,precise:Boolean=true,onDenied:(String)->Unit,action:()->Unit) {
    val context=LocalContext.current
    val currentAction by rememberUpdatedState(action)
    val currentDenied by rememberUpdatedState(onDenied)
    fun allowed()=if(precise)context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED else WalkTracking.hasAnyLocation(context)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(allowed())currentAction()
        else if(precise)currentDenied(tr("Для записи прогулки нужна точная геолокация («Точно» в окне разрешения). Отметку на карте можно поставить вручную.","Walk recording needs precise location (choose Precise in the permission prompt). You can still add a pin manually."))
        else currentDenied(tr("Нет доступа к геолокации. Разрешите его в настройках приложения или поставьте отметку вручную.","No location access. Allow it in app settings or add a pin manually."))
    }
    Button(onClick={
        val requests=mutableListOf<String>()
        if(!allowed())requests.addAll(listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        if(notification&&Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requests.add(Manifest.permission.POST_NOTIFICATIONS)
        if(requests.isEmpty())currentAction()else permission.launch(requests.toTypedArray())
    },enabled=enabled,modifier=modifier.heightIn(min=48.dp)){Text(text)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FindingEditor(vm:ExploreViewModel,initial:Finding,initialPhoto:String?,state:ExploreSnapshot,back:()->Unit,saved:(Finding)->Unit) {
    val busy by vm.busy.collectAsState()
    var name by rememberSaveable(initial.id){mutableStateOf(initial.name)}
    var brand by rememberSaveable(initial.id){mutableStateOf(initial.brand)}
    var barcode by rememberSaveable(initial.id){mutableStateOf(initial.barcode.orEmpty())}
    var category by rememberSaveable(initial.id){mutableStateOf(initial.category.name)}
    var notes by rememberSaveable(initial.id){mutableStateOf(initial.notes)}
    var photo by rememberSaveable(initial.id){mutableStateOf(initialPhoto)}
    var remove by rememberSaveable(initial.id){mutableStateOf(false)}
    var photoBusy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var offers by rememberSaveable(initial.id,stateSaver=offerSaver){mutableStateOf(initial.offers)}
    var newPlace by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
        SectionTitle(tr("Карточка находки","Finding card"),tr("Сохраняется отдельно от питания","Saved separately from nutrition"))
        OutlinedTextField(name,{name=it.take(200)},label={Text(tr("Название","Name"))},modifier=Modifier.fillMaxWidth().testTag("findingName"))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FindingCategory.entries.forEach{c->FilterChip(category==c.name,{category=c.name},label={Text(c.title())},modifier=Modifier.heightIn(min=48.dp).testTag("findingCategory${c.name}"))}}
        OutlinedTextField(brand,{brand=it.take(500)},label={Text(tr("Бренд","Brand"))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(barcode,{barcode=it.take(40)},label={Text(tr("Штрихкод (необязательно)","Barcode (optional)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("findingBarcode"))
        if(initial.photoFile!=null&&photo==null&&!remove){
            FindingPicture(vm.repository,initial,Modifier.fillMaxWidth().height(200.dp),true)
            TextButton(onClick={remove=true},modifier=Modifier.fillMaxWidth()){Text(tr("Удалить текущее фото","Remove current photo"))}
        }
        ProductPhotoEditor(photo,!busy,{photoBusy=it},{value->photo=value;if(value==null)remove=true})
        OutlinedTextField(notes,{notes=it.take(10000)},label={Text(tr("Заметка, данные с этикетки","Notes, label details"))},minLines=3,modifier=Modifier.fillMaxWidth().testTag("findingNotes"))
        SectionTitle(tr("Где купить","Where to buy"))
        if(state.places.isEmpty())Text(tr("Добавьте магазин или другое место.","Add a shop or another place."))
        state.places.forEach{p->
            val offer=offers.find{it.placeId==p.id}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(offer!=null,{checked->offers=if(checked)offers+FindOffer(p.id)else offers.filterNot{it.placeId==p.id}},modifier=Modifier.testTag("offerSelect"));Text(p.name)}
            if(offer!=null){
                OutlinedTextField(offer.price,{value->offers=offers.map{if(it.placeId==p.id)it.copy(price=value.take(500))else it}},label={Text(tr("Цена / размер упаковки (необязательно)","Price / pack size (optional)"))},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(offer.note,{value->offers=offers.map{if(it.placeId==p.id)it.copy(note=value.take(2000))else it}},label={Text(tr("Заметка об этом магазине","Note for this shop"))},modifier=Modifier.fillMaxWidth())
            }
        }
        OutlinedButton(onClick={newPlace=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("findingNewPlace")){Text(tr("Добавить место покупки","Add a purchase location"))}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={runCatching{
            require(name.isNotBlank()){tr("Введите название","Enter a name")}
            initial.copy(name=name.trim(),brand=brand.trim(),barcode=barcode.takeIf{it.isNotBlank()}?.let(::normalizeBarcode),category=FindingCategory.valueOf(category),notes=notes,
                offers=offers.filter{offer->state.places.any{it.id==offer.placeId}},updatedAt=System.currentTimeMillis())
        }.onSuccess{vm.saveFinding(it,photo,remove,saved)}.onFailure{error=it.message}},enabled=!busy&&!photoBusy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("findingSave")){Text(tr("Сохранить находку","Save finding"))}
    }
    if(newPlace)PlaceEditor(vm,null,null,{newPlace=false}){p->offers=offers.filterNot{it.placeId==p.id}+FindOffer(p.id);newPlace=false}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PlaceEditor(vm:ExploreViewModel,initial:SavedPlace?,point:GeoPoint?,close:()->Unit,saved:(SavedPlace)->Unit) {
    val id=rememberSaveable(initial?.id){initial?.id?:UUID.randomUUID().toString()}
    var name by rememberSaveable(id){mutableStateOf(initial?.name.orEmpty())}
    var lat by rememberSaveable(id){mutableStateOf((initial?.point?:point)?.latitude?.let(::plain).orEmpty())}
    var lon by rememberSaveable(id){mutableStateOf((initial?.point?:point)?.longitude?.let(::plain).orEmpty())}
    var kind by rememberSaveable(id){mutableStateOf((initial?.kind?:PlaceKind.SHOP).name)}
    var note by rememberSaveable(id){mutableStateOf(initial?.note.orEmpty())}
    var error by remember{mutableStateOf<String?>(null)}
    var accuracy by remember{mutableStateOf<Double?>(null)}
    val busy by vm.busy.collectAsState()
    Dialog(close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.95f).fillMaxHeight(.94f),shape=MaterialTheme.shapes.large){
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                TextButton(onClick=close){Text(tr("Назад","Back"))};SectionTitle(tr("Место на карте","Place on the map"))
                OutlinedTextField(name,{name=it.take(200)},label={Text(tr("Название места","Place name"))},modifier=Modifier.fillMaxWidth().testTag("placeName"))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){PlaceKind.entries.forEach{k->FilterChip(kind==k.name,{kind=k.name},label={Text(k.title())},modifier=Modifier.heightIn(min=48.dp))}}
                Text(tr("Точку можно выбрать долгим нажатием на карту или по GPS.","Choose a point by long pressing the map or using GPS."),style=MaterialTheme.typography.bodySmall)
                LocationActionButton(tr("Использовать моё место","Use my location"),!busy,precise=false,modifier=Modifier.fillMaxWidth(),onDenied={error=it}){vm.locate{lat=plain(it.point.latitude);lon=plain(it.point.longitude);accuracy=it.accuracyMeters}}
                accuracy?.let{a->Text(tr("Точность ±${a.roundToInt()} м","Accuracy ±${a.roundToInt()} m")+if(a>35)tr(" — проверьте точку на карте"," — check the point on the map")else"",style=MaterialTheme.typography.bodySmall)}
                OutlinedTextField(lat,{lat=it.take(32)},label={Text(tr("Широта","Latitude"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("placeLat"))
                OutlinedTextField(lon,{lon=it.take(32)},label={Text(tr("Долгота","Longitude"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("placeLon"))
                OutlinedTextField(note,{note=it.take(10000)},label={Text(tr("Адрес / заметка","Address / notes"))},minLines=2,modifier=Modifier.fillMaxWidth())
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                Button(onClick={runCatching{
                    require(name.isNotBlank()){tr("Введите название места","Enter the place name")}
                    val p=GeoPoint(lat.replace(',','.').toDouble(),lon.replace(',','.').toDouble());validateGeoPoint(p)
                    SavedPlace(id,name.trim(),p,PlaceKind.valueOf(kind),note,initial?.createdAt?:System.currentTimeMillis())
                }.onSuccess{vm.savePlace(it,saved)}.onFailure{error=tr("Проверьте название и координаты","Check the name and coordinates")}},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("placeSave")){Text(tr("Сохранить место","Save place"))}
            }
        }
    }
}
