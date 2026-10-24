package az.healthchecker.ui.training
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import kotlinx.coroutines.delay

/**
 * "Workout now": today's suggested exercises one card each, with a picture of the movement, the full technique and
 * one-tap logging; the stopwatch and a rest or hold timer stay at the bottom. The screen stays on while it is open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun WorkoutNowScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,plan:Recommendation?,back:()->Unit,anotherSet:()->Unit,adjust:(Exercise,Prescription)->Unit,edit:(WorkoutEntry)->Unit,addOther:()->Unit) {
    val view=LocalView.current
    DisposableEffect(Unit){view.keepScreenOn=true;onDispose{view.keepScreenOn=false}}
    val busy by vm.busy.collectAsStateWithLifecycle()
    val day=snapshot.training(date)
    val weight=snapshot.bodyWeight(date)
    val prefs=snapshot.trainingPrefs?:TrainingPrefs()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start=8.dp,end=16.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            Column(Modifier.weight(1f)){
                Text(tr("Тренировка сейчас","Workout now"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(dayMonth(date,true),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(plan==null){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
        else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("workoutNowList"),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val done=plan.items.count{r->day.workouts.any{it.exerciseId==r.exercise.id}}
            item{Panel {
                Text(tr("Выполнено ","Done ")+"$done "+tr("из","of")+" ${plan.items.size}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("workoutProgress"))
                LinearProgressIndicator(progress={if(plan.items.isEmpty())0f else done.toFloat()/plan.items.size},modifier=Modifier.fillMaxWidth())
                val minutes=plan.items.sumOf{it.prescription.minutesFor(it.exercise)?:0.0}
                val kcal=plan.items.sumOf{r->r.prescription.minutesFor(r.exercise)?.let{burnedKcal(r.exercise.met,weight,it)}?:0.0}
                Text(listOfNotNull(tr("План","Plan")+" ≈ ${number(minutes)} ${tr("мин","min")}",if(weight!=null)"≈ ${number(kcal)} $KCAL" else null).joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
                // Without a body weight the calories are unknown, so only the minutes are shown.
                if(day.workouts.isNotEmpty())Text(listOfNotNull(tr("Сделано сегодня","Done today")+": ${number(day.workouts.sumOf{it.minutes})} ${tr("мин","min")}",if(day.burned.value>0)"≈ ${number(day.burned.value)}${if(!day.burned.complete)"+" else ""} $KCAL" else null).joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                Text(if(plan.recovery)tr("Мышцы ещё восстанавливаются — лёгкий день","Muscles are still recovering — an easy day") else tr("Акцент: ","Focus: ")+plan.focus.distinct().joinToString(", "){muscleLabel(it).lowercase(Lang.locale)},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${if(prefs.dumbbells)tr("С гантелями и без","With and without dumbbells") else tr("Без инвентаря","No equipment")} · ${tr("уровень","level")}: ${levelLabel(prefs.level).lowercase(Lang.locale)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
            itemsIndexed(plan.items,key={_,r->r.exercise.id}){i,r->
                WorkoutCard(vm,i,plan.items.size,r,weight,day.workouts.firstOrNull{it.exerciseId==r.exercise.id},busy,
                    log={if(r.prescription.minutesFor(r.exercise)!=null)vm.logPrescribed(r.exercise,r.prescription,date) else adjust(r.exercise,r.prescription)},
                    adjust={adjust(r.exercise,r.prescription)},edit=edit)
            }
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick=anotherSet,modifier=Modifier.testTag("anotherSet")){Icon(Icons.Outlined.Refresh,null);Spacer(Modifier.width(6.dp));Text(tr("Другая подборка","Another set"))}
                OutlinedButton(onClick=addOther){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text(tr("Другое упражнение","Another exercise"))}
            }}
            item{Text(tr("Схемы движений — упрощённые рисунки приложения; свою картинку можно добавить в карточке упражнения («Изменить»). При боли остановитесь.",
                "Movement schemes are the app's simplified drawings; add your own picture in the exercise card (\"Adjust\"). Stop if it hurts."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        WorkoutTools(vm)
    }
}

fun roleLabel(r:RecommendedExercise):String = when(r.role){
    Role.WARMUP->tr("РАЗМИНКА","WARM-UP");Role.MAIN->tr("ОСНОВНАЯ ЧАСТЬ","MAIN PART")+" · ${muscleLabel(r.exercise.muscles.first()).uppercase(Lang.locale)}"
    Role.CARDIO->tr("КАРДИО","CARDIO");Role.COOLDOWN->tr("ЗАМИНКА","COOL-DOWN")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun WorkoutCard(vm:AppViewModel,index:Int,total:Int,r:RecommendedExercise,weight:Double?,logged:WorkoutEntry?,busy:Boolean,log:()->Unit,adjust:()->Unit,edit:(WorkoutEntry)->Unit) {
    val e=r.exercise;val p=r.prescription
    Panel(Modifier.testTag("workoutCard")) {
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("${index+1}/$total · "+roleLabel(r),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f))
            if(logged!=null){Icon(Icons.Outlined.CheckCircle,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(4.dp));Text(tr("Записано","Logged"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
        }
        ExerciseIllustration(vm,e,height=190.dp)
        Text(e.displayName,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        if(e.alsoKnownAs.isNotEmpty())Text(e.alsoKnownAs.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val minutes=p.minutesFor(e)
        Text(listOfNotNull(prescriptionLabel(p).takeIf{it.isNotEmpty()},if(e.equipment==Equipment.DUMBBELLS)tr("гантели","dumbbells") else null,minutes?.let{m->burnedKcal(e.met,weight,m)?.let{"≈ ${number(it)} $KCAL"}}).joinToString(" · "),
            style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
        Text(e.displayCue,style=MaterialTheme.typography.bodyMedium)
        if((p.sets?:0)>1)Text(tr("Отдых между подходами ","Rest between sets ")+"≈ ${restSeconds(e).toInt()} ${tr("с","s")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(logged==null){
                Button(onClick=log,enabled=!busy,modifier=Modifier.testTag("logPrescribed")){Icon(Icons.Outlined.Check,null);Spacer(Modifier.width(6.dp));Text(tr("Записать","Log it"))}
                OutlinedButton(onClick=adjust){Text(tr("Изменить","Adjust"))}
            } else OutlinedButton(onClick={edit(logged)}){Icon(Icons.Outlined.Edit,null);Spacer(Modifier.width(6.dp));Text(tr("Изменить запись","Edit the entry"))}
            // A hold is timed by its own length, a timed activity by its minutes, sets by the rest between them.
            val hold=p.seconds?.takeIf{e.measure==ExerciseMeasure.SECONDS}
            val timed=p.minutes?.takeIf{e.measure==ExerciseMeasure.MINUTES}?.let{(it*60).toInt()}
            when {
                hold!=null->TextButton(onClick={vm.startCountdown(hold,e.displayName)},modifier=Modifier.testTag("holdTimer")){Icon(Icons.Outlined.Timer,null);Spacer(Modifier.width(6.dp));Text(tr("Таймер ","Timer ")+"$hold ${tr("с","s")}")}
                timed!=null->TextButton(onClick={vm.startCountdown(timed,e.displayName)}){Icon(Icons.Outlined.Timer,null);Spacer(Modifier.width(6.dp));Text(tr("Таймер ","Timer ")+"${timed/60} ${tr("мин","min")}")}
                (p.sets?:0)>1->TextButton(onClick={vm.startCountdown(restSeconds(e).toInt(),tr("Отдых","Rest"))}){Icon(Icons.Outlined.HourglassTop,null);Spacer(Modifier.width(6.dp));Text(tr("Отдых ","Rest ")+"${restSeconds(e).toInt()} ${tr("с","s")}")}
            }
        }
    }
}

/** Bottom tool bar of the workout: rest/hold timer and the app's stopwatch. */
@Composable private fun WorkoutTools(vm:AppViewModel) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,shadowElevation=8.dp,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            CountdownRow(vm)
            HorizontalDivider()
            StopwatchRow(vm)
        }
    }
}

