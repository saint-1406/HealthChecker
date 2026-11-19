package az.healthchecker.ui.training
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.LocalDate

/** "3 × 12 · 8 kg", "2 × 45 s", "30 min". */
fun setsLabel(sets:Int?,reps:Int?,seconds:Int?,loadKg:Double?,minutes:Double?=null):String = listOfNotNull(
    sets?.let{s->reps?.let{"$s × $it"}?:seconds?.let{"$s × $it ${tr("с","s")}"}},loadKg?.let{"${plain(it)} ${tr("кг","kg")}"},
    if(sets==null)minutes?.let{"${number(it)} ${tr("мин","min")}"} else null).joinToString(" · ")
fun setsLabel(w:WorkoutEntry):String = setsLabel(w.sets,w.reps,w.seconds,w.loadKg,w.minutes)
fun setsLabel(n:NextStep):String = setsLabel(n.sets,n.reps,n.seconds,n.loadKg)

fun recordName(k:RecordKind):String = when(k) {
    RecordKind.LOAD->tr("Самая тяжёлая гантель","Heaviest dumbbell");RecordKind.E1RM->tr("Расчётный максимум (1ПМ)","Estimated max (1RM)")
    RecordKind.REPS->tr("Больше всего повторений в подходе","Most reps in a set");RecordKind.TOTAL_REPS->tr("Больше всего повторений за раз","Most reps in a session")
    RecordKind.SECONDS->tr("Самый долгий подход","Longest set");RecordKind.MINUTES->tr("Дольше всего","Longest")
}
fun recordValue(k:RecordKind,v:Double):String = when(k) {
    RecordKind.LOAD->"${plain(v)} ${tr("кг","kg")}";RecordKind.E1RM->"≈ ${number(v,1)} ${tr("кг","kg")}"
    RecordKind.REPS,RecordKind.TOTAL_REPS->count(v.toInt(),"повторение","повторения","повторений","rep","reps")
    RecordKind.SECONDS->"${v.toInt()} ${tr("с","s")}";RecordKind.MINUTES->"${number(v)} ${tr("мин","min")}"
}
/** "new record: 14 reps, ≈ 13,3 kg 1RM". */
fun recordsText(entry:WorkoutEntry,kinds:Set<RecordKind>):String =
    kinds.sortedBy{it.ordinal}.joinToString(", "){k->(if(k==RecordKind.E1RM)tr("1ПМ ","1RM ") else "")+recordValue(k,entry.score(k)!!)}

/** Kinds worth listing for an exercise, in a sensible order. */
private fun shownKinds(records:Map<RecordKind,WorkoutEntry>):List<RecordKind> =
    listOf(RecordKind.E1RM,RecordKind.LOAD,RecordKind.REPS,RecordKind.TOTAL_REPS,RecordKind.SECONDS,RecordKind.MINUTES).filter{it in records}
        .filter{it!=RecordKind.MINUTES||records.size==1}

/**
 * In the workout editor: last time, the records, a one-tap next step and a small chart; [newRecord] is what the values
 * typed now would beat.
 */
