package az.healthchecker.ui.dashboard
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.diary.MealDraftCard
import az.healthchecker.ui.steps.stepsText
import az.healthchecker.ui.watch.hoursMinutes
import az.healthchecker.ui.watch.watchActivityLabel
import az.healthchecker.watch.WatchStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Your day": the VitaCore workout, food and water, weight, the watch's day, then the last 7 days with the day's balance.
 * The date header and the bottom bar stay put (HealthApp's scaffold); the cards scroll between them.
 */
@Composable fun DashboardScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,backupError:String?,add:()->Unit,water:()->Unit,edit:(DiaryEntry)->Unit,openProfile:()->Unit,openTraining:()->Unit,openSteps:()->Unit,openProtein:()->Unit,openDiary:()->Unit,openDraft:()->Unit,chooseFolder:()->Unit,openExpenditure:()->Unit={},openWatch:()->Unit={}) {
    val day=snapshot.day(date)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val draft by vm.mealDraft.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    var details by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(top=4.dp,bottom=20.dp).testTag("overviewList"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(backupError!=null)Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.fillMaxWidth().clickable(onClick=openProfile)){
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Outlined.SyncProblem,null);Column{Text(tr("Резервная копия не обновляется","The backup is not updating"),fontWeight=FontWeight.SemiBold);Text(tr("Откройте профиль, чтобы повторить или выбрать папку заново","Open the profile to retry or choose the folder again"),style=MaterialTheme.typography.bodySmall)}}
        }
        if(settings.overviewCards.training)ActivityCard(snapshot.training(date),openTraining)
        NutritionCard(day,settings.waterMl,{details=true},add,water,openProtein)
        draft?.let{MealDraftCard(it,openDraft)}
        if(photos.isNotEmpty())OverviewCard(Modifier.clickable(onClick=openDiary).testTag("photosWaiting")){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(Icons.Outlined.PhotoCamera,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){Text(count(photos.size,"фото ждёт внесения","фото ждут внесения","фото ждут внесения","photo to log","photos to log"),style=MaterialTheme.typography.titleMedium);Text(tr("Откройте «Дневник» → «Фото на потом»","Open Diary → Photos for later"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Icon(Icons.Outlined.ChevronRight,null)
            }
        }
        // Only for today: the estimate is about the current weeks, not a past date.
        if(date==LocalDate.now().toString())az.healthchecker.ui.profile.ExpenditureCard(vm,snapshot,openExpenditure)
        if(settings.overviewCards.weight)WeightCard(vm,snapshot,date)
        if(settings.overviewCards.steps)WatchCard(vm,snapshot,date,openSteps,openWatch)
        WeekCard(snapshot,date){details=true}
        BackupSetupCard(vm,snapshot,chooseFolder)
    }
    if(details)EnergySheet(vm,day){details=false}
}

/** The overview's card: the theme's surface, a hairline border, 16 dp inside. [accent] tints it with the main colour. */
@Composable fun OverviewCard(modifier:Modifier=Modifier,accent:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=if(accent)c.primary.copy(alpha=0.14f).compositeOver(c.surface) else c.surface,
        border=BorderStroke(1.dp,if(accent)c.primary.copy(alpha=0.45f) else c.outlineVariant.copy(alpha=0.45f))) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable private fun CardTitle(icon:ImageVector,title:String,chevron:Boolean=false,modifier:Modifier=Modifier) =
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
        Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
        if(chevron)Icon(Icons.Outlined.ChevronRight,null,modifier=Modifier.size(20.dp))
    }
private val LARGE_TYPE=1.3f
/** True when the screen holds little text per line (a narrow phone, a large system font or both): rows then stack. */
@Composable fun tightLayout():Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp/LocalDensity.current.fontScale<300f

