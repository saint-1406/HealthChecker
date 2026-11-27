package az.healthchecker.ui.dashboard
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.diary.MealDraftCard
import az.healthchecker.ui.kit.Button
import az.healthchecker.ui.kit.OutlinedButton
import az.healthchecker.ui.steps.stepsText
import az.healthchecker.ui.theme.ArcadeCut
import az.healthchecker.ui.theme.pulseColor
import az.healthchecker.ui.theme.reducedMotion
import az.healthchecker.ui.watch.hoursMinutes
import az.healthchecker.ui.watch.watchActivityLabel
import az.healthchecker.watch.WatchStatus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Your day" in Arcade, after the mockup: the big title, the VitaCore workout as the hero block with cut corners and a
 * pixel athlete, then food and water, weight, the watch and the week as sections divided by hairlines. At the bottom a
 * strip of quick actions: "Day" and "7 days" jump within the overview, "+ Entry" offers every kind of entry.
 * Same data, same handlers and same test tags as the standard overview.
 */
@Composable fun ArcadeDashboard(vm:AppViewModel,snapshot:UserSnapshot,date:String,backupError:String?,add:()->Unit,water:()->Unit,openProfile:()->Unit,
    openTraining:()->Unit,openSteps:()->Unit,openProtein:()->Unit,openDiary:()->Unit,openDraft:()->Unit,chooseFolder:()->Unit,openExpenditure:()->Unit,openWatch:()->Unit,logExercise:()->Unit) {
    val day=snapshot.day(date)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val draft by vm.mealDraft.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    var details by rememberSaveable{mutableStateOf(false)}
    var entrySheet by rememberSaveable{mutableStateOf(false)}
    var recordWeight by rememberSaveable{mutableStateOf(false)}
    val scroll=rememberScrollState();val scope=rememberCoroutineScope()
    var weekY by remember{mutableIntStateOf(Int.MAX_VALUE)}
    val still=reducedMotion()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal=16.dp).padding(top=8.dp,bottom=16.dp).testTag("overviewList"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(tr("Ваш день","Your day"),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("arcadeTitle"))
            if(backupError!=null)Surface(shape=RectangleShape,color=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.fillMaxWidth().clickable(onClick=openProfile)){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Outlined.SyncProblem,null);Column{Text(tr("Резервная копия не обновляется","The backup is not updating"),fontWeight=FontWeight.SemiBold);Text(tr("Откройте профиль, чтобы повторить или выбрать папку заново","Open the profile to retry or choose the folder again"),style=MaterialTheme.typography.bodySmall)}}
            }
            if(settings.overviewCards.training)HeroWorkout(snapshot.training(date),openTraining)
            Rule()
            FoodSection(day,settings.waterMl,still,{details=true},add,water,openProtein)
            draft?.let{MealDraftCard(it,openDraft)}
            if(photos.isNotEmpty())Row(Modifier.fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outlineVariant).clickable(onClick=openDiary).padding(12.dp).testTag("photosWaiting"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Outlined.PhotoCamera,null,tint=MaterialTheme.colorScheme.primary)
                Text(count(photos.size,"фото ждёт внесения","фото ждут внесения","фото ждут внесения","photo to log","photos to log"),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight,null)
            }
            if(date==LocalDate.now().toString())az.healthchecker.ui.profile.ExpenditureCard(vm,snapshot,openExpenditure)
            if(settings.overviewCards.weight){Rule();ArcadeWeight(vm,snapshot,date,recordWeight,{recordWeight=it})}
            if(settings.overviewCards.steps){Rule();WatchSection(vm,snapshot,date,openSteps,openWatch)}
            Rule()
            Box(Modifier.onGloballyPositioned{weekY=it.positionInParent().y.toInt()}){WeekSection(snapshot,date){details=true}}
            BackupSetupCard(vm,snapshot,chooseFolder)
        }
        // Quick actions inside the overview; the tab strip below still reaches every section.
        val inWeek=scroll.value>=weekY-scroll.viewportSize/2&&weekY!=Int.MAX_VALUE
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            QuickAction(tr("День","Day"),!inWeek,Modifier.weight(1f).testTag("quickDay")){scope.launch{if(still)scroll.scrollTo(0) else scroll.animateScrollTo(0)}}
            QuickAction(tr("7 дней","7 days"),inWeek,Modifier.weight(1f).testTag("quickWeek")){scope.launch{if(still)scroll.scrollTo(weekY) else scroll.animateScrollTo(weekY)}}
            QuickAction("+ "+tr("Запись","Entry"),false,Modifier.weight(1.3f).testTag("quickEntry")){entrySheet=true}
        }
    }
    if(details)EnergySheet(vm,day){details=false}
    if(entrySheet)EntrySheet(settings.waterMl,{entrySheet=false},add,water,{recordWeight=true},logExercise)
}

