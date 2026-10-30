package az.healthchecker.ui.explore

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import az.healthchecker.barcode.normalizeScannedCode
import az.healthchecker.domain.*
import az.healthchecker.explore.WalkRecordingState
import az.healthchecker.ui.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun ExploreHomeScreen(vm:ExploreViewModel=viewModel()) {
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    var page by rememberSaveable{mutableIntStateOf(0)}
    var findingId by rememberSaveable{mutableStateOf<String?>(null)}
    var draft by rememberSaveable{mutableStateOf<String?>(null)}
    var draftPhoto by rememberSaveable{mutableStateOf<String?>(null)}
    var placeId by rememberSaveable{mutableStateOf<String?>(null)}
    var placeEdit by rememberSaveable{mutableStateOf(false)}
    var pinLat by rememberSaveable{mutableStateOf<Double?>(null)}
    var pinLon by rememberSaveable{mutableStateOf<Double?>(null)}
    var focusLat by rememberSaveable{mutableStateOf<Double?>(null)}
    var focusLon by rememberSaveable{mutableStateOf<Double?>(null)}
    var selectedWalk by rememberSaveable{mutableStateOf<String?>(null)}
    var scan by rememberSaveable{mutableStateOf(false)}
    var barcode by rememberSaveable{mutableStateOf("")}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    var search by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf("")}
    var favorites by rememberSaveable{mutableStateOf(false)}
    var manualPosition by remember{mutableStateOf<TrackPoint?>(null)}
    val finding=state.findings.find{it.id==findingId}
    val place=state.places.find{it.id==placeId}
    fun newFinding(){val now=System.currentTimeMillis();draft=Json.encodeToString(Finding(UUID.randomUUID().toString(),"",createdAt=now,updatedAt=now));draftPhoto=null}
    fun lookup(value:String){vm.lookupBarcode(value){f,photo->if(scan){scan=false;if(state.findings.any{it.id==f.id})findingId=f.id else {draft=Json.encodeToString(f);draftPhoto=photo}}}}
    fun focus(p:GeoPoint){focusLat=p.latitude;focusLon=p.longitude;page=1}
    val scanner=rememberLauncherForActivityResult(ScanContract()){result->
        result.contents?.let{raw->runCatching{normalizeScannedCode(raw,result.formatName)}.onSuccess{barcode=it;lookup(it)}.onFailure{vm.error.value=it.message}}
    }
    BackHandler(draft!=null||findingId!=null||placeId!=null||scan){when{draft!=null->{draft=null;draftPhoto=null};scan->scan=false;findingId!=null->findingId=null;else->placeId=null}}
    val initial=draft?.let{Json.decodeFromString<Finding>(it)}
    if(initial!=null)FindingEditor(vm,initial,draftPhoto,state,{draft=null;draftPhoto=null}){f->draft=null;draftPhoto=null;findingId=f.id}
    else if(finding!=null)FindingDetail(vm,finding,state,{findingId=null},{draft=Json.encodeToString(finding);draftPhoto=null},{confirmDelete=true}){p->findingId=null;placeId=p.id;focus(p.point)}
    else Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(page,edgePadding=12.dp){listOf(tr("Каталог","Catalog"),tr("Карта","Map"),tr("Прогулки","Walks")).forEachIndexed{i,title->Tab(page==i,{page=i},text={Text(title)},modifier=Modifier.testTag("exploreTab$i"))}}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        when(page){
            0->{
                Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(search,{search=it.take(200)},label={Text(tr("Поиск находок","Search findings"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilterChip(favorites,{favorites=!favorites},label={Text(tr("Избранное","Favorites"))})
                        FindingCategory.entries.forEach{c->FilterChip(category==c.name,{category=if(category==c.name)""else c.name},label={Text(c.title())})}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick={newFinding()},enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("findingAdd")){Text(tr("Добавить","Add"))}
                        OutlinedButton(onClick={scan=true},enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("findingScan")){Icon(Icons.Outlined.QrCodeScanner,null);Text(tr("Сканировать","Scan"))}
                    }
                }
                val matches=remember(state.findings,search,category,favorites){state.findings.filter{(!favorites||it.favorite)&&(category.isEmpty()||it.category.name==category)&&(it.name.contains(search,true)||it.brand.contains(search,true)||it.barcode?.contains(search)==true||it.notes.contains(search,true))}.sortedByDescending{it.updatedAt}}
                if(matches.isEmpty())Column(Modifier.padding(24.dp)){Text(tr("Сохраните интересный товар, фото и место покупки.","Save an interesting product, a photo and a place to buy it."));Text(tr("Находки не добавляются в дневник питания.","Findings are saved separately from your food diary."),style=MaterialTheme.typography.bodySmall)}
                LazyVerticalGrid(GridCells.Adaptive(155.dp),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)) {
                    items(matches,key={it.id}){f->Card(onClick={findingId=f.id},modifier=Modifier.testTag("findingCard")){
                        FindingPicture(vm.repository,f,Modifier.fillMaxWidth().height(140.dp))
                        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(f.name,style=MaterialTheme.typography.titleMedium,maxLines=3);Text(f.category.title(),style=MaterialTheme.typography.bodySmall)};IconButton(onClick={vm.favorite(f)},enabled=!busy){Icon(if(f.favorite)Icons.Outlined.Star else Icons.Outlined.StarBorder,tr("Избранное","Favorite"))}}
                    }}
                }
            }
            1->{
                Row(Modifier.padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    LocationActionButton(tr("Моё место","My location"),!busy,modifier=Modifier.weight(1f),onDenied={vm.error.value=it}){vm.locate{manualPosition=TrackPoint(it,System.currentTimeMillis(),0.0);focus(it)}}
                    OutlinedButton(onClick={placeId=null;pinLat=null;pinLon=null;placeEdit=true},enabled=!busy,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("placeAdd")){Text(tr("Добавить место","Add place"))}
                }
                Text(tr("Долгое нажатие — новая отметка. Подсветка показывает только записанные прогулки.","Long press to add a pin. Shading shows recorded walks only."),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
                OfflineExploreMap(vm.repository,state,recording.latestPoint?:manualPosition,focusLat?.let{lat->focusLon?.let{GeoPoint(lat,it)}},selectedWalk,
                    {placeId=it.id},{pinLat=it.latitude;pinLon=it.longitude;placeId=null;placeEdit=true},Modifier.fillMaxWidth().weight(1f).testTag("offlineExploreMap"))
                WalkControls(vm,state,recording,busy){page=1;selectedWalk=null}
            }
            else->{
                WalkControls(vm,state,recording,busy){page=1;selectedWalk=null}
                Text(tr("История записанных прогулок","Recorded walks"),style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(16.dp))
                LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    if(state.walks.isEmpty())item{Text(tr("Нажмите «Начать прогулку». GPS включается только на время записи.","Tap Start walk. GPS is used only while recording."))}
                    items(state.walks.sortedByDescending{it.startedAt},key={it.id}){w->Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(w.name.ifBlank{walkDate(w.startedAt)},style=MaterialTheme.typography.titleMedium)
                        Text("${distance(w.distanceMeters)} · ${duration(w.durationMs)} · ${w.status.title()}")
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            TextButton(onClick={selectedWalk=w.id;w.lastPoint?.point?.let{focus(it)}?:run{page=1}},modifier=Modifier.testTag("walkShowMap")){Text(tr("На карте","Show map"))}
                            if(w.status==WalkStatus.INTERRUPTED)LocationActionButton(tr("Продолжить","Resume"),!busy,true,onDenied={vm.error.value=it}){vm.startWalk(w.id){page=1}}
                            if(w.status==WalkStatus.FINISHED||w.status==WalkStatus.INTERRUPTED)IconButton(onClick={selectedWalk=w.id;confirmDelete=true},enabled=!busy){Icon(Icons.Outlined.DeleteOutline,tr("Удалить прогулку","Delete walk"))}
                        }
                    }}}
                }
            }
        }
    }
    if(place!=null&&!placeEdit)AlertDialog(onDismissRequest={placeId=null},title={Text(place.name)},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text(place.kind.title());Text(place.note);Text("${plain(place.point.latitude)}, ${plain(place.point.longitude)}",style=MaterialTheme.typography.bodySmall)
        state.findings.filter{f->f.offers.any{it.placeId==place.id}}.forEach{f->TextButton(onClick={placeId=null;findingId=f.id}){Text(f.name)}}
        TextButton(onClick={placeEdit=true}){Text(tr("Изменить место","Edit place"))};TextButton(onClick={confirmDelete=true}){Text(tr("Удалить место","Delete place"))}
    }},confirmButton={TextButton(onClick={focus(place.point);placeId=null}){Text(tr("На карте","Show on map"))}},dismissButton={TextButton(onClick={placeId=null}){Text(tr("Закрыть","Close"))}})
    if(placeEdit)PlaceEditor(vm,place,pinLat?.let{lat->pinLon?.let{GeoPoint(lat,it)}},{placeEdit=false}){p->placeEdit=false;placeId=null;focus(p.point)}
    if(scan)AlertDialog(onDismissRequest={scan=false},title={Text(tr("Добавить по штрихкоду","Add by barcode"))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(tr("Поиск в ваших находках и встроенной базе еды. Другие товары можно описать вручную.","Search your findings and the bundled food database. Other products can be described manually."))
        OutlinedTextField(barcode,{barcode=it.take(40)},label={Text(tr("Штрихкод","Barcode"))},modifier=Modifier.testTag("findingScanCode"))
        Button(onClick={runCatching{scanner.launch(ScanOptions().setDesiredBarcodeFormats(listOf(ScanOptions.EAN_13,ScanOptions.EAN_8,ScanOptions.UPC_A,ScanOptions.UPC_E,ScanOptions.ITF)).setBeepEnabled(false).setOrientationLocked(false).setPrompt(tr("Штрихкод товара","Product barcode")))}.onFailure{vm.error.value=it.message}},enabled=!busy){Text(tr("Открыть камеру","Open camera"))}
        if(busy)LinearProgressIndicator()
    }},confirmButton={TextButton(onClick={lookup(barcode)},enabled=!busy&&barcode.isNotBlank()){Text(tr("Найти / создать","Find / create"))}},dismissButton={TextButton(onClick={scan=false}){Text(tr("Отмена","Cancel"))}})
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text(tr("Удалить?","Delete?"))},text={Text(if(place!=null)tr("Место исчезнет из карточек. Сами находки останутся.","The location will be removed from cards. Findings will remain.")else tr("Это действие нельзя отменить.","This cannot be undone."))},confirmButton={TextButton(onClick={confirmDelete=false;when{finding!=null->vm.deleteFinding(finding){findingId=null};place!=null->vm.deletePlace(place){placeId=null};else->state.walks.find{it.id==selectedWalk}?.let{vm.deleteWalk(it);selectedWalk=null}}},enabled=!busy){Text(tr("Удалить","Delete"))}},dismissButton={TextButton(onClick={confirmDelete=false}){Text(tr("Отмена","Cancel"))}})
    if(error!=null)AlertDialog(onDismissRequest={vm.error.value=null},title={Text(tr("Не удалось выполнить действие","Could not complete the action"))},text={Text(error!!)},confirmButton={TextButton(onClick={vm.error.value=null}){Text(tr("Понятно","OK"))}})
}

