package az.healthchecker.ui.dashboard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun DashboardScreen(snapshot:UserSnapshot,date:String,backupError:String?,add:()->Unit,water:()->Unit,edit:(DiaryEntry)->Unit,openProfile:()->Unit,openTraining:()->Unit) {
    val day=snapshot.day(date);val summary=day.summary;val goals=day.goals
    val green=Color(0xFF245C49);val mint=Color(0xFFCDF2CA)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(backupError!=null)Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.fillMaxWidth().clickable(onClick=openProfile)){
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Outlined.SyncProblem,null);Column{Text("Резервная копия не обновляется",fontWeight=FontWeight.SemiBold);Text("Откройте профиль, чтобы повторить или выбрать папку заново",style=MaterialTheme.typography.bodySmall)}}
        }
        Surface(shape=RoundedCornerShape(28.dp),color=green,contentColor=Color.White) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("ЭНЕРГИЯ ЗА ДЕНЬ",style=MaterialTheme.typography.labelMedium,color=mint)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(number(summary.kcal.value),fontSize=52.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-2).sp);Text("из ${number(goals?.kcal)} ккал",color=Color(0xFFC0D4C8));if(goals?.kcal==null)Text("Задайте цель в профиле",style=MaterialTheme.typography.bodySmall,color=mint)}
                    val progress=goals?.kcal?.let{(summary.kcal.value/it).coerceIn(0.0,1.0).toFloat()}?:0f
                    Box(Modifier.size(86.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){drawArc(Color(0xFF477762),-90f,360f,false,style=Stroke(8.dp.toPx(),cap=StrokeCap.Round));drawArc(mint,-90f,360f*progress,false,style=Stroke(8.dp.toPx(),cap=StrokeCap.Round))};Icon(Icons.Outlined.Restaurant,null,tint=mint,modifier=Modifier.size(28.dp))}
                }
                val target=goals?.kcal
                Text(if(target==null)"Каждая запись — чуть больше ясности" else if(summary.kcal.value>target)"Выше цели на ${number(summary.kcal.value-target)} ккал" else "До цели ещё ${number(target-summary.kcal.value)} ккал",style=MaterialTheme.typography.bodyMedium,color=mint)
                if(!summary.kcal.complete)Text("Сумма неполная: есть неизвестные значения",style=MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MetricCard("Белок",summary.protein.value,goals?.protein,"г",Icons.Outlined.EggAlt,summary.protein.complete,Modifier.weight(1f))
            MetricCard("Вода",summary.waterMl,goals?.waterMl,"мл",Icons.Outlined.WaterDrop,true,Modifier.weight(1f))
        }
        val training=snapshot.training(date)
        Panel(Modifier.clickable(onClick=openTraining)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(Icons.Outlined.FitnessCenter,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){
                    Text(when(training.trained){true->"Тренировка была";false->"День отдыха";null->"Тренировка не отмечена"},style=MaterialTheme.typography.titleMedium)
                    Text(if(training.workouts.isEmpty())"Откройте «Тренировки»: рекомендации на сегодня" else "${plural(training.workouts.size,"упражнение","упражнения","упражнений")} · ≈ ${number(training.burned.value)}${if(!training.burned.complete)"+" else ""} ккал израсходовано",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight,null)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Button(onClick=add,modifier=Modifier.weight(1f).heightIn(min=50.dp)){Icon(Icons.Outlined.Add,null);Text("Записать еду")}
            FilledTonalButton(onClick=water,modifier=Modifier.heightIn(min=50.dp)){Icon(Icons.Outlined.WaterDrop,null);Text("+250 мл")}
        }
        Panel {
            SectionTitle("Последние 7 дней","Калории · ${LocalDate.parse(date).minusDays(6).format(DateTimeFormatter.ofPattern("d MMM",RU))} — ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM",RU))}")
            val week=snapshot.week(date);val peak=week.maxOf{it.summary.kcal.value}.coerceAtLeast(1.0)
            Row(Modifier.fillMaxWidth().height(140.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Bottom){
                week.forEach{d->val logged=isLogged(d);Column(Modifier.weight(1f).semantics{contentDescription="${d.date}: ${if(logged)number(d.summary.kcal.value)+" ккал" else "нет данных"}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Box(Modifier.width(22.dp).height((if(logged)8+90*d.summary.kcal.value/peak else 5.0).dp).clip(RoundedCornerShape(8.dp)).background(if(!logged)MaterialTheme.colorScheme.surfaceVariant else if(d.date==date)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer))
                    Text(LocalDate.parse(d.date).format(DateTimeFormatter.ofPattern("EE",RU)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }}
            }
            val counted=averageDays(week);val partial=week.count(::isLogged)-counted.size
            Text((weeklyAverage(week)?.let{"В среднем ${number(it)} ккал · ${counted.size} дн. с данными"}?:"Пока нет данных для среднего")+if(partial>0)" · без $partial дн. с неизвестной калорийностью" else "",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel{Text("Баланс за день",style=MaterialTheme.typography.titleMedium);Text("Жиры ${number(summary.fat.value,1)}${if(!summary.fat.complete)"+" else ""} г     Углеводы ${number(summary.carbs.value,1)}${if(!summary.carbs.complete)"+" else ""} г");Text("Вода — только выпитая питьевая вода. «+» у суммы означает, что часть значений неизвестна.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(day.entries.isNotEmpty()){SectionTitle("Последние записи");day.entries.take(3).forEach{e->Panel(Modifier.clickable{edit(e)}){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(e.name,modifier=Modifier.weight(1f));Text("${number(e.nutrients.kcal)} ккал",color=MaterialTheme.colorScheme.primary)}}}}
        Spacer(Modifier.height(20.dp))
    }
}
@Composable private fun MetricCard(title:String,value:Double,target:Double?,unit:String,icon:androidx.compose.ui.graphics.vector.ImageVector,complete:Boolean,modifier:Modifier) {
    Panel(modifier){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${number(value)}${if(!complete)"+" else ""} $unit",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text(if(target==null)"Цель не задана"else"из ${number(target)} $unit",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