/** A hairline between sections. */
@Composable private fun Rule()=HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)

/** A square quick-action cell: outlined, the current one in the accent colour. */
@Composable private fun QuickAction(label:String,on:Boolean,modifier:Modifier,onClick:()->Unit) {
    val c=MaterialTheme.colorScheme
    Box(modifier.heightIn(min=48.dp).border(if(on)2.dp else 1.dp,if(on)c.primary else c.outline).clickable(onClick=onClick).semantics{role=Role.Button;selected=on},contentAlignment=Alignment.Center){
        Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold,color=if(on)c.primary else c.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(horizontal=6.dp))
    }
}

/** The hero block: the day's VitaCore workout on the accent colour with cut corners and the pixel athlete. */
@Composable private fun HeroWorkout(training:TrainingDay,open:()->Unit) {
    val c=MaterialTheme.colorScheme
    val done=training.workouts.isNotEmpty()
    val title=when{done->tr("Тренировка завершена","Workout done");training.trained==true->tr("Тренировка отмечена","Workout marked");training.trained==false->tr("День отдыха","Rest day");else->tr("Тренировки пока нет","No workout yet")}
    Surface(onClick=open,shape=ArcadeCut(18.dp),color=if(done||training.trained==true)c.primary else c.primaryContainer,contentColor=if(done||training.trained==true)c.onPrimary else c.onPrimaryContainer,modifier=Modifier.fillMaxWidth().testTag("trainingCard")) {
        Row(Modifier.padding(start=16.dp,top=16.dp,bottom=16.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(tr("ТРЕНИРОВКА VITACORE","VITACORE WORKOUT"),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                if(done)FlowRow(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("≈${number(training.burned.value)}${if(!training.burned.complete)"+" else ""} $KCAL",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("trainingKcal"))
                    Text(count(training.workouts.size,"упражнение","упражнения","упражнений","exercise","exercises"),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.align(Alignment.CenterVertically))
                }
                else Text(when(training.trained){true->tr("Упражнения не записаны — откройте «Тренировки»","No exercises logged — open Training");false->tr("Отдых тоже часть плана","Rest is part of the plan too")
                    null->tr("Рекомендации на сегодня — во вкладке «Тренировки»","Today's suggestions are on the Training tab")},style=MaterialTheme.typography.bodySmall)
            }
            val ink=LocalContentColor.current
            PixelArt(PIXEL_ATHLETE,mapOf('D' to ink,'M' to ink.copy(alpha=0.45f),'P' to pulseColorOn(ink),'W' to c.surface),Modifier.size(84.dp).semantics{contentDescription=""})
        }
    }
}
/** The plates of the pixel athlete: the pulse pink, darker when it sits on a light block. */
@Composable private fun pulseColorOn(ink:Color):Color = if(ink.red+ink.green+ink.blue<1.5f)Color(0xFFC2185B) else Color(0xFFFF8FAB)

/**
 * Food: calories as a big figure over the goal with a bar of 24 segments that fill as entries come in, protein, and
 * water with "+ glass" and "Log food". Tapping the summary opens the day's energy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FoodSection(day:DayState,glassMl:Int,still:Boolean,openDetails:()->Unit,add:()->Unit,water:()->Unit,openProtein:()->Unit) {
    val s=day.summary;val goals=day.goals;val c=MaterialTheme.colorScheme
    val kcal by animateIntAsState(s.kcal.value.toInt(),if(still)snap() else tween(450),label="kcal")
    val waterMl by animateIntAsState(s.waterMl.toInt(),if(still)snap() else tween(350),label="water")
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().clickable(onClick=openDetails).semantics{contentDescription=tr("Энергия за день: подробнее","Energy for the day: details")}.testTag("energyCard"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.Bottom) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){ArcadeLabel(tr("Питание","Food"));Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp))}
                Text(buildAnnotatedString{
                    withStyle(SpanStyle(fontSize=MaterialTheme.typography.headlineMedium.fontSize,fontWeight=FontWeight.Bold)){append(number(kcal.toDouble())+if(!s.kcal.complete)"+" else "")}
                    withStyle(SpanStyle(color=c.onSurfaceVariant)){append(goals?.kcal?.let{" / ${number(it)} $KCAL"}?:" $KCAL")}
                },style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("kcalLine"))
            }
            SegmentBar(goals?.kcal?.let{(s.kcal.value/it).toFloat()}?:0f,still,Modifier.fillMaxWidth().height(8.dp))
            if(goals?.kcal==null)Text(tr("Цель по калориям не задана — её можно задать в профиле","No calorie goal — set one in the profile"),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().clickable(onClick=openProtein).testTag("proteinCard"),verticalAlignment=Alignment.CenterVertically) {
            Text(tr("Белок ","Protein ")+"${number(s.protein.value,1)}${if(!s.protein.complete)"+" else ""}"+(goals?.protein?.let{" / ${number(it)}"}?:"")+" $G",style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant,modifier=Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight,tr("Белок подробнее","Protein details"),tint=c.onSurfaceVariant,modifier=Modifier.size(18.dp))
        }
        val waterInfo=@Composable{m:Modifier->Column(m){Text(tr("Вода","Water"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("${number(waterMl.toDouble())}"+(goals?.waterMl?.let{" / ${number(it)}"}?:"")+" $ML",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,modifier=Modifier.testTag("waterLine"))}}
        val waterButton=@Composable{m:Modifier->OutlinedButton(onClick=water,contentPadding=PaddingValues(horizontal=12.dp),modifier=m.heightIn(min=48.dp).testTag("waterButton")){Text("+$glassMl $ML",maxLines=1,fontWeight=FontWeight.Bold)}}
        val foodButton=@Composable{m:Modifier->Button(onClick=add,contentPadding=PaddingValues(horizontal=12.dp),modifier=m.heightIn(min=48.dp).testTag("logFoodButton")){Text("+ "+tr("Записать еду","Log food"),maxLines=1,fontWeight=FontWeight.Bold)}}
        if(tightLayout())Column(verticalArrangement=Arrangement.spacedBy(8.dp)){waterInfo(Modifier);waterButton(Modifier.fillMaxWidth());foodButton(Modifier.fillMaxWidth())}
        else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){waterInfo(Modifier.weight(1f));waterButton(Modifier);foodButton(Modifier)}
    }
}

/**
 * A progress bar of [segments] square cells; past the goal the extra share is drawn in the pulse colour. The filling
 * moves to its new length when the data changes (instantly with reduced motion).
 */
@Composable fun SegmentBar(progress:Float,still:Boolean,modifier:Modifier,segments:Int=24) {
    val shown by animateFloatAsState(progress.coerceIn(0f,2f),if(still)snap() else tween(500),label="segments")
    val c=MaterialTheme.colorScheme;val over=pulseColor
    androidx.compose.foundation.Canvas(modifier.semantics{contentDescription="${(progress*100).toInt()}%"}) {
        val gap=3.dp.toPx();val w=(size.width-gap*(segments-1))/segments
        val full=shown*segments
        (0 until segments).forEach{i->
            val x=i*(w+gap)
            drawRect(c.outlineVariant,androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Size(w,size.height))
            val part=(full-i).coerceIn(0f,1f)
            if(part>0f)drawRect(c.primary,androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Size(w*part,size.height))
            val extra=(full-segments-i).coerceIn(0f,1f)
            if(extra>0f)drawRect(over,androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Size(w*extra,size.height))
        }
    }
}

/**
 * The watch's day as an instrument row of three framed cells — steps with distance, average pulse, sleep — and the
 * watch's workouts as separate lines with an accent edge. Disconnected: a line that opens the watch settings.
 */
@Composable private fun WatchSection(vm:AppViewModel,snapshot:UserSnapshot,date:String,openSteps:()->Unit,openWatch:()->Unit) {
    val state by vm.watch.collectAsStateWithLifecycle()
    val today=LocalDate.now().toString();val data=state.data;val zone=ZoneId.systemDefault();val c=MaterialTheme.colorScheme
    val connected=state.status==WatchStatus.CONNECTED||state.status==WatchStatus.PARTIAL
    val steps=snapshot.stepsOn(date,data);val pulse=data?.heartOn(date,today);val asleep=data?.asleepOn(date,zone)
    Column(Modifier.testTag("watchCard"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            ArcadeLabel(if(date==today)tr("Часы сегодня","Watch today") else tr("Часы","Watch")+" · "+dayMonth(date),Modifier.weight(1f).testTag("watchTitle"))
            if(state.loading)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp) else Text("Health Connect",style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant,maxLines=1,modifier=Modifier.clickable(onClick=openWatch).padding(4.dp))
        }
        val cells=listOf<@Composable (Modifier)->Unit>(
            {m->Cell(tr("Шаги","Steps"),stepsText(steps.steps),c.onSurface,snapshot.profile?.takeIf{steps.steps>0}?.let{number(stepDistanceKm(steps.steps,it),1)+" "+tr("км","km")}?:stepSourceShort(steps.source),m.clickable(onClick=openSteps).testTag("stepsCard"))},
            {m->Cell(tr("Пульс","Pulse"),pulse?.toString()?:"—",if(pulse!=null)pulseColor else c.onSurface,if(pulse!=null)tr("средний, уд/мин","average, bpm") else tr("Нет данных","No data"),m.testTag("watchPulse"))},
            {m->Cell(tr("Сон","Sleep"),asleep?.let{hoursMinutes(it)}?:"—",c.onSurface,if(asleep!=null)tr("за ночь","that night") else tr("Нет записи","No record"),m.testTag("watchSleepDay"))})
        if(LocalDensity.current.fontScale>LARGE_TYPE||tightLayout())Column(Modifier.border(1.dp,c.outline)){cells.forEachIndexed{i,cell->if(i>0)HorizontalDivider(color=c.outline);cell(Modifier.fillMaxWidth())}}
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(1.dp,c.outline)){cells.forEachIndexed{i,cell->if(i>0)VerticalDivider(color=c.outline);cell(Modifier.weight(1f).fillMaxHeight())}}
        if(!connected) {
            Row(Modifier.fillMaxWidth().border(1.dp,c.outlineVariant).clickable(onClick=openWatch).padding(12.dp).testTag("connectWatch"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.WatchOff,null,tint=c.onSurfaceVariant)
                Column(Modifier.weight(1f)){Text(tr("Часы не подключены","No watch connected"),style=MaterialTheme.typography.titleSmall)
                    Text(if(state.status==WatchStatus.UNSUPPORTED)tr("Health Connect на этом телефоне недоступен","Health Connect is not available on this phone") else tr("Пульс, сон и тренировки появятся после подключения Health Connect","Pulse, sleep and workouts appear once Health Connect is connected"),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
                Icon(Icons.Outlined.ChevronRight,null)
            }
        } else {
            val sessions=data?.workoutsOn(date,zone).orEmpty().sortedBy{it.start}
            if(sessions.isEmpty())Text(tr("За этот день тренировок с часов нет","No watch workouts that day"),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            sessions.forEach{w->Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("watchWorkoutRow"),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(pulseColor))
                Column(Modifier.weight(1f).padding(start=10.dp,top=4.dp,bottom=4.dp)){
                    Text(w.title?:watchActivityLabel(w.activity),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(w.start.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))+" · "+w.source,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment=Alignment.End){
                    Text(hoursMinutes(w.minutes),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Bold)
                    w.avgHeartRate?.let{Text("$it ${tr("уд/мин","bpm")}",style=MaterialTheme.typography.bodySmall,color=pulseColor)}
                }
            }}
        }
    }
}
@Composable private fun Cell(label:String,value:String,valueColor:Color,sub:String,modifier:Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=valueColor,maxLines=1)
        Text(sub,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
    }
}

