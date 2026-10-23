package az.healthchecker.ui.dashboard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.steps.stepsEnergy
import az.healthchecker.ui.steps.stepsText

@Composable fun DashboardScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,backupError:String?,add:()->Unit,water:()->Unit,edit:(DiaryEntry)->Unit,openProfile:()->Unit,openTraining:()->Unit,openSteps:()->Unit) {
    val day=snapshot.day(date);val summary=day.summary;val goals=day.goals
    val green=Color(0xFF245C49);val mint=Color(0xFFCDF2CA)
    var details by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(backupError!=null)Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.fillMaxWidth().clickable(onClick=openProfile)){
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Outlined.SyncProblem,null);Column{Text(tr("Резервная копия не обновляется","The backup is not updating"),fontWeight=FontWeight.SemiBold);Text(tr("Откройте профиль, чтобы повторить или выбрать папку заново","Open the profile to retry or choose the folder again"),style=MaterialTheme.typography.bodySmall)}}
        }
        Surface(shape=RoundedCornerShape(28.dp),color=green,contentColor=Color.White,modifier=Modifier.clickable{details=true}.testTag("energyCard")) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){Text(tr("ЭНЕРГИЯ ЗА ДЕНЬ","ENERGY FOR THE DAY"),style=MaterialTheme.typography.labelMedium,color=mint,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,tr("Подробнее","Details"),tint=mint)}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(number(summary.kcal.value),fontSize=52.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-2).sp);Text("${tr("из","of")} ${number(goals?.kcal)} $KCAL",color=Color(0xFFC0D4C8));if(goals?.kcal==null)Text(tr("Задайте цель в профиле","Set a goal in the profile"),style=MaterialTheme.typography.bodySmall,color=mint)}
                    val progress=goals?.kcal?.let{(summary.kcal.value/it).coerceIn(0.0,1.0).toFloat()}?:0f
                    Box(Modifier.size(86.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){drawArc(Color(0xFF477762),-90f,360f,false,style=Stroke(8.dp.toPx(),cap=StrokeCap.Round));drawArc(mint,-90f,360f*progress,false,style=Stroke(8.dp.toPx(),cap=StrokeCap.Round))};Icon(Icons.Outlined.Restaurant,null,tint=mint,modifier=Modifier.size(28.dp))}
                }
                val target=goals?.kcal
                Text(if(target==null)tr("Каждая запись — чуть больше ясности","Every entry adds a bit of clarity") else if(summary.kcal.value>target)tr("Выше цели на ","Above the goal by ")+"${number(summary.kcal.value-target)} $KCAL" else tr("До цели ещё ","Left to the goal: ")+"${number(target-summary.kcal.value)} $KCAL",style=MaterialTheme.typography.bodyMedium,color=mint)
                Text("${tr("Ж","F")} ${total(summary.fat)} · ${tr("У","C")} ${total(summary.carbs)} · ${tr("Кл","Fb")} ${total(summary.fiber)} — ${tr("нажмите для подробностей","tap for details")}",style=MaterialTheme.typography.bodySmall,color=mint)
                if(!summary.kcal.complete)Text(tr("Сумма неполная: есть неизвестные значения","The total is incomplete: some values are unknown"),style=MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MetricCard(tr("Белок","Protein"),summary.protein.value,goals?.protein,G,Icons.Outlined.EggAlt,summary.protein.complete,Modifier.weight(1f))
            MetricCard(tr("Вода","Water"),summary.waterMl,goals?.waterMl,ML,Icons.Outlined.WaterDrop,true,Modifier.weight(1f))
        }
        WeightCard(vm,snapshot,date)
        val steps=snapshot.stepsOn(date)
        Panel(Modifier.clickable(onClick=openSteps).testTag("stepsCard")){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(Icons.AutoMirrored.Outlined.DirectionsWalk,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){
                    Text("${stepsText(steps.steps)} ${tr("шагов","steps")}",style=MaterialTheme.typography.titleMedium)
                    Text(if(steps.steps>0)stepsEnergy(steps.steps,snapshot.profile) else tr("Введите шаги или включите подсчёт","Enter steps or switch on counting"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight,null)
            }
        }
        val training=snapshot.training(date)
        Panel(Modifier.clickable(onClick=openTraining)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(Icons.Outlined.FitnessCenter,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){
                    Text(when(training.trained){true->tr("Тренировка была","Trained");false->tr("День отдыха","Rest day");null->tr("Тренировка не отмечена","Training not marked")},style=MaterialTheme.typography.titleMedium)
                    Text(if(training.workouts.isEmpty())tr("Откройте «Тренировки»: рекомендации на сегодня","Open “Training” for today's suggestions") else "${count(training.workouts.size,"упражнение","упражнения","упражнений","exercise","exercises")} · ≈ ${number(training.burned.value)}${if(!training.burned.complete)"+" else ""} $KCAL ${tr("израсходовано","burned")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight,null)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Button(onClick=add,modifier=Modifier.weight(1f).heightIn(min=50.dp)){Icon(Icons.Outlined.Add,null);Text(tr("Записать еду","Log food"))}
            FilledTonalButton(onClick=water,modifier=Modifier.heightIn(min=50.dp)){Icon(Icons.Outlined.WaterDrop,null);Text("+250 $ML")}
        }
        Panel {
            val start=java.time.LocalDate.parse(date).minusDays(6).toString()
            SectionTitle(tr("Последние 7 дней","Last 7 days"),"${tr("Калории","Calories")} · ${shortDate(start)} — ${shortDate(date)}")
            val week=snapshot.week(date);val peak=week.maxOf{it.summary.kcal.value}.coerceAtLeast(1.0)
            Row(Modifier.fillMaxWidth().height(140.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Bottom){
                week.forEach{d->val logged=isLogged(d);Column(Modifier.weight(1f).semantics{contentDescription="${d.date}: ${if(logged)number(d.summary.kcal.value)+" "+KCAL else tr("нет данных","no data")}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Box(Modifier.width(22.dp).height((if(logged)8+90*d.summary.kcal.value/peak else 5.0).dp).clip(RoundedCornerShape(8.dp)).background(if(!logged)MaterialTheme.colorScheme.surfaceVariant else if(d.date==date)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer))
                    Text(weekday(d.date),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }}
            }
            val counted=averageDays(week);val partial=week.count(::isLogged)-counted.size
            Text((weeklyAverage(week)?.let{tr("В среднем ","Average ")+"${number(it)} $KCAL · ${counted.size} ${tr("дн. с данными","days with data")}"}?:tr("Пока нет данных для среднего","No data for an average yet"))+if(partial>0)tr(" · без $partial дн. с неизвестной калорийностью"," · excluding $partial days with unknown calories") else "",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel{Text(tr("Баланс за день","Day balance"),style=MaterialTheme.typography.titleMedium);Text("${tr("Жиры","Fat")} ${total(summary.fat)}     ${tr("Углеводы","Carbs")} ${total(summary.carbs)}\n${tr("Клетчатка","Fibre")} ${total(summary.fiber)}");Text(tr("Вода — только выпитая питьевая вода. «+» у суммы означает, что часть значений неизвестна.","Water counts drinking water only. A “+” means some values are unknown."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(day.entries.isNotEmpty()){SectionTitle(tr("Последние записи","Latest entries"));day.entries.take(3).forEach{e->Panel(Modifier.clickable{edit(e)}){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(vm.entryName(e),modifier=Modifier.weight(1f));Text("${number(e.nutrients.kcal)} $KCAL",color=MaterialTheme.colorScheme.primary)}}}}
        Spacer(Modifier.height(20.dp))
    }
    if(details)EnergySheet(vm,day){details=false}
}
/** "12,5 г", with "+" when part of the day is unknown. */
private fun total(t:NutrientTotal):String = "${number(t.value,1)}${if(!t.complete)"+" else ""} $G"

/** Day totals of every nutrient and the same numbers for each entry separately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EnergySheet(vm:AppViewModel,day:DayState,close:()->Unit) {
    val s=day.summary
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp).testTag("energySheet"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            SectionTitle(tr("Энергия за день","Energy for the day"),dayMonth(day.date,true))
            Text("${number(s.kcal.value)}${if(!s.kcal.complete)"+" else ""} $KCAL",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Nutrient(tr("Белки","Protein"),s.protein,Modifier.weight(1f));Nutrient(tr("Жиры","Fat"),s.fat,Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Nutrient(tr("Углеводы","Carbs"),s.carbs,Modifier.weight(1f));Nutrient(tr("Клетчатка","Fibre"),s.fiber,Modifier.weight(1f))
            }
            Text(tr("Клетчатка входит в углеводы. «+» — у части записей значение неизвестно.","Fibre is part of carbohydrate. A “+” means some entries have an unknown value."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(tr("По записям","By entry"),style=MaterialTheme.typography.titleMedium)
            if(day.entries.isEmpty())Text(tr("За этот день записей нет","No entries for this day"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            day.entries.forEach{e->
                Column(Modifier.fillMaxWidth()){
                    Row{Text(vm.entryName(e),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text("${number(e.nutrients.kcal)} $KCAL",color=MaterialTheme.colorScheme.primary)}
                    Text(quantityLabel(e.quantity,e.grams),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(macrosLine(e.nutrients),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
@Composable private fun Nutrient(title:String,t:NutrientTotal,modifier:Modifier) {
    Surface(modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.padding(14.dp)){Text(title,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(total(t),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)}}
}
@Composable private fun MetricCard(title:String,value:Double,target:Double?,unit:String,icon:androidx.compose.ui.graphics.vector.ImageVector,complete:Boolean,modifier:Modifier) {
    Panel(modifier){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${number(value)}${if(!complete)"+" else ""} $unit",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text(if(target==null)tr("Цель не задана","No goal set") else "${tr("из","of")} ${number(target)} $unit",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
