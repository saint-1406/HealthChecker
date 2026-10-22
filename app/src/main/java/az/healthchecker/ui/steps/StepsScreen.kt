package az.healthchecker.ui.steps
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.LocalDate

fun stepsText(n:Int):String = String.format(Lang.locale,"%,d",n)
/** "≈ 412 ккал · 7,5 км", or a hint when the profile lacks height and weight. */
fun stepsEnergy(steps:Int,profile:Profile?):String = profile?.let{p->"≈ ${number(stepKcal(steps,p))} $KCAL · ${number(stepDistanceKm(steps,p),1)} ${tr("км","km")}"}?:tr("Укажите рост и вес в профиле, чтобы считать калории","Enter height and weight in the profile to estimate calories")

@Composable fun StepsScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,back:()->Unit) {
    val tracking by vm.stepTracking.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val day=snapshot.stepsOn(date);val profile=snapshot.profile
    var manual by rememberSaveable(date,day.manual){mutableStateOf(day.manual?.toString()?:"")}
    var message by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){vm.refreshStepTracking()}
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){granted->
        if(Build.VERSION.SDK_INT<29||granted[Manifest.permission.ACTIVITY_RECOGNITION]==true){vm.setStepTracking(true);message=null}
        else message=tr("Без разрешения «Физическая активность» телефон не отдаёт шаги. Его можно включить в настройках приложения.","Without the “Physical activity” permission the phone does not share steps. You can allow it in the app settings.")
    }
    fun enable() {
        val wanted=buildList{if(Build.VERSION.SDK_INT>=29)add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.POST_NOTIFICATIONS)}
        // Android answers at once, without a dialog, for permissions already granted.
        if(wanted.isEmpty())vm.setStepTracking(true) else permissions.launch(wanted.toTypedArray())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
        SectionTitle(tr("Шаги","Steps"),if(date==LocalDate.now().toString())tr("Сегодня · ","Today · ")+dayMonth(date) else dayMonth(date,true))
        Panel {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.primary)
                Column{Text(stepsText(day.steps),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("stepsTotal"))
                    Text(if(day.manual!=null)tr("введено вручную","entered by you") else if(day.sensor>0)tr("посчитано телефоном","counted by the phone") else tr("шагов","steps"),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Text(stepsEnergy(day.steps,profile),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("stepsKcal"))
            if(day.manual!=null&&day.sensor>0)Text(tr("Телефон насчитал ","The phone counted ")+stepsText(day.sensor),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            Text(tr("Ввести шаги вручную","Enter steps yourself"),style=MaterialTheme.typography.titleMedium)
            Text(tr("Например, из другого приложения или часов. Введённое число заменяет подсчёт телефона за этот день.","For example from another app or a watch. The number replaces the phone's count for this day."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(manual,{manual=it.filter(Char::isDigit).take(6)},label={Text(tr("Шагов за день","Steps for the day"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("manualSteps"))
            val value=manual.toIntOrNull()
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={vm.setManualSteps(date,value)},enabled=value!=null&&value<=MAX_STEPS_PER_DAY&&!busy,modifier=Modifier.testTag("saveSteps")){Text(tr("Сохранить","Save"))}
                if(day.manual!=null)TextButton(onClick={vm.setManualSteps(date,null)},enabled=!busy){Text(tr("Вернуть подсчёт телефона","Use the phone's count"))}
            }
        }
        Panel {
            Text(tr("Считать шаги автоматически","Count steps automatically"),style=MaterialTheme.typography.titleMedium)
            if(!vm.stepSensor)Text(tr("В этом телефоне нет датчика шагов — вводите шаги вручную.","This phone has no step sensor — enter steps yourself."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
            else {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(if(tracking)tr("Включено","On") else tr("Выключено","Off"),modifier=Modifier.weight(1f))
                    Switch(checked=tracking,onCheckedChange={if(it)enable() else vm.setStepTracking(false)},modifier=Modifier.testTag("stepTracking"))
                }
                Text(tr("Работает как шагомер: телефон считает шаги встроенным датчиком без интернета и почти без расхода батареи. Пока подсчёт включён, в шторке висит тихое уведомление — без него Android не даёт приложениям получать шаги в фоне. Считаются шаги с момента включения.",
                    "Works like a pedometer: the phone counts steps with its built-in sensor, offline and with almost no battery use. While counting is on, a quiet notification stays in the shade — without it Android does not give apps steps in the background. Steps count from the moment you switch it on."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
        Panel {
            Text(tr("Последние 7 дней","Last 7 days"),style=MaterialTheme.typography.titleMedium)
            (6 downTo 0).map{LocalDate.parse(date).minusDays(it.toLong()).toString()}.forEach{d->val s=snapshot.stepsOn(d).steps
                Row(Modifier.fillMaxWidth()){Text("${weekday(d)}, ${shortDate(d)}",modifier=Modifier.weight(1f));Text(stepsText(s),fontWeight=FontWeight.Medium);profile?.takeIf{s>0}?.let{p->Text("  ≈ ${number(stepKcal(s,p))} $KCAL",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            }
        }
        Text(tr("Калории — оценка для ходьбы в среднем темпе: 3,8 MET (Compendium 2024, код 17190) × вес × время; длина шага ≈ 0,415 × рост (у женщин 0,413), скорость 5 км/ч. Это общий расход за время ходьбы, он показывается отдельно и не меняет цель по еде.",
            "Calories are an estimate for walking at a moderate pace: 3.8 MET (Compendium 2024, code 17190) × weight × time; step length ≈ 0.415 × height (0.413 for women) at 5 km/h. It is the total energy used while walking, shown separately and not added to the food goal."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
