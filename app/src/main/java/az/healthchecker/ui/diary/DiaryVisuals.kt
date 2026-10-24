package az.healthchecker.ui.diary
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One colour per macronutrient across the diary: protein the theme's main colour, fat its accent, carbohydrate amber. */
data class MacroColors(val protein:Color,val fat:Color,val carbs:Color)
@Composable fun macroColors():MacroColors {
    val s=MaterialTheme.colorScheme;val dark=s.background.luminance()<0.3f
    return MacroColors(s.primary,s.tertiary,if(dark)Color(0xFFF2C46B) else Color(0xFFC98A12))
}

/** Energy from protein, fat and carbohydrate (4, 9 and 4 kcal per gram) as one bar; empty when nothing is known. */
@Composable fun MacroBar(n:Nutrients,modifier:Modifier=Modifier,height:Dp=8.dp) {
    val c=macroColors();val track=MaterialTheme.colorScheme.surfaceVariant
    val parts=listOf((n.protein?:0.0)*4 to c.protein,(n.fat?:0.0)*9 to c.fat,(n.carbs?:0.0)*4 to c.carbs)
    val total=parts.sumOf{it.first}
    Canvas(modifier.height(height).clip(RoundedCornerShape(height/2))) {
        drawRect(track)
        if(total>0){var x=0f;parts.forEach{(kcal,color)->val w=(kcal/total).toFloat()*size.width;drawRect(color,topLeft=androidx.compose.ui.geometry.Offset(x,0f),size=androidx.compose.ui.geometry.Size(w,size.height));x+=w}}
    }
}

/** Diary header: the day's calories against the goal as a ring, the energy split and every macronutrient. */
@Composable fun DaySummaryCard(day:DayState) {
    val s=day.summary;val goal=day.goals?.kcal;val c=macroColors()
    val ring=MaterialTheme.colorScheme.primary;val track=MaterialTheme.colorScheme.surfaceVariant
    Panel(Modifier.testTag("diarySummary")) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("ЗА ДЕНЬ","FOR THE DAY"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Text("${number(s.kcal.value)}${if(!s.kcal.complete)"+" else ""} $KCAL",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold)
                Text(goal?.let{g->"${tr("из","of")} ${number(g)} · "+if(s.kcal.value>g)tr("выше цели на ","above by ")+number(s.kcal.value-g) else tr("осталось ","left ")+number(g-s.kcal.value)}?:tr("Цель не задана","No goal set"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val progress=goal?.let{(s.kcal.value/it).coerceIn(0.0,1.0).toFloat()}?:0f
            Box(Modifier.size(64.dp).semantics{contentDescription=tr("Выполнено ","Done ")+"${(progress*100).toInt()}%"},contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()){val w=7.dp.toPx();drawArc(track,-90f,360f,false,style=Stroke(w,cap=StrokeCap.Round));drawArc(ring,-90f,360f*progress,false,style=Stroke(w,cap=StrokeCap.Round))}
                Text(if(goal==null)"—" else "${(progress*100).toInt()}%",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
            }
        }
        MacroBar(Nutrients(s.kcal.value,s.protein.value,s.fat.value,s.carbs.value),Modifier.fillMaxWidth(),10.dp)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Legend(tr("Белки","Protein"),s.protein,c.protein,Modifier.weight(1f))
            Legend(tr("Жиры","Fat"),s.fat,c.fat,Modifier.weight(1f))
            Legend(tr("Углеводы","Carbs"),s.carbs,c.carbs,Modifier.weight(1f))
        }
        Text("${tr("Клетчатка","Fibre")} ${number(s.fiber.value,1)}${if(!s.fiber.complete)"+" else ""} $G · ${tr("Вода","Water")} ${number(s.waterMl)} $ML",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Legend(name:String,t:NutrientTotal,color:Color,modifier:Modifier) {
    Column(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(CircleShape).background(color));Spacer(Modifier.width(6.dp));Text(name,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Text("${number(t.value,1)}${if(!t.complete)"+" else ""} $G",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
    }
}

/** Round badge before an entry: a drop for water, a plate for the user's dishes, otherwise the food's first letter. */
@Composable fun EntryBadge(name:String,water:Boolean,dish:Boolean) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
        when {
            water->Icon(Icons.Outlined.WaterDrop,null,tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(20.dp))
            dish->Icon(Icons.Outlined.RestaurantMenu,null,tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(20.dp))
            else->Text(name.trim().take(1).uppercase(Lang.locale),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** "13:05" of when an entry was logged, in the zone it was logged in. */
fun entryTime(e:DiaryEntry):String = runCatching{Instant.parse(e.instant).atZone(ZoneId.of(e.zoneId)).format(DateTimeFormatter.ofPattern("HH:mm"))}.getOrDefault("")