@Composable fun LastTimePanel(history:List<WorkoutEntry>,last:WorkoutEntry,measure:ExerciseMeasure,newRecord:String?,apply:(NextStep)->Unit) {
    var chart by remember{mutableStateOf(false)}
    Panel(Modifier.testTag("lastTime")) {
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Outlined.History,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp))
            Text(tr("Прошлый раз · ","Last time · ")+dayMonth(last.localDate),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        }
        Text(setsLabel(last),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("lastTimeValue"))
        val records=personalRecords(history)
        shownKinds(records).take(3).forEach{k->Row(Modifier.fillMaxWidth()){
            Text(recordName(k),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            Text(recordValue(k,records.getValue(k).score(k)!!),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium)
        }}
        nextStep(last,measure)?.let{n->
            FilledTonalButton(onClick={apply(n)},modifier=Modifier.fillMaxWidth().testTag("applyNextStep")){Icon(Icons.AutoMirrored.Outlined.TrendingUp,null);Spacer(Modifier.width(8.dp));Text(tr("Шаг вперёд: ","Step up: ")+setsLabel(n))}
            Text(if(n.loadKg!=null&&n.loadKg!=last.loadKg)tr("$TOP_REPS повторений даются — пора взять гантель тяжелее и начать с $RESTART_REPS.","$TOP_REPS reps are done — time for a heavier dumbbell, starting at $RESTART_REPS.")
                else tr("Понемногу: одно повторение, подход или 5 секунд за раз. Если тяжело — повторите прошлый результат.","Little by little: one rep, set or 5 seconds at a time. If it is hard, repeat last time."),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val metric=mainMetric(history,measure);val series=progressSeries(history,metric)
        if(series.size>=2)TextButton(onClick={chart=!chart},modifier=Modifier.testTag("toggleProgressChart")){Text(if(chart)tr("Скрыть график","Hide the chart") else tr("График: ","Chart: ")+recordName(metric).lowercase(Lang.locale))}
        if(chart&&series.size>=2)ProgressChart(series,metric,Modifier.fillMaxWidth().height(120.dp))
    }
    newRecord?.let{Text("🏆 "+tr("Новый рекорд: ","New record: ")+it,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("newRecord"))}
}

@Composable fun ProgressChart(points:List<ProgressPoint>,metric:RecordKind,modifier:Modifier) {
    val color=MaterialTheme.colorScheme.primary;val grid=MaterialTheme.colorScheme.outlineVariant
    val first=LocalDate.parse(points.first().date).toEpochDay();val span=(LocalDate.parse(points.last().date).toEpochDay()-first).coerceAtLeast(1)
    val low=points.minOf{it.value};val high=points.maxOf{it.value};val range=(high-low).takeIf{it>0}?:1.0
    Column {
        Canvas(modifier.semantics{contentDescription=recordName(metric)+": "+recordValue(metric,points.first().value)+" → "+recordValue(metric,points.last().value)}.testTag("progressChart")) {
            val pad=8.dp.toPx()
            fun at(p:ProgressPoint)=Offset(pad+(size.width-2*pad)*(LocalDate.parse(p.date).toEpochDay()-first)/span,size.height-pad-(size.height-2*pad)*((p.value-low)/range).toFloat())
            drawLine(grid,Offset(0f,size.height-pad),Offset(size.width,size.height-pad),1.dp.toPx())
            val path=Path().apply{points.forEachIndexed{i,p->val o=at(p);if(i==0)moveTo(o.x,o.y) else lineTo(o.x,o.y)}}
            drawPath(path,color,style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round))
            points.forEach{drawCircle(color,3.5.dp.toPx(),at(it))}
        }
        Row(Modifier.fillMaxWidth()){
            Text(shortDate(points.first().date)+" · "+recordValue(metric,points.first().value),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            Text(shortDate(points.last().date)+" · "+recordValue(metric,points.last().value),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Training → Progress: every exercise done in sets, with sessions, the latest result and the change since the start. */
@Composable fun ProgressScreen(vm:AppViewModel,snapshot:UserSnapshot,back:()->Unit,open:(String)->Unit) {
    val list=remember(snapshot.workouts){strengthProgress(snapshot){vm.exercise(it)?.measure}}
    LazyColumn(Modifier.fillMaxSize().testTag("progressList"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))};Text(tr("Прогресс в упражнениях","Exercise progress"),style=MaterialTheme.typography.headlineSmall)}}
        if(list.isEmpty())item{Text(tr("Здесь появятся упражнения, которые вы делаете подходами: последний результат, рекорды и график. Запишите первое упражнение во вкладке «Тренировки».","Exercises you do in sets will show up here with the latest result, records and a chart. Log the first one on the Training tab."),style=MaterialTheme.typography.bodyMedium)}
        items(list,key={it.exerciseId}){p->Panel(Modifier.clickable{open(p.exerciseId)}.testTag("progress-${p.exerciseId}")) {
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(vm.workoutName(p.last),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text(setsLabel(p.last)+" · "+dayMonth(p.last.localDate),style=MaterialTheme.typography.bodyMedium)
                    Text(count(p.sessions,"тренировка","тренировки","тренировок","session","sessions")+(p.best?.let{" · "+tr("лучшее: ","best: ")+recordValue(p.metric,it)}?:""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                p.changePercent?.let{c->Text((if(c>0)"+" else if(c<0)"−" else "")+"${kotlin.math.abs(c)} %",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,
                    color=if(c>0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }}
        item{Text(tr("Расчётный максимум (1ПМ) — оценка по формуле Эпли: вес × (1 + повторения / 30), для подходов до 12 повторений. Это не совет поднимать такой вес.",
            "The estimated max (1RM) is Epley's formula: weight × (1 + reps / 30), for sets of up to 12 reps. It is not advice to lift that weight."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

/** One exercise: chart of the main measure, personal records and every session, newest first; a session opens for editing. */
@Composable fun ExerciseProgressScreen(vm:AppViewModel,snapshot:UserSnapshot,exerciseId:String,back:()->Unit,edit:(WorkoutEntry)->Unit) {
    val history=remember(snapshot.workouts,exerciseId){snapshot.exerciseHistory(exerciseId)}
    val measure=vm.exercise(exerciseId)?.measure?:if(history.any{it.reps!=null})ExerciseMeasure.REPS else ExerciseMeasure.SECONDS
    val metric=mainMetric(history,measure);val series=progressSeries(history,metric)
    val records=personalRecords(history);val trophies=remember(history){recordsAtTheTime(history)}
    LazyColumn(Modifier.fillMaxSize().testTag("exerciseProgress"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))};Text(history.lastOrNull()?.let(vm::workoutName)?:"",style=MaterialTheme.typography.headlineSmall)}}
        if(history.isEmpty()){item{Text(tr("Записей нет.","No entries."))};return@LazyColumn}
        item{Panel {
            Text(recordName(metric),style=MaterialTheme.typography.titleMedium)
            if(series.size>=2)ProgressChart(series,metric,Modifier.fillMaxWidth().height(160.dp))
            else Text(tr("График появится после второй тренировки с этим упражнением.","The chart appears after the second session with this exercise."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        item{Panel(Modifier.testTag("personalRecords")) {
            Text(tr("Личные рекорды","Personal records"),style=MaterialTheme.typography.titleMedium)
            shownKinds(records).forEach{k->val w=records.getValue(k);Row(Modifier.fillMaxWidth()){
                Column(Modifier.weight(1f)){Text(recordName(k));Text(dayMonth(w.localDate)+" · "+setsLabel(w),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text(recordValue(k,w.score(k)!!),fontWeight=FontWeight.SemiBold)
            }}
        }}
        item{SectionTitle(tr("Все тренировки","All sessions"),count(history.size,"запись","записи","записей","entry","entries"))}
        items(history.reversed(),key={it.id}){w->Panel(Modifier.clickable{edit(w)}) {
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(dayMonth(w.localDate,w.localDate.take(4)!=LocalDate.now().year.toString()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(setsLabel(w),fontWeight=FontWeight.Medium)}
                trophies[w.id]?.let{Text("🏆",modifier=Modifier.semantics{contentDescription=tr("Рекорд: ","Record: ")+recordsText(w,it)})}
            }
        }}
    }
}
