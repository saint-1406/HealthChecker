package az.healthchecker.ui.dashboard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.LocalDate

private fun kg(v:Double?,decimals:Int=1)="${number(v,decimals)} ${tr("кг","kg")}"
/** "−0,6 кг" / "+0,4 kg" with the sign always shown. */
private fun change(v:Double)=(if(v>0)"+" else if(v<0)"−" else "±")+kg(kotlin.math.abs(v))
private fun trendText(t:Trend?)=when(t){Trend.DOWN->tr("снижается","going down");Trend.UP->tr("растёт","going up");Trend.STABLE->tr("стабилен","stable");null->""}

/** Overview card: last morning weigh-in, direction over a week, a 90-day line; tap opens the history. */
@Composable fun WeightCard(vm:AppViewModel,snapshot:UserSnapshot,date:String) {
    val stats=weightStats(snapshot.weights,date,snapshot.profile?.initialWeight)
    var record by rememberSaveable{mutableStateOf(false)}
    var history by rememberSaveable{mutableStateOf(false)}
    Panel(Modifier.clickable{history=true}.testTag("weightCard")) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Icon(Icons.Outlined.MonitorWeight,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){
                Text(tr("Вес","Weight"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                val latest=stats.latest
                if(latest==null)Text(tr("Взвешивайтесь утром и записывайте вес — здесь появится, снижается он или растёт","Weigh yourself in the morning and log it — the trend will appear here"),style=MaterialTheme.typography.bodyMedium)
                else {
                    Text(kg(latest.kg),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("weightLatest"))
                    Text(if(latest.date==date)tr("сегодня","today") else dayMonth(latest.date),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledTonalButton(onClick={record=true},modifier=Modifier.testTag("recordWeight")){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(4.dp));Text(tr("Записать","Log"))}
        }
        stats.weekChange?.let{w->Text("${tr("За неделю","This week")}: ${change(w)} · ${trendText(stats.trend)}",style=MaterialTheme.typography.bodyMedium,color=when(stats.trend){Trend.DOWN->MaterialTheme.colorScheme.primary;Trend.UP->MaterialTheme.colorScheme.tertiary;else->MaterialTheme.colorScheme.onSurface},modifier=Modifier.testTag("weightTrend"))}
        val more=listOfNotNull(stats.monthChange?.let{tr("за месяц ","month ")+change(it)},stats.sinceStart?.takeIf{snapshot.weights.size>1||snapshot.profile!=null}?.let{tr("с начала ","since start ")+change(it)})
        if(more.isNotEmpty())Text(more.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(stats.series.size>=2)WeightLine(stats.series,Modifier.fillMaxWidth().height(56.dp))
    }
    if(record)WeightDialog(vm,date,stats.latest?.kg?:snapshot.profile?.currentWeight){record=false}
    if(history)WeightHistory(vm,snapshot,date,stats,{history=false}){history=false;record=true}
}

@Composable private fun WeightLine(points:List<WeightEntry>,modifier:Modifier) {
    val color=MaterialTheme.colorScheme.primary
    val first=LocalDate.parse(points.first().date).toEpochDay();val span=(LocalDate.parse(points.last().date).toEpochDay()-first).coerceAtLeast(1)
    val low=points.minOf{it.kg};val high=points.maxOf{it.kg};val range=(high-low).coerceAtLeast(0.5)
    Canvas(modifier.semantics{contentDescription=tr("График веса: от ","Weight chart: from ")+kg(points.first().kg)+tr(" до "," to ")+kg(points.last().kg)}) {
        fun at(p:WeightEntry)=Offset(size.width*(LocalDate.parse(p.date).toEpochDay()-first)/span,size.height-6.dp.toPx()-(size.height-12.dp.toPx())*((p.kg-low)/range).toFloat())
        val path=Path().apply{points.forEachIndexed{i,p->val o=at(p);if(i==0)moveTo(o.x,o.y) else lineTo(o.x,o.y)}}
        drawPath(path,color,style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round))
        points.forEach{drawCircle(color,3.dp.toPx(),at(it))}
    }
}

/** Morning weigh-in for the selected day; a second entry for the same day replaces the first. */
@Composable fun WeightDialog(vm:AppViewModel,date:String,suggested:Double?,close:()->Unit) {
    var text by rememberSaveable{mutableStateOf("")}
    val busy by vm.busy.collectAsStateWithLifecycle()
    val value=runCatching{parseQuantity(text)}.getOrNull()?.takeIf{it in 20.0..400.0}
    AlertDialog(onDismissRequest=close,icon={Icon(Icons.Outlined.MonitorWeight,null)},
        title={Text(tr("Вес за ","Weight for ")+if(date==LocalDate.now().toString())tr("сегодня","today") else dayMonth(date))},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(text,{text=it.take(6)},label={Text(tr("Вес, кг","Weight, kg"))},placeholder={suggested?.let{Text(plain(it))}},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("weightInput"))
            Text(tr("Взвешивайтесь утром: после туалета, до еды и воды, в одной и той же одежде — так дни можно сравнивать.","Weigh in the morning after the toilet, before food and drink, in the same clothes — that keeps days comparable."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},
        confirmButton={TextButton(onClick={value?.let{vm.saveWeight(date,it,close)}},enabled=value!=null&&!busy,modifier=Modifier.testTag("saveWeight")){Text(tr("Сохранить","Save"))}},
        dismissButton={TextButton(onClick=close){Text(tr("Отмена","Cancel"))}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun WeightHistory(vm:AppViewModel,snapshot:UserSnapshot,date:String,stats:WeightStats,close:()->Unit,record:()->Unit) {
    val list=snapshot.weights.sortedByDescending{it.date}
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SectionTitle(tr("Вес","Weight"),tr("Направление считается по средним за 7 дней: так суточные колебания воды не путают картину.","The direction uses 7-day averages so daily water swings do not mislead."))
            if(stats.series.size>=2)WeightLine(stats.series,Modifier.fillMaxWidth().height(140.dp))
            listOfNotNull(stats.weekChange?.let{tr("За неделю: ","Week: ")+change(it)+" · "+trendText(stats.trend)},stats.monthChange?.let{tr("За месяц: ","Month: ")+change(it)},stats.sinceStart?.let{tr("С начала: ","Since start: ")+change(it)}).forEach{Text(it)}
            Button(onClick=record,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text(tr("Записать вес","Log weight"))}
            if(list.isEmpty())Text(tr("Записей пока нет","No entries yet"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEachIndexed{i,w->
                val previous=list.getOrNull(i+1)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(kg(w.kg),fontWeight=FontWeight.SemiBold);Text(dayMonth(w.date)+(previous?.let{" · "+change(w.kg-it.kg)}?:""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={vm.deleteWeight(w)}){Icon(Icons.Outlined.DeleteOutline,tr("Удалить вес за ","Delete weight for ")+dayMonth(w.date))}
                }
            }
        }
    }
}
