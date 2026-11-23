package az.healthchecker.ui.profile
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.reminders.Reminders
import az.healthchecker.ui.*
import java.time.LocalTime

/** Profile row subtitle: which reminders are on. */
@Composable fun reminderLine():String {
    val s=Reminders.settings(LocalContext.current)
    val on=listOfNotNull(tr("лекарства","medicines").takeIf{s.medications},tr("вода","water").takeIf{s.water},tr("вес","weight").takeIf{s.weigh},tr("тренировки","training").takeIf{s.training},tr("дневник","diary").takeIf{s.log})
    return if(on.isEmpty())tr("Выключены","Off") else on.joinToString(", ").replaceFirstChar{it.titlecase(Lang.locale)}
}

/** Profile → Reminders: switches with their times. Saved at once; the next alarm is re-planned on every change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun RemindersScreen(vm:AppViewModel,back:()->Unit) {
    val context=LocalContext.current
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var s by remember{mutableStateOf(Reminders.settings(context))}
    var allowed by remember{mutableStateOf(Reminders.notificationsAllowed(context))}
    var error by remember{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf<ReminderSettings?>(null)}
    var unrestricted by remember{mutableStateOf(Reminders.batteryUnrestricted(context))}
    // Back from the system dialogs: show what was really allowed.
    LifecycleResumeEffect(Unit){unrestricted=Reminders.batteryUnrestricted(context);allowed=Reminders.notificationsAllowed(context);onPauseOrDispose{}}
    fun store(next:ReminderSettings){runCatching{Reminders.save(context,next)}.onSuccess{s=next;error=null}.onFailure{error=it.message}}
    val ask=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ok->allowed=ok;pending?.let{if(ok)store(it)};pending=null}
    // Switching anything on first asks for notifications (Android 13+); switching off never asks.
    fun update(next:ReminderSettings){
        if(next.anyOn&&!allowed&&Build.VERSION.SDK_INT>=33){pending=next;ask.launch(Manifest.permission.POST_NOTIFICATIONS)} else store(next)}
    ProfilePage(tr("Напоминания","Reminders"),tr("Уведомления на этом телефоне","Notifications on this phone"),back,"remindersScreen") {
        if(!allowed&&s.anyOn)Panel {
            Text(tr("Уведомления запрещены — напоминания не придут.","Notifications are blocked — reminders will not show."),color=MaterialTheme.colorScheme.error)
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text(tr("Открыть настройки уведомлений","Open notification settings"))}
        }
        if(s.anyOn&&(!unrestricted||Reminders.xiaomi))BatteryPanel(unrestricted)
        val meds=snapshot.medications.filter{it.activeOn(java.time.LocalDate.now().toString())}
        Section(tr("Лекарства и витамины","Medicines and vitamins"),s.medications,"remindMeds",{update(s.copy(medications=it))}) {
            val timed=meds.filter{it.times.isNotEmpty()}
            Text(if(timed.isEmpty())tr("Укажите время приёма в карточке препарата (Дневник → препарат), и в это время придёт напоминание с кнопкой «Принял».","Set dose times on a medicine's card (Diary → the medicine) to get a reminder with a “Taken” button.")
                else timed.joinToString("\n"){"${it.name}: ${it.times.sorted().joinToString(", ")}"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Если доза уже отмечена, напоминание не придёт.","No reminder when the dose is already marked."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section(tr("Вода","Water"),s.water,"remindWater",{update(s.copy(water=it))}) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                TimeField(tr("С","From"),s.waterFrom,"waterFrom"){update(s.copy(waterFrom=it))}
                TimeField(tr("До","To"),s.waterTo,"waterTo"){update(s.copy(waterTo=it))}
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(1,2,3,4).forEach{h->FilterChip(s.waterEveryHours==h,{update(s.copy(waterEveryHours=h))},label={Text(tr("каждые $h ч","every $h h"))})}}
            Text(tr("Кнопка с объёмом стакана в уведомлении сразу записывает воду. Когда цель по воде выполнена, напоминаний больше нет.","The glass button in the notification logs the water at once. Once the water goal is reached, reminders stop for the day."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section(tr("Утреннее взвешивание","Morning weigh-in"),s.weigh,"remindWeigh",{update(s.copy(weigh=it))}) {
            TimeField(tr("Время","Time"),s.weighAt,"weighAt"){update(s.copy(weighAt=it))}
            Text(tr("Регулярные взвешивания делают расход энергии точнее. Если вы уже взвесились, напоминания не будет.","Regular weigh-ins make the energy expenditure more accurate. No reminder if you have already weighed in."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section(tr("Тренировки","Training"),s.training,"remindTraining",{update(s.copy(training=it))}) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){(1..7).forEach{d->FilterChip(d in s.trainingDays,{update(s.copy(trainingDays=if(d in s.trainingDays)s.trainingDays-d else s.trainingDays+d))},label={Text(dayOfWeekShort(d))},modifier=Modifier.testTag("trainDay$d"))}}
            TimeField(tr("Время","Time"),s.trainingAt,"trainingAt"){update(s.copy(trainingAt=it))}
            snapshot.setup?.takeIf{it.appliedOn!=null}?.let{Text(tr("По программе: ${it.daysPerWeek} трен. в неделю.","Your program: ${it.daysPerWeek} sessions a week."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        }
        Section(tr("Записать еду вечером","Log food in the evening"),s.log,"remindLog",{update(s.copy(log=it))}) {
            TimeField(tr("Время","Time"),s.logAt,"logAt"){update(s.copy(logAt=it))}
            Text(tr("Не придёт, если день уже отмечен «День заполнен».","Skipped when the day is already marked complete."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Text(tr("Напоминания работают без интернета. Android может показать их чуть позже, обычно в пределах 10 минут, а в режиме сна телефона — до часа: так он бережёт батарею. Если телефон был выключен и напоминание опоздало больше чем на час с четвертью, оно не придёт.","Reminders work offline. Android may show them a little late, usually within 10 minutes, up to an hour while the phone sleeps, to save battery. A reminder more than 75 minutes late (the phone was off) is skipped."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Battery optimisation delays or drops reminders, Xiaomi's MIUI/HyperOS especially. Shown while it is on, and on Xiaomi
 * phones always, since their autostart switch cannot be read by the app.
 */
