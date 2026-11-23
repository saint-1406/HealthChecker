package az.healthchecker.ui.watch
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.steps.stepsText
import az.healthchecker.watch.HealthConnectSource
import az.healthchecker.watch.WatchStatus
import az.healthchecker.watch.privacyText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun watchActivityLabel(a:WatchActivity):String = when(a) {
    WatchActivity.WALK->tr("Ходьба","Walking");WatchActivity.RUN->tr("Бег","Running");WatchActivity.BIKE->tr("Велосипед","Cycling");WatchActivity.SWIM->tr("Плавание","Swimming")
    WatchActivity.STRENGTH->tr("Силовая","Strength");WatchActivity.YOGA->tr("Йога","Yoga");WatchActivity.PILATES->tr("Пилатес","Pilates");WatchActivity.STRETCH->tr("Растяжка","Stretching")
    WatchActivity.HIKE->tr("Поход","Hiking");WatchActivity.ELLIPTICAL->tr("Эллипс","Elliptical");WatchActivity.ROWING->tr("Гребля","Rowing");WatchActivity.HIIT->tr("Интервальная (HIIT)","HIIT")
    WatchActivity.OTHER->tr("Тренировка","Workout")
}
fun hoursMinutes(minutes:Int):String = if(minutes<60)"$minutes ${tr("мин","min")}" else "${minutes/60} ${tr("ч","h")} ${minutes%60} ${tr("мин","min")}"
private fun clock(i:Instant):String = i.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
/** Profile row subtitle: what is connected and where the data came from. */
fun watchLine(state:az.healthchecker.ui.WatchState):String = when(state.status) {
    WatchStatus.UNSUPPORTED->tr("Health Connect недоступен","Health Connect unavailable")
    WatchStatus.INSTALL,WatchStatus.UPDATE,WatchStatus.NOT_CONNECTED->tr("Не подключено · Mi Fitness, Health Connect","Not connected · Mi Fitness, Health Connect")
    WatchStatus.PARTIAL,WatchStatus.CONNECTED->state.data?.let{d->if(d.fromXiaomi)tr("Данные Mi Fitness получены","Mi Fitness data received") else if(d.isEmpty)tr("Подключено · данных пока нет","Connected · no data yet") else tr("Подключено · ","Connected · ")+d.sources.joinToString(", ")}?:tr("Подключено","Connected")
}

/**
 * Watch data through Health Connect: connecting, the Mi Fitness setup steps, and what came in — steps for 7 days,
 * sleep, workouts and today's pulse.
 */
