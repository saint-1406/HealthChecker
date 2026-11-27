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
import androidx.compose.ui.text.withStyle
import java.time.LocalDate
import az.healthchecker.ui.kit.FilledTonalIconButton
import az.healthchecker.ui.kit.TextButton
import az.healthchecker.ui.kit.Button

internal fun kg(v:Double?,decimals:Int=1)="${number(v,decimals)} ${tr("кг","kg")}"
/** "−0,6 кг" / "+0,4 kg" with the sign always shown. */
internal fun change(v:Double)=(if(v>0)"+" else if(v<0)"−" else "±")+kg(kotlin.math.abs(v))
private fun trendText(t:Trend?)=when(t){Trend.DOWN->tr("снижается","going down");Trend.UP->tr("растёт","going up");Trend.STABLE->tr("стабилен","stable");null->""}

/**
 * Overview card: the latest weigh-in large; under it where the log starts, the change over a month (the 7-day averages,
 * as weightStats has it) and over the whole log (latest − first entry); then the 90-day line. Tap opens the history,
 * "+" logs a new weigh-in.
 */
@Composable fun WeightCard(vm:AppViewModel,snapshot:UserSnapshot,date:String) {
    val stats=weightStats(snapshot.weights,date,snapshot.profile?.initialWeight)
    var record by rememberSaveable{mutableStateOf(false)}
    var history by rememberSaveable{mutableStateOf(false)}
    // "In the beginning" is the first entry of the log itself, not the profile's starting weight.
    val first=snapshot.weights.filter{it.date<=date}.minByOrNull{it.date}
    val latest=stats.latest
    OverviewCard(Modifier.clickable{history=true}.testTag("weightCard")) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(Icons.Outlined.MonitorWeight,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
            Text(tr("Вес","Weight"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            FilledTonalIconButton(onClick={record=true},modifier=Modifier.size(32.dp).testTag("recordWeight")){Icon(Icons.Outlined.Add,tr("Записать вес","Log weight"),Modifier.size(18.dp))}
            Spacer(Modifier.weight(1f))
            if(latest!=null)Text(kg(latest.kg),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("weightLatest"))
        }
        if(latest==null)Text(tr("Взвешивайтесь утром и записывайте вес — здесь появится, снижается он или растёт","Weigh yourself in the morning and log it — the trend will appear here"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            if(latest.date!=date)Text(tr("последнее взвешивание ","last weigh-in ")+dayMonth(latest.date),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val figures=listOf(Triple(tr("В начале","At the start"),first?.let{kg(it.kg)}?:"—",false) to "weightStart",
                Triple(tr("За месяц","This month"),stats.monthChange?.let(::change)?:"—",stats.monthChange!=null) to "weightMonth",
                Triple(tr("За всё время","All time"),first?.let{change(latest.kg-it.kg)}?:"—",first!=null) to "weightAllTime")
            // Little room per line: one figure per row, label on the left.
            if(tightLayout())figures.forEach{(f,tag)->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(f.first,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                Text(f.second,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=if(f.third)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,modifier=Modifier.testTag(tag))}}
            else Row(Modifier.fillMaxWidth()){figures.forEach{(f,tag)->WeightFigure(f.first,f.second,f.third,Modifier.weight(1f),tag)}}
        }
        if(stats.series.size>=2)WeightLine(stats.series,Modifier.fillMaxWidth().height(48.dp))
    }
    if(record)WeightDialog(vm,date,stats.latest?.kg?:snapshot.profile?.currentWeight){record=false}
    if(history)WeightHistory(vm,snapshot,date,stats,{history=false}){history=false;record=true}
}

/** A label over a figure; a change ([accent]) is drawn in the main colour. */
@Composable private fun WeightFigure(label:String,value:String,accent:Boolean,modifier:Modifier,tag:String) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=if(accent)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,maxLines=1,modifier=Modifier.testTag(tag))
    }
}