@Composable private fun FindingDetail(vm:ExploreViewModel,f:Finding,state:ExploreSnapshot,back:()->Unit,edit:()->Unit,delete:()->Unit,place:(SavedPlace)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row{IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))};Spacer(Modifier.weight(1f));IconButton(onClick={vm.favorite(f)}){Icon(if(f.favorite)Icons.Outlined.Star else Icons.Outlined.StarBorder,tr("Избранное","Favorite"))}}
        FindingPicture(vm.repository,f,Modifier.fillMaxWidth().height(240.dp),true)
        Text(f.name,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.testTag("findingDetailName"));Text(listOf(f.category.title(),f.brand).filter{it.isNotBlank()}.joinToString(" · "))
        f.barcode?.let{Text(it)};if(f.notes.isNotBlank())Text(f.notes)
        SectionTitle(tr("Где купить","Where to buy"))
        if(f.offers.isEmpty())Text(tr("Места покупки ещё не добавлены","No purchase locations yet"))
        f.offers.forEach{o->state.places.find{it.id==o.placeId}?.let{p->OutlinedCard(onClick={place(p)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(p.name);if(o.price.isNotBlank())Text(o.price);if(o.note.isNotBlank())Text(o.note)}}}}
        Button(onClick=edit,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("findingEdit")){Text(tr("Изменить","Edit"))}
        TextButton(onClick=delete,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(tr("Удалить находку","Delete finding"))}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun WalkControls(vm:ExploreViewModel,state:ExploreSnapshot,recording:WalkRecordingState,busy:Boolean,started:()->Unit) {
    val walk=state.walks.find{it.status==WalkStatus.ACTIVE||it.status==WalkStatus.PAUSED}
    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        if(walk==null)LocationActionButton(tr("Начать прогулку","Start walk"),!busy,true,Modifier.fillMaxWidth().testTag("walkStart"),{vm.error.value=it}){vm.startWalk(done=started)}
        else {
            Text("${distance(walk.distanceMeters)} · ${duration(if(recording.walkId==walk.id)maxOf(recording.durationMs,walk.durationMs)else walk.durationMs)} · ${walk.status.title()}",style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("walkProgress"))
            if(walk.status==WalkStatus.ACTIVE&&recording.waitingForFix)Text(tr("Ждём точный GPS. На улице сигнал лучше.","Waiting for precise GPS. Reception is better outdoors."),style=MaterialTheme.typography.bodySmall)
            recording.message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(walk.status==WalkStatus.ACTIVE)OutlinedButton(onClick={vm.pauseWalk()},enabled=!busy,modifier=Modifier.testTag("walkPause")){Text(tr("Пауза","Pause"))}
                else LocationActionButton(tr("Продолжить","Resume"),!busy,true,onDenied={vm.error.value=it}){vm.startWalk(walk.id)}
                Button(onClick={vm.finishWalk()},enabled=!busy,modifier=Modifier.testTag("walkFinish")){Text(tr("Завершить","Finish"))}
            }
        }
    }
}
private fun WalkStatus.title()=when(this){WalkStatus.ACTIVE->tr("Запись","Recording");WalkStatus.PAUSED->tr("Пауза","Paused");WalkStatus.FINISHED->tr("Завершена","Finished");WalkStatus.INTERRUPTED->tr("Прервана","Interrupted")}
private fun distance(m:Double)=String.format(java.util.Locale.ROOT,"%.2f",m/1000)+tr(" км"," km")
private fun duration(ms:Long):String {val s=ms/1000;return "%d:%02d:%02d".format(s/3600,s/60%60,s%60)}
private fun walkDate(ms:Long)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