// ── 1. The VitaCore workout ─────────────────────────────────────────────────────────────────────────────────────
/** What was trained in VitaCore on the day: done with its energy and number of exercises, a rest day, or not marked. */
@Composable private fun ActivityCard(training:TrainingDay,open:()->Unit) {
    OverviewCard(Modifier.clickable(onClick=open).testTag("trainingCard"),accent=training.workouts.isNotEmpty()) {
        val title=when{
            training.workouts.isNotEmpty()->tr("Тренировка завершена","Workout done")
            training.trained==true->tr("Тренировка отмечена","Workout marked")
            training.trained==false->tr("День отдыха","Rest day")
            else->tr("Тренировки пока нет","No workout yet")
        }
        CardTitle(if(training.trained==false)Icons.Outlined.Bedtime else Icons.Outlined.FitnessCenter,title)
        if(training.workouts.isNotEmpty()){
            val kcal=@Composable{m:Modifier->Text("≈${number(training.burned.value)}${if(!training.burned.complete)"+" else ""} $KCAL",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=m.testTag("trainingKcal"))}
            val exercises=@Composable{m:Modifier->Text(count(training.workouts.size,"упражнение","упражнения","упражнений","exercise","exercises"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=m)}
            if(tightLayout())Column{kcal(Modifier);exercises(Modifier)}
            else Row(Modifier.fillMaxWidth()){kcal(Modifier.weight(1f));exercises(Modifier.weight(1f))}
        }
        else Text(when(training.trained){true->tr("Упражнения не записаны — откройте «Тренировки»","No exercises logged — open Training");false->tr("Отдых тоже часть плана","Rest is part of the plan too")
            null->tr("Рекомендации на сегодня — во вкладке «Тренировки»","Today's suggestions are on the Training tab")},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── 2. Food and water ───────────────────────────────────────────────────────────────────────────────────────────
/** Calories against the goal (tap: the day's energy), protein, then water with "+ glass" and "Log food" on one line. */
@Composable private fun NutritionCard(day:DayState,glassMl:Int,openDetails:()->Unit,add:()->Unit,water:()->Unit,openProtein:()->Unit) {
    val s=day.summary;val goals=day.goals
    OverviewCard(Modifier.testTag("nutritionCard")) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=openDetails).semantics{contentDescription=tr("Энергия за день: подробнее","Energy for the day: details")}.testTag("energyCard"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val kcal=@Composable{Text("${number(s.kcal.value)}${if(!s.kcal.complete)"+" else ""}"+(goals?.kcal?.let{" / ${number(it)}"}?:"")+" $KCAL",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("kcalLine"))}
            if(tightLayout()){CardTitle(Icons.Outlined.Restaurant,tr("Питание","Food"),chevron=true);kcal()}
            else Row(verticalAlignment=Alignment.CenterVertically) {
                CardTitle(Icons.Outlined.Restaurant,tr("Питание","Food"),chevron=true,modifier=Modifier.weight(1f))
                kcal()
            }
            LinearProgressIndicator(progress={goals?.kcal?.let{(s.kcal.value/it).coerceIn(0.0,1.0).toFloat()}?:0f},modifier=Modifier.fillMaxWidth().height(6.dp),drawStopIndicator={},gapSize=0.dp,
                strokeCap=androidx.compose.ui.graphics.StrokeCap.Round)
            if(goals?.kcal==null)Text(tr("Цель по калориям не задана — её можно задать в профиле","No calorie goal — set one in the profile"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick=openProtein).testTag("proteinCard"),verticalAlignment=Alignment.CenterVertically) {
            Text(tr("Белок ","Protein ")+"${number(s.protein.value,1)}${if(!s.protein.complete)"+" else ""}"+(goals?.protein?.let{" "+tr("из","of")+" ${number(it)}"}?:"")+" $G",
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight,tr("Белок подробнее","Protein details"),tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(18.dp))
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A phone too narrow (or text too large) for one line puts the two buttons under the water.
            val oneLine=maxWidth>=330.dp&&LocalDensity.current.fontScale<=LARGE_TYPE
            val waterInfo=@Composable{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Icon(Icons.Outlined.WaterDrop,null,tint=MaterialTheme.colorScheme.primary)
                Column{Text(tr("Вода","Water"),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                    Text("${number(s.waterMl)}"+(goals?.waterMl?.let{" / ${number(it)}"}?:"")+" $ML",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("waterLine"))}
            }}
            val waterButton=@Composable{m:Modifier->FilledTonalButton(onClick=water,contentPadding=PaddingValues(horizontal=12.dp),modifier=m.heightIn(min=44.dp).testTag("waterButton")){Text("+$glassMl $ML",maxLines=1)}}
            val foodButton=@Composable{m:Modifier->Button(onClick=add,contentPadding=PaddingValues(horizontal=14.dp),modifier=m.heightIn(min=44.dp).testTag("logFoodButton")){Icon(Icons.Outlined.Add,tr("Добавить еду","Add food"),Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(tr("Записать еду","Log food"),maxLines=1)}}
            if(oneLine)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){waterInfo()};waterButton(Modifier);foodButton(Modifier)}
            else if(tightLayout())Column(verticalArrangement=Arrangement.spacedBy(8.dp)){waterInfo();waterButton(Modifier.fillMaxWidth());foodButton(Modifier.fillMaxWidth())}
            else Column(verticalArrangement=Arrangement.spacedBy(8.dp)){waterInfo();Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){waterButton(Modifier.weight(1f));foodButton(Modifier.weight(1.4f))}}
        }
    }
}

// ── 4. The watch's day ─────────────────────────────────────────────────────────────────────────────────────────
/**
 * Steps (manual beats the watch beats the phone), the day's average pulse and sleep in three columns, then the watch's
 * workouts of the day. Without Health Connect the steps still show and a line offers to connect.
 */
@Composable private fun WatchCard(vm:AppViewModel,snapshot:UserSnapshot,date:String,openSteps:()->Unit,openWatch:()->Unit) {
    val state by vm.watch.collectAsStateWithLifecycle()
    val today=LocalDate.now().toString()
    val data=state.data
    val connected=state.status==WatchStatus.CONNECTED||state.status==WatchStatus.PARTIAL
    val steps=snapshot.stepsOn(date,data)
    val zone=ZoneId.systemDefault()
    OverviewCard(Modifier.testTag("watchCard")) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(date==today)tr("Часы сегодня","Watch today") else tr("Часы","Watch")+" · "+dayMonth(date),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).testTag("watchTitle"),maxLines=2,overflow=TextOverflow.Ellipsis)
            if(state.loading)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp) else Text("Health Connect",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,modifier=Modifier.padding(start=8.dp).clickable(onClick=openWatch))
        }
        val pulse=data?.heartOn(date,today)
        val asleep=data?.asleepOn(date,zone)
        val columns=listOf<@Composable (Modifier)->Unit>(
            {m->WatchMetric(Icons.AutoMirrored.Outlined.DirectionsWalk,tr("Шаги","Steps"),stepsText(steps.steps),
                snapshot.profile?.takeIf{steps.steps>0}?.let{"≈"+number(stepDistanceKm(steps.steps,it),1)+" "+tr("км","km")}?:stepSourceShort(steps.source),m.clickable(onClick=openSteps).testTag("stepsCard"))},
            {m->WatchMetric(Icons.Outlined.FavoriteBorder,tr("Пульс","Pulse"),pulse?.toString()?:"—",if(pulse!=null)tr("средний, уд/мин","average, bpm") else tr("Нет данных","No data"),m.testTag("watchPulse"))},
            {m->WatchMetric(Icons.Outlined.Bedtime,tr("Сон","Sleep"),asleep?.let{hoursMinutes(it)}?:"—",if(asleep!=null)tr("за ночь","that night") else tr("Нет записи","No record"),m.testTag("watchSleepDay"))},
        )
        if(LocalDensity.current.fontScale>LARGE_TYPE)Column(verticalArrangement=Arrangement.spacedBy(8.dp)){columns.forEach{it(Modifier.fillMaxWidth())}}
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            columns.forEachIndexed{i,c->
                if(i>0)VerticalDivider(Modifier.padding(horizontal=10.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                c(Modifier.weight(1f))
            }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
        if(!connected) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick=openWatch).testTag("connectWatch"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.WatchOff,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)){Text(tr("Часы не подключены","No watch connected"),style=MaterialTheme.typography.titleSmall)
                    Text(if(state.status==WatchStatus.UNSUPPORTED)tr("Health Connect на этом телефоне недоступен","Health Connect is not available on this phone") else tr("Пульс, сон и тренировки появятся после подключения Health Connect","Pulse, sleep and workouts appear once Health Connect is connected"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Icon(Icons.Outlined.ChevronRight,null)
            }
        } else {
            val sessions=data?.workoutsOn(date,zone).orEmpty().sortedBy{it.start}
            Text(tr("Тренировки с часов","Watch workouts"),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            if(sessions.isEmpty())Text(tr("За этот день тренировок с часов нет","No watch workouts that day"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            sessions.forEach{w->Row(Modifier.fillMaxWidth().testTag("watchWorkoutRow"),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){
                    Text(w.title?:watchActivityLabel(w.activity),style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(w.start.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))+" · "+w.source,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment=Alignment.End){
                    Text(hoursMinutes(w.minutes),style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold)
                    w.avgHeartRate?.let{Text("$it ${tr("уд/мин","bpm")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
                }
            }}
        }
    }
}
private fun stepSourceShort(s:StepSource):String = when(s){StepSource.MANUAL->tr("введено вручную","entered by hand");StepSource.WATCH->tr("с часов","from the watch");StepSource.PHONE->tr("телефон","phone");StepSource.NONE->tr("нет данных","no data")}
@Composable private fun WatchMetric(icon:ImageVector,label:String,value:String,sub:String,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp));Text(label,style=MaterialTheme.typography.labelLarge,maxLines=1)}
        Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,maxLines=1)
        Text(sub,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
    }
}