/** The 90-day line: a thin stroke through the weigh-ins with a dot on the latest one. */
@Composable private fun WeightLine(points:List<WeightEntry>,modifier:Modifier) {
    val color=MaterialTheme.colorScheme.primary
    val first=LocalDate.parse(points.first().date).toEpochDay();val span=(LocalDate.parse(points.last().date).toEpochDay()-first).coerceAtLeast(1)
    val low=points.minOf{it.kg};val high=points.maxOf{it.kg};val range=(high-low).coerceAtLeast(0.5)
    Canvas(modifier.semantics{contentDescription=tr("График веса: от ","Weight chart: from ")+kg(points.first().kg)+tr(" до "," to ")+kg(points.last().kg)}) {
        fun at(p:WeightEntry)=Offset(4.dp.toPx()+(size.width-8.dp.toPx())*(LocalDate.parse(p.date).toEpochDay()-first)/span,size.height-5.dp.toPx()-(size.height-10.dp.toPx())*((p.kg-low)/range).toFloat())
        val path=Path().apply{points.forEachIndexed{i,p->val o=at(p);if(i==0)moveTo(o.x,o.y) else lineTo(o.x,o.y)}}
        drawPath(path,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        drawCircle(color,4.dp.toPx(),at(points.last()))
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
@Composable internal fun WeightHistory(vm:AppViewModel,snapshot:UserSnapshot,date:String,stats:WeightStats,close:()->Unit,record:()->Unit) {
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

/**
 * Arcade's weight section: the latest weigh-in large with a small unit and "+", a compact bar chart of the recent
 * weigh-ins (the latest in the accent colour), and the start, month and all-time figures underneath. Tap: the history.
 */
@Composable fun ArcadeWeight(vm:AppViewModel,snapshot:UserSnapshot,date:String,record:Boolean,setRecord:(Boolean)->Unit) {
    val stats=weightStats(snapshot.weights,date,snapshot.profile?.initialWeight)
    var history by rememberSaveable{mutableStateOf(false)}
    val first=snapshot.weights.filter{it.date<=date}.minByOrNull{it.date}
    val latest=stats.latest;val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable{history=true}.testTag("weightCard"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            ArcadeLabel(tr("Вес","Weight"),Modifier.weight(1f))
            FilledTonalIconButton(onClick={setRecord(true)},modifier=Modifier.size(36.dp).testTag("recordWeight")){Icon(Icons.Outlined.Add,tr("Записать вес","Log weight"),Modifier.size(18.dp))}
        }
        if(latest==null)Text(tr("Взвешивайтесь утром и записывайте вес — здесь появится, снижается он или растёт","Weigh yourself in the morning and log it — the trend will appear here"),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
        else {
            Row(verticalAlignment=Alignment.Bottom) {
                Text(androidx.compose.ui.text.buildAnnotatedString{append(number(latest.kg,1));withStyle(androidx.compose.ui.text.SpanStyle(fontSize=MaterialTheme.typography.bodyMedium.fontSize,fontWeight=FontWeight.Normal,color=c.onSurfaceVariant)){append(" "+tr("кг","kg"))}},
                    style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("weightLatest"))
                Spacer(Modifier.weight(1f))
                if(stats.series.size>=2)WeightBars(stats.series.takeLast(16),Modifier.width(150.dp).height(40.dp))
            }
            if(latest.date!=date)Text(tr("последнее взвешивание ","last weigh-in ")+dayMonth(latest.date),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            val figures=listOf(Triple(tr("Старт","Start"),first?.let{kg(it.kg)}?:"—",false) to "weightStart",
                Triple(tr("За месяц","This month"),stats.monthChange?.let(::change)?:"—",stats.monthChange!=null) to "weightMonth",
                Triple(tr("За всё время","All time"),first?.let{change(latest.kg-it.kg)}?:"—",first!=null) to "weightAllTime")
            if(tightLayout())figures.forEach{(f,tag)->Row(Modifier.fillMaxWidth()){Text(f.first,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,modifier=Modifier.weight(1f));Text(f.second,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Bold,modifier=Modifier.testTag(tag))}}
            else Row(Modifier.fillMaxWidth()){figures.forEach{(f,tag)->Column(Modifier.weight(1f)){Text(f.first,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1);Text(f.second,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Bold,maxLines=1,modifier=Modifier.testTag(tag))}}}
        }
    }
    if(record)WeightDialog(vm,date,stats.latest?.kg?:snapshot.profile?.currentWeight){setRecord(false)}
    if(history)WeightHistory(vm,snapshot,date,stats,{history=false}){history=false;setRecord(true)}
}

/** The recent weigh-ins as square bars over a floor just under the lowest; the last one in the accent colour. */
@Composable private fun WeightBars(points:List<WeightEntry>,modifier:Modifier) {
    val c=MaterialTheme.colorScheme
    val low=points.minOf{it.kg};val high=points.maxOf{it.kg};val range=(high-low).coerceAtLeast(0.5)
    Canvas(modifier.semantics{contentDescription=tr("График веса: от ","Weight chart: from ")+kg(points.first().kg)+tr(" до "," to ")+kg(points.last().kg)}) {
        val gap=2.dp.toPx();val w=(size.width-gap*(points.size-1))/points.size
        points.forEachIndexed{i,p->
            val h=size.height*(0.3f+0.7f*((p.kg-low)/range).toFloat())
            drawRect(if(i==points.lastIndex)c.primary else c.primary.copy(alpha=0.45f),Offset(i*(w+gap),size.height-h),androidx.compose.ui.geometry.Size(w,h))
        }
    }
}