@Composable private fun CountdownRow(vm:AppViewModel) {
    val c by vm.countdown.collectAsStateWithLifecycle()
    val timer=c
    var left by remember{mutableLongStateOf(vm.countdownMs())}
    LaunchedEffect(timer){left=vm.countdownMs();while(timer?.running==true){delay(100);left=vm.countdownMs()}}
    if(timer==null)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        Icon(Icons.Outlined.HourglassTop,null,tint=MaterialTheme.colorScheme.primary)
        Text(tr("Отдых","Rest"),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        listOf(30,60,90,120).forEach{s->AssistChip(onClick={vm.startCountdown(s,tr("Отдых","Rest"))},label={Text(if(s<120)"$s ${tr("с","s")}" else "2 ${tr("мин","min")}")},modifier=Modifier.testTag("rest$s"))}
    } else Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        val over=!timer.running&&left==0L
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Column(Modifier.weight(1f)){
                Text(if(over)tr("Время вышло · ","Time is up · ")+timer.label else timer.label,style=MaterialTheme.typography.labelMedium,color=if(over)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                Text(formatCountdown(left),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("countdownTime"))
            }
            if(over)FilledIconButton(onClick={vm.startCountdown((timer.totalMs/1000).toInt(),timer.label)}){Icon(Icons.Outlined.Replay,tr("Ещё раз","Again"))}
            else {
                TextButton(onClick={vm.addCountdown(15)}){Text("+15 ${tr("с","s")}")}
                if(timer.running)FilledTonalIconButton(onClick=vm::pauseCountdown,modifier=Modifier.testTag("countdownPause")){Icon(Icons.Outlined.Pause,tr("Пауза","Pause"))}
                else FilledIconButton(onClick=vm::resumeCountdown){Icon(Icons.Outlined.PlayArrow,tr("Продолжить","Resume"))}
            }
            IconButton(onClick=vm::cancelCountdown,modifier=Modifier.testTag("countdownClose")){Icon(Icons.Outlined.Close,tr("Закрыть таймер","Close the timer"))}
        }
        LinearProgressIndicator(progress={if(timer.totalMs>0)(left.toFloat()/timer.totalMs).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
    }
}

@Composable private fun StopwatchRow(vm:AppViewModel) {
    val sw by vm.stopwatch.collectAsStateWithLifecycle()
    var now by remember{mutableLongStateOf(vm.stopwatchMs())}
    LaunchedEffect(sw){now=vm.stopwatchMs();while(sw.running){delay(100);now=vm.stopwatchMs()}}
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Icon(Icons.Outlined.Timer,tr("Секундомер","Stopwatch"),tint=MaterialTheme.colorScheme.primary)
        Text(formatStopwatch(now),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).testTag("stopwatchTime"))
        if(sw.running)FilledTonalIconButton(onClick=vm::pauseStopwatch,modifier=Modifier.testTag("stopwatchPause")){Icon(Icons.Outlined.Pause,tr("Пауза","Pause"))}
        else FilledIconButton(onClick=vm::startStopwatch,modifier=Modifier.testTag("stopwatchStart")){Icon(Icons.Outlined.PlayArrow,if(now>0)tr("Продолжить","Resume") else tr("Старт","Start"))}
        IconButton(onClick=vm::resetStopwatch,enabled=now>0&&!sw.running){Icon(Icons.Outlined.RestartAlt,tr("Сброс","Reset"))}
    }
}