// ── 5. The last 7 days and the day's balance ───────────────────────────────────────────────────────────────────
/**
 * Seven bars of calories: a day without entries is an empty outline, a logged day of 0 kcal a thin solid line. Below,
 * the selected day's fat, carbohydrate and fibre; "Day balance ›" opens the same sheet as the food card.
 */
@Composable private fun WeekCard(snapshot:UserSnapshot,date:String,openDetails:()->Unit) {
    val week=snapshot.week(date);val summary=snapshot.day(date).summary
    val c=MaterialTheme.colorScheme
    OverviewCard(Modifier.testTag("weekCard")) {
        Row(verticalAlignment=Alignment.CenterVertically){
            Text(tr("Последние 7 дней","Last 7 days"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            Text(period(week.first().date,week.last().date),style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)
        }
        Text(tr("Калории","Calories"),style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)
        val peak=week.maxOf{it.summary.kcal.value}.coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth().height(120.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom) {
            week.forEach{d->
                val logged=isLogged(d);val kcal=d.summary.kcal.value
                Column(Modifier.weight(1f).semantics{contentDescription="${d.date}: ${if(logged)number(kcal)+" "+KCAL else tr("нет записей","no entries")}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    val shape=RoundedCornerShape(topStart=6.dp,topEnd=6.dp,bottomStart=2.dp,bottomEnd=2.dp)
                    val bar=Modifier.fillMaxWidth(0.7f)
                    when {
                        !logged->Box(bar.height(10.dp).clip(shape).border(1.dp,c.outlineVariant,shape))
                        kcal<=0->Box(bar.height(3.dp).clip(shape).background(c.primary))
                        else->Box(bar.height((6+80*kcal/peak).dp).clip(shape).background(if(d.date==date)c.primary else c.primary.copy(alpha=0.45f)))
                    }
                    Text(weekday(d.date),style=MaterialTheme.typography.labelSmall,color=if(d.date==date)c.primary else c.onSurfaceVariant,fontWeight=if(d.date==date)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        val counted=averageDays(week);val partial=week.count(::isLogged)-counted.size
        Text((weeklyAverage(week)?.let{tr("В среднем ","Average ")+"${number(it)} $KCAL · "+count(counted.size,"день с данными","дня с данными","дней с данными","day with data","days with data")}?:tr("Пока нет данных для среднего","No data for an average yet"))+
            if(partial>0)tr(" · без $partial дн. с неизвестной калорийностью"," · excluding $partial days with unknown calories") else "",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,modifier=Modifier.testTag("weekAverage"))
        HorizontalDivider(color=c.outlineVariant.copy(alpha=0.5f))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=openDetails).testTag("balanceRow"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(tr("Баланс за день","Day balance"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Icon(Icons.Outlined.ChevronRight,null,modifier=Modifier.size(20.dp))}
            val parts=listOf(tr("Жиры","Fat") to summary.fat,tr("Углеводы","Carbs") to summary.carbs,tr("Клетчатка","Fibre") to summary.fiber)
            if(tightLayout())parts.forEach{(label,t)->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(label,style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant,modifier=Modifier.weight(1f));Text(total(t),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}}
            else Row(Modifier.fillMaxWidth()) {
                parts.forEach{(label,t)->
                    Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant);Text(total(t),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}
                }
            }
        }
    }
}
/** "21–27 нояб." within a month, "28 нояб. – 4 дек." across two. */
private fun period(from:String,to:String):String {
    val a=LocalDate.parse(from);val b=LocalDate.parse(to)
    return if(a.month==b.month)"${a.dayOfMonth}–"+shortDate(to) else shortDate(from)+" – "+shortDate(to)
}

/** "12,5 г", with "+" when part of the day is unknown. */
private fun total(t:NutrientTotal):String = "${number(t.value,1)}${if(!t.complete)"+" else ""} $G"

/** Day totals of every nutrient and the same numbers for each entry separately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EnergySheet(vm:AppViewModel,day:DayState,close:()->Unit) {
    val s=day.summary;val goal=day.goals?.kcal
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=28.dp).testTag("energySheet"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){
                    Text(tr("Энергия за день","Energy for the day"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                    Text(dayMonth(day.date,true),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=close,modifier=Modifier.testTag("closeEnergy")){Icon(Icons.Outlined.Close,tr("Закрыть","Close"))}
            }
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text("${number(s.kcal.value)}${if(!s.kcal.complete)"+" else ""} $KCAL",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
                Text(goal?.let{g->"${tr("из","of")} ${number(g)} $KCAL · "+if(s.kcal.value>g)tr("выше цели на ","above by ")+"${number(s.kcal.value-g)} $KCAL" else tr("осталось ","left ")+"${number(g-s.kcal.value)} $KCAL"}?:tr("Цель не задана","No goal set"),
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Nutrient(tr("Белки","Protein"),s.protein,Modifier.weight(1f));Nutrient(tr("Жиры","Fat"),s.fat,Modifier.weight(1f))}
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Nutrient(tr("Углеводы","Carbs"),s.carbs,Modifier.weight(1f));Nutrient(tr("Клетчатка","Fibre"),s.fiber,Modifier.weight(1f))}
            Text(tr("Клетчатка входит в углеводы. «+» — у части записей значение неизвестно.","Fibre is part of carbohydrate. A “+” means some entries have an unknown value."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(tr("По записям","By entry"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(day.entries.isEmpty())Text(tr("За этот день записей нет","No entries for this day"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            day.entries.forEachIndexed{i,e->
                if(i>0)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
                    Row{Text(vm.entryName(e),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text("${number(e.nutrients.kcal)} $KCAL",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)}
                    Text(quantityLabel(e.quantity,e.grams),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(macrosLine(e.nutrients),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
@Composable private fun Nutrient(title:String,t:NutrientTotal,modifier:Modifier) {
    Surface(modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.padding(14.dp)){Text(title,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium);Text(total(t),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)}}
}