/**
 * Seven square bars with weekday labels and the period; a day without entries is an empty frame, a logged zero a thin
 * line. Then the average, and "Day balance ›" with fat, carbohydrate and fibre in one line.
 */
@Composable private fun WeekSection(snapshot:UserSnapshot,date:String,openDetails:()->Unit) {
    val week=snapshot.week(date);val summary=snapshot.day(date).summary;val c=MaterialTheme.colorScheme
    Column(Modifier.testTag("weekCard"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){ArcadeLabel(tr("Последние 7 дней","Last 7 days"),Modifier.weight(1f));Text(period(week.first().date,week.last().date),style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)}
        val peak=week.maxOf{it.summary.kcal.value}.coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth().height(104.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
            week.forEach{d->
                val logged=isLogged(d);val kcal=d.summary.kcal.value;val sel=d.date==date
                Column(Modifier.weight(1f).semantics{contentDescription="${d.date}: ${if(logged)number(kcal)+" "+KCAL else tr("нет записей","no entries")}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    val bar=Modifier.fillMaxWidth()
                    when {
                        !logged->Box(bar.height(8.dp).border(1.dp,c.outline))
                        kcal<=0->Box(bar.height(3.dp).background(c.primary))
                        else->Box(bar.height((4+72*kcal/peak).dp).background(if(sel)c.primary else c.primary.copy(alpha=0.45f)))
                    }
                    Text(weekday(d.date),style=MaterialTheme.typography.labelSmall,color=if(sel)c.primary else c.onSurfaceVariant,fontWeight=if(sel)FontWeight.Bold else FontWeight.Normal,maxLines=1)
                }
            }
        }
        val counted=averageDays(week);val partial=week.count(::isLogged)-counted.size
        Text((weeklyAverage(week)?.let{tr("В среднем ","Average ")+"${number(it)} $KCAL · "+count(counted.size,"день с данными","дня с данными","дней с данными","day with data","days with data")}?:tr("Пока нет данных для среднего","No data for an average yet"))+
            if(partial>0)tr(" · без $partial дн. с неизвестной калорийностью"," · excluding $partial days with unknown calories") else "",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,modifier=Modifier.testTag("weekAverage"))
        val line="${tr("Ж","F")} ${total(summary.fat)} · ${tr("У","C")} ${total(summary.carbs)} · ${tr("Кл","Fb")} ${total(summary.fiber)}".replace(" $G ·"," ·")
        if(tightLayout())Column(Modifier.fillMaxWidth().clickable(onClick=openDetails).testTag("balanceRow"),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text(tr("Баланс за день","Day balance"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp))}
            Text(line,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
        } else Row(Modifier.fillMaxWidth().clickable(onClick=openDetails).padding(vertical=4.dp).testTag("balanceRow"),verticalAlignment=Alignment.CenterVertically) {
            Text(tr("Баланс за день","Day balance"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp))
            Spacer(Modifier.weight(1f))
            Text(line,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=8.dp))
        }
    }
}

/** "+ Entry": every kind of entry from one place — food, a glass of water, weight, an exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EntrySheet(glassMl:Int,close:()->Unit,add:()->Unit,water:()->Unit,weight:()->Unit,exercise:()->Unit) {
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),shape=RectangleShape) {
        Column(Modifier.padding(horizontal=16.dp).padding(bottom=24.dp).testTag("entrySheet"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ArcadeLabel(tr("Новая запись","New entry"))
            listOf(Triple(Icons.Outlined.Restaurant,tr("Еда","Food"),add),Triple(Icons.Outlined.WaterDrop,tr("Вода","Water")+" · +$glassMl $ML",water),
                Triple(Icons.Outlined.MonitorWeight,tr("Вес","Weight"),weight),Triple(Icons.Outlined.FitnessCenter,tr("Упражнение","Exercise"),exercise)).forEachIndexed{i,(icon,label,go)->
                Row(Modifier.fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outlineVariant).clickable{close();go()}.heightIn(min=52.dp).padding(horizontal=12.dp).testTag("entryOption-$i"),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(label,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)
                }
            }
        }
    }
}
