package az.healthchecker.ui.profile
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.LocalDate
import az.healthchecker.ui.kit.Button

/** "≈ 2 550 ккал" — expenditure figures are rounded to 10 kcal: more digits would pretend a precision there is not. */
fun kcal10(v:Double):String = number(Math.round(v/10.0)*10.0)+" "+KCAL
fun weeklyKg(v:Double):String = (if(v>0)"+" else if(v<0)"−" else "")+number(kotlin.math.abs(v),2)+" "+tr("кг/нед","kg/wk")

/** Profile → Energy expenditure: the adaptive estimate, how it was reached, and a new calorie target to apply. */
@Composable fun ExpenditureScreen(vm:AppViewModel,back:()->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    val today=LocalDate.now().toString()
    val e=remember(snapshot,today){expenditure(snapshot,today)}
    val current=snapshot.day(today).goals?.kcal
    val suggested=remember(e,snapshot){adaptiveTarget(e,snapshot)}
    ProfilePage(tr("Расход энергии","Energy expenditure"),tr("По вашему дневнику и весу","From your diary and weight"),back,"expenditureScreen") {
        Panel {
            Text(tr("Вы тратите в сутки","You spend per day"),style=MaterialTheme.typography.titleMedium)
            Text(e.estimate?.let{"≈ "+kcal10(it)}?:"—",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("expenditureEstimate"))
            Text(when{
                e.estimate==null->tr("Чтобы посчитать, укажите пол, возраст, рост, вес и активность в «Тело и активность».","To estimate it, enter sex, age, height, weight and activity under “Body and activity”.")
                e.observed==null->tr("Пока это расчёт по формуле: рост, вес, возраст и активность.","For now this is the formula: height, weight, age and activity.")
                e.confidence>=0.99->tr("По вашим данным за 4 недели.","From your own data over 4 weeks.")
                else->tr("Смесь формулы и ваших данных: ваши данные дают ${(e.confidence*100).toInt()} %. Через 3 недели записей и взвешиваний останутся только они.",
                    "A blend of the formula and your data: your data give ${(e.confidence*100).toInt()} %. After 3 weeks of logging and weighing only they count.")
            },style=MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress={e.confidence.toFloat()},modifier=Modifier.fillMaxWidth().testTag("expenditureConfidence"))
        }
        Panel(Modifier.testTag("expenditureDetails")) {
            Text(tr("Из чего складывается","How it adds up"),style=MaterialTheme.typography.titleMedium)
            Row2(tr("Съедали в среднем","Ate on average"),e.avgIntake?.let{kcal10(it)}?:"—")
            Row2(tr("Дней с записями","Logged days"),"${e.loggedDays} "+tr("из $EXPENDITURE_WINDOW_DAYS","of $EXPENDITURE_WINDOW_DAYS")+if(e.usedCompletedDays)tr(" (заполненные)"," (complete)") else "")
            Row2(tr("Вес по тренду","Weight trend"),e.trendPerWeek?.let(::weeklyKg)?:"—")
            Row2(tr("Взвешиваний за 4 недели","Weigh-ins in 4 weeks"),"${e.weighIns}")
            Row2(tr("По данным","From data"),e.observed?.let{"≈ "+kcal10(it)}?:"—")
            Row2(tr("По формуле","By formula"),e.formula?.let{"≈ "+kcal10(it)}?:"—")
            when(e.status) {
                ExpenditureStatus.NEED_FOOD->Hint(tr("Записывайте еду хотя бы $MIN_LOGGED_DAYS дней. Дни меньше ${MIN_DAY_KCAL.toInt()} ккал не учитываются — похоже, их записали не целиком. Отмечайте «День заполнен», и учитываться будут только такие дни.",
                    "Log food for at least $MIN_LOGGED_DAYS days. Days under ${MIN_DAY_KCAL.toInt()} kcal are left out — they look half-logged. Mark “Day complete” and only those days will count."))
                ExpenditureStatus.NEED_WEIGHT->Hint(tr("Взвешивайтесь утром хотя бы $MIN_WEIGH_INS раза за $MIN_WEIGH_SPAN_DAYS дней, лучше каждый день: скачки воды сглаживаются трендом.",
                    "Weigh yourself in the morning at least $MIN_WEIGH_INS times over $MIN_WEIGH_SPAN_DAYS days, daily is better: the trend smooths water swings.")+
                    tr(" Умные весы Xiaomi подключаются через Mi Fitness → «Часы и Health Connect» — вес будет записываться сам."," A Xiaomi smart scale connects through Mi Fitness → “Watch and Health Connect” — the weight is then logged by itself."))
                ExpenditureStatus.NEED_PROFILE->Hint(tr("Укажите пол, возраст, рост и активность в «Тело и активность».","Enter sex, age, height and activity under “Body and activity”."))
                ExpenditureStatus.OK->{}
            }
        }
        if(suggested!=null)Panel(Modifier.testTag("adaptiveTarget")) {
            Text(tr("Цель по калориям","Calorie target"),style=MaterialTheme.typography.titleMedium)
            val goal=snapshot.setup?.goal
            Text(if(goal==null)tr("Без программы — чтобы удерживать вес.","No program — to keep your weight.") else tr("Цель программы: ","Program goal: ")+goalLabel(goal).lowercase(Lang.locale),style=MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(current?.let{kcal10(it)}?:tr("не задана","not set"),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward,null)
                Text(kcal10(suggested),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("suggestedKcal"))
            }
            if(shouldSuggest(e,current,suggested))Button(onClick={vm.applyAdaptiveTarget(suggested)},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("applyAdaptive")){Text(tr("Применить новую цель","Apply the new target"))}
            else Text(if(e.observed==null||e.confidence<0.5)tr("Предложим новую цель, когда данных станет больше.","A new target is offered once there is more data.") else tr("Текущая цель уже подходит.","Your current target already fits."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Белок и жиры не меняются, углеводы пересчитываются под новые калории.","Protein and fat stay; carbohydrates follow the new calories."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(tr("Как считается: средние калории минус изменение веса по тренду × 7 700 ккал/кг. Тренд — сглаженный вес, без скачков воды и соли. Это оценка, а не медицинская консультация.",
            "How it works: average calories minus the weight trend change × 7 700 kcal/kg. The trend is a smoothed weight without water and salt swings. It is an estimate, not medical advice."),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Row2(label:String,value:String) = Row(Modifier.fillMaxWidth()){Text(label,modifier=Modifier.weight(1f));Text(value,fontWeight=FontWeight.Medium)}
@Composable private fun Hint(text:String) = Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)

/** Home card: shown when the data suggest a target at least 100 kcal away from the current one. */
@Composable fun ExpenditureCard(vm:AppViewModel,snapshot:UserSnapshot,open:()->Unit) {
    val today=LocalDate.now().toString()
    val e=remember(snapshot,today){expenditure(snapshot,today)}
    val suggested=remember(e,snapshot){adaptiveTarget(e,snapshot)}
    val current=snapshot.day(today).goals?.kcal
    val hidden by vm.expenditureCardHidden.collectAsStateWithLifecycle()
    if(hidden||!shouldSuggest(e,current,suggested))return
    Panel(Modifier.clickable(onClick=open).testTag("expenditureCard")) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.LocalFireDepartment,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(tr("Ваш расход ≈ ","You spend ≈ ")+kcal10(e.estimate!!),style=MaterialTheme.typography.titleMedium)
                Text(tr("Цель можно обновить: ","Update the target: ")+(current?.let{kcal10(it)}?:"—")+" → "+kcal10(suggested!!),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick={vm.hideExpenditureCard()}){Icon(Icons.Outlined.Close,tr("Скрыть на неделю","Hide for a week"))}
        }
    }
}