@Composable fun WatchScreen(vm:AppViewModel,back:()->Unit) {
    val state by vm.watch.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var message by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){vm.refreshWatch()}
    val request=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()){granted->
        message=if(granted.isEmpty())tr("Доступ не выдан. Если окно больше не появляется, разрешите чтение в настройках Health Connect.","No access given. If the window no longer appears, allow reading in Health Connect's settings.") else null
        vm.refreshWatch()
    }
    fun start(intent:android.content.Intent){try{context.startActivity(intent)}catch(e:ActivityNotFoundException){message=tr("Не удалось открыть: нет подходящего приложения","Could not open: no app handles it")}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("watchScreen"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
        SectionTitle(tr("Часы и Health Connect","Watch and Health Connect"),tr("Шаги, сон, тренировки и пульс с часов Xiaomi, вес с умных весов — через Mi Fitness","Steps, sleep, workouts and pulse from a Xiaomi watch, weight from a smart scale — through Mi Fitness"))
        Panel {
            val connected=state.status==WatchStatus.CONNECTED||state.status==WatchStatus.PARTIAL
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(if(connected)Icons.Outlined.Watch else Icons.Outlined.WatchOff,null,tint=if(connected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(watchLine(state),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f).testTag("watchStatus"))
                if(state.loading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
            }
            when(state.status) {
                WatchStatus.UNSUPPORTED->Text(tr("Health Connect работает на Android 9 и новее. На этом телефоне шаги считает сам телефон или вводятся вручную.","Health Connect needs Android 9 or newer. On this phone steps are counted by the phone or entered by hand."),style=MaterialTheme.typography.bodyMedium)
                WatchStatus.INSTALL,WatchStatus.UPDATE->{
                    Text(if(state.status==WatchStatus.INSTALL)tr("На Android 13 и старше Health Connect ставится отдельным приложением из Google Play.","On Android 13 and older Health Connect is a separate app from Google Play.") else tr("Health Connect нужно обновить в Google Play.","Health Connect needs an update from Google Play."),style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={start(HealthConnectSource.installIntent())},modifier=Modifier.testTag("installHealthConnect")){Text(if(state.status==WatchStatus.INSTALL)tr("Установить Health Connect","Install Health Connect") else tr("Обновить Health Connect","Update Health Connect"))}
                }
                WatchStatus.NOT_CONNECTED,WatchStatus.PARTIAL->{
                    Text(if(state.status==WatchStatus.PARTIAL)tr("Разрешено не всё: часть данных не видна.","Not everything is allowed: some data is hidden.") else tr("Разрешите VitaCore читать шаги, сон, тренировки, пульс и вес.","Let VitaCore read steps, sleep, workouts, pulse and weight."),style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={try{request.launch(HealthConnectSource.permissions)}catch(e:ActivityNotFoundException){start(HealthConnectSource.settingsIntent())}},modifier=Modifier.testTag("connectHealthConnect")){Icon(Icons.Outlined.Link,null);Spacer(Modifier.width(8.dp));Text(tr("Подключить","Connect"))}
                }
                WatchStatus.CONNECTED->{}
            }
            state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            if(state.status!=WatchStatus.UNSUPPORTED&&state.status!=WatchStatus.INSTALL)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(connected)OutlinedButton(onClick={vm.refreshWatch()},enabled=!state.loading,modifier=Modifier.testTag("refreshWatch")){Text(tr("Обновить","Refresh"))}
                TextButton(onClick={start(HealthConnectSource.settingsIntent())}){Text(tr("Настройки Health Connect","Health Connect settings"))}
            }
        }
        val data=state.data
        if(data!=null&&!data.isEmpty)WatchPanels(data,state.weightsAdded) else MiFitnessSteps(data!=null)
        if(data!=null&&!data.isEmpty&&!data.fromXiaomi)MiFitnessSteps(true)
        Text(privacyText(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How to make Mi Fitness share the watch's data. */
@Composable private fun MiFitnessSteps(connected:Boolean) = Panel(Modifier.testTag("miFitnessSteps")) {
    Text(if(connected)tr("Данных Mi Fitness пока нет","No Mi Fitness data yet") else tr("Как подключить часы Xiaomi","How to connect a Xiaomi watch"),style=MaterialTheme.typography.titleMedium)
    listOf(
        tr("В Mi Fitness: Профиль → Сторонние данные → Health Connect. Включите синхронизацию и разрешите запись шагов, сна, тренировок и пульса.","In Mi Fitness: Profile → Third-party data → Health Connect. Turn on syncing and allow writing steps, sleep, workouts and heart rate."),
        tr("Синхронизируйте часы с Mi Fitness (потяните экран вниз в приложении).","Sync the watch with Mi Fitness (pull down in the app)."),
        if(connected)tr("Вернитесь сюда и нажмите «Обновить». В Health Connect → Данные и доступ → Шаги видно, пишет ли туда Mi Fitness.","Come back and tap Refresh. Health Connect → Data and access → Steps shows whether Mi Fitness writes there.")
        else tr("Здесь нажмите «Подключить» и разрешите VitaCore чтение.","Here, tap Connect and allow VitaCore to read."),
    ).forEachIndexed{i,t->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Text("${i+1}.",fontWeight=FontWeight.SemiBold);Text(t,style=MaterialTheme.typography.bodyMedium)}}
    Text(tr("Mi Fitness передаёт шаги, дистанцию, сон и тренировки с пульсом. Круглосуточный пульс и SpO₂ зависят от модели часов и версии Mi Fitness.","Mi Fitness shares steps, distance, sleep and workouts with heart rate. All-day pulse and SpO₂ depend on the watch model and Mi Fitness version."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun WatchPanels(data:WatchData,weightsAdded:Int) {
    val today=LocalDate.now();val days=(6 downTo 0).map{today.minusDays(it.toLong()).toString()}
    Panel(Modifier.testTag("watchSteps")) {
        Text(tr("Шаги","Steps"),style=MaterialTheme.typography.titleMedium)
        Text(stepsText(data.steps[today.toString()]?:0),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("watchStepsToday"))
        Text(tr("сегодня · ","today · ")+data.stepSources.joinToString(", ").ifEmpty{tr("нет источников","no sources")},color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        val max=(days.maxOf{data.steps[it]?:0}).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(120.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Bottom) {
            days.forEach{d->val s=data.steps[d]?:0
                Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                    Text(if(s>=1000)number(s/1000.0,if(s<10000)1 else 0)+tr("к","k") else "$s",style=MaterialTheme.typography.labelSmall,maxLines=1)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(0.75f*s/max+0.02f).background(if(d==today.toString())MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(topStart=6.dp,topEnd=6.dp)))
                    Text(weekday(d),style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center)
                }
            }
        }
        Text(tr("Это общий итог Health Connect: шаги часов и телефона не складываются, повторы убираются. Он заменяет подсчёт телефона на главном экране, а введённое вручную число по-прежнему важнее.","This is Health Connect's combined total: watch and phone steps are not added up, overlaps are removed. It replaces the phone's count on the home screen; a number you enter yourself still wins."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    data.heartToday?.let{h->Panel(Modifier.testTag("watchHeart")) {
        Text(tr("Пульс сегодня","Pulse today"),style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){listOf(tr("мин","min") to h.min,tr("средний","average") to h.avg,tr("макс","max") to h.max).forEach{(l,v)->Column{Text("$v",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("$l, ${tr("уд/мин","bpm")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        Text(data.heartSources.joinToString(", "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
    Panel(Modifier.testTag("watchSleep")) {
        Text(tr("Сон","Sleep"),style=MaterialTheme.typography.titleMedium)
        if(data.sleep.isEmpty())Text(tr("Ночей за 7 дней нет. Сон появляется после синхронизации часов утром.","No nights in 7 days. Sleep appears after the watch syncs in the morning."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        data.sleep.forEach{n->Row(Modifier.fillMaxWidth()){
            Text("${weekday(n.date())}, ${shortDate(n.date())}",modifier=Modifier.weight(1f))
            Column(horizontalAlignment=Alignment.End){Text(hoursMinutes(n.asleepMinutes),fontWeight=FontWeight.Medium);Text("${clock(n.start)}–${clock(n.end)} · ${n.source}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }}
    }
    Panel(Modifier.testTag("watchWeight")) {
        Text(tr("Вес с весов","Scale weight"),style=MaterialTheme.typography.titleMedium)
        if(data.weights.isEmpty())Text(tr("Взвешиваний за 30 дней нет. Умные весы Xiaomi передают вес через Mi Fitness, если в нём включена синхронизация с Health Connect.","No weigh-ins in 30 days. Xiaomi smart scales share weight through Mi Fitness when its Health Connect sync is on."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        data.weights.take(5).forEach{w->Row(Modifier.fillMaxWidth()){
            val d=w.time.atZone(ZoneId.systemDefault()).toLocalDate().toString()
            Text("${weekday(d)}, ${shortDate(d)}",modifier=Modifier.weight(1f))
            Column(horizontalAlignment=Alignment.End){Text(number(w.kg,1)+" "+tr("кг","kg"),fontWeight=FontWeight.Medium);Text("${clock(w.time)} · ${w.source}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }}
        if(weightsAdded>0)Text(tr("Добавлено в журнал веса: $weightsAdded","Added to the weight log: $weightsAdded"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("weightsAdded"))
        Text(tr("Первое взвешивание дня само попадает в журнал веса — по нему считаются тренд и расход энергии. Если вы уже записали вес за этот день вручную, остаётся ваш. Удалённое из журнала больше не вернётся.","The first weigh-in of a day goes to the weight log by itself — the trend and the energy expenditure use it. A weight you entered for that day yourself stays. One you delete from the log does not come back."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Panel(Modifier.testTag("watchWorkouts")) {
        Text(tr("Тренировки с часов","Watch workouts"),style=MaterialTheme.typography.titleMedium)
        if(data.workouts.isEmpty())Text(tr("За 7 дней тренировок нет.","No workouts in 7 days."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        data.workouts.forEach{w->Row(Modifier.fillMaxWidth()){
            Column(Modifier.weight(1f)){Text(w.title?:watchActivityLabel(w.activity),fontWeight=FontWeight.Medium);Text("${weekday(w.date())}, ${shortDate(w.date())} · ${clock(w.start)} · ${w.source}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Column(horizontalAlignment=Alignment.End){Text(hoursMinutes(w.minutes));w.avgHeartRate?.let{Text("♥ $it ${tr("уд/мин","bpm")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        }}
    }
}
