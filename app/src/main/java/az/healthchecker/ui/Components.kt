package az.healthchecker.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import java.util.Locale
val RU:Locale=Locale.forLanguageTag("ru")
// Editable form value: keeps full precision so re-saving an unchanged field does not round it.
fun plain(value:Double):String = if(value%1.0==0.0&&value<1e15)value.toLong().toString() else value.toString().replace('.',',')
fun plural(n:Int,one:String,few:String,many:String):String = "$n "+when{n%100 in 11..14->many;n%10==1->one;n%10 in 2..4->few;else->many}
fun number(value:Double?,decimals:Int=0):String = value?.let{String.format(RU,"%.${decimals}f",it)}?:"—"
fun quantityLabel(q:Quantity,grams:Double):String = when(q.unit){Measure.GRAMS->"${number(grams)} г";Measure.ML->"${number(q.value)} мл";Measure.PIECES->"${plain(q.value)} шт · ${number(grams)} г"}
fun muscleLabel(m:Muscle):String = when(m){Muscle.LEGS->"Ноги";Muscle.GLUTES->"Ягодицы";Muscle.CHEST->"Грудь";Muscle.BACK->"Спина";Muscle.SHOULDERS->"Плечи";Muscle.ARMS->"Руки";Muscle.CORE->"Пресс и кор"}
fun kindLabel(k:ExerciseKind):String = when(k){ExerciseKind.STRENGTH->"Силовое";ExerciseKind.CARDIO->"Кардио";ExerciseKind.MOBILITY->"Растяжка и мобильность"}
fun levelLabel(level:Int):String = listOf("Новичок","Средний","Продвинутый")[level.coerceIn(1,3)-1]
fun workoutLabel(w:WorkoutEntry):String = listOfNotNull(
    w.sets?.let{s->w.reps?.let{"$s × $it"}?:w.seconds?.let{"$s × $it с"}},w.loadKg?.let{"${plain(it)} кг"},"${number(w.minutes)} мин",w.kcal?.let{"≈ ${number(it)} ккал"}).joinToString(" · ")
@Composable fun SectionTitle(title:String,subtitle:String?=null) {
    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
    subtitle?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