@Composable private fun BatteryPanel(unrestricted:Boolean) = Panel(Modifier.testTag("batteryPanel")) {
    val context=LocalContext.current
    if(!unrestricted) {
        Text(tr("Экономия батареи может задерживать напоминания","Battery saving can hold reminders back"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text(if(Reminders.xiaomi)tr("На телефонах Xiaomi, Redmi и POCO система может совсем не показать напоминание, пока приложение закрыто. Разрешите VitaCore работать в фоне: приложение не будет тратить батарею, оно просыпается только во время напоминаний.","On Xiaomi, Redmi and POCO phones the system may not show a reminder at all while the app is closed. Let VitaCore run in the background: it does not drain the battery, it only wakes up for reminders.")
            else tr("Разрешите VitaCore работать в фоне, чтобы напоминания приходили вовремя. Приложение просыпается только во время напоминаний.","Let VitaCore run in the background so reminders come on time. It only wakes up for reminders."),style=MaterialTheme.typography.bodyMedium)
        Button(onClick={Reminders.allowBackground(context)},modifier=Modifier.fillMaxWidth().testTag("allowBackground")){Text(tr("Разрешить работу в фоне","Allow running in the background"))}
    } else Text(tr("Работа в фоне разрешена.","Running in the background is allowed."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("batteryOk"))
    if(Reminders.xiaomi) {
        Text(tr("Ещё на Xiaomi: Безопасность → Разрешения → Автозапуск → включить VitaCore. Тогда напоминания вернутся и после перезагрузки.","Also on Xiaomi: Security → Permissions → Autostart → turn VitaCore on. Then reminders come back after a restart too."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={Reminders.openAutostart(context)},modifier=Modifier.testTag("openAutostart")){Text(tr("Открыть автозапуск","Open autostart"))}
    }
}

@Composable private fun Section(title:String,on:Boolean,tag:String,toggle:(Boolean)->Unit,content:@Composable ColumnScope.()->Unit) = Panel {
    Row(verticalAlignment=Alignment.CenterVertically){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Switch(on,toggle,modifier=Modifier.testTag(tag))}
    if(on)content()
}

/** A time chip that opens the system-style time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TimeField(label:String,value:String,tag:String,changed:(String)->Unit) {
    var open by remember{mutableStateOf(false)}
    AssistChip(onClick={open=true},label={Text("$label $value")},modifier=Modifier.testTag(tag))
    if(open){
        val t=LocalTime.parse(value);val state=rememberTimePickerState(t.hour,t.minute,is24Hour=true)
        AlertDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={open=false;changed("%02d:%02d".format(state.hour,state.minute))}){Text(tr("Готово","Done"))}},
            dismissButton={TextButton(onClick={open=false}){Text(tr("Отмена","Cancel"))}},text={TimePicker(state)})
    }
}
