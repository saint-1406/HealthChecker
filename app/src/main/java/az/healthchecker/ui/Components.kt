package az.healthchecker.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
// Editable form value: keeps full precision so re-saving an unchanged field does not round it.
fun plain(value:Double):String = if(value%1.0==0.0&&value<1e15)value.toLong().toString() else value.toString().replace('.',if(Lang.en)'.' else ',')
fun plural(n:Int,one:String,few:String,many:String):String = "$n "+when{n%100 in 11..14->many;n%10==1->one;n%10 in 2..4->few;else->many}
fun number(value:Double?,decimals:Int=0):String = value?.let{String.format(Lang.locale,"%.${decimals}f",it)}?:"—"
/** "19 октября" / "October 19", optionally with the year. */
fun dayMonth(date:String,year:Boolean=false):String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern(if(Lang.en)(if(year)"MMMM d, yyyy" else "MMMM d") else if(year)"d MMMM yyyy" else "d MMMM",Lang.locale))
fun shortDate(date:String):String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern(if(Lang.en)"MMM d" else "d MMM",Lang.locale))
fun weekday(date:String):String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EE",Lang.locale))
val G:String get() = tr("г","g")
val KCAL:String get() = tr("ккал","kcal")
val ML:String get() = tr("мл","ml")
fun quantityLabel(q:Quantity,grams:Double):String = when(q.unit){Measure.GRAMS->"${number(grams)} $G";Measure.ML->"${number(q.value)} $ML";Measure.PIECES->"${plain(q.value)} ${tr("шт","pcs")} · ${number(grams)} $G"}
/** "Б 12,6 · Ж 10,6 · У 1,1 · Кл 0,0 г" — every macro of an amount, unknown values as "—". */
fun macrosLine(n:Nutrients):String = "${tr("Б","P")} ${number(n.protein,1)} · ${tr("Ж","F")} ${number(n.fat,1)} · ${tr("У","C")} ${number(n.carbs,1)} · ${tr("Кл","Fb")} ${number(n.fiber,1)} $G"
fun muscleLabel(m:Muscle):String = when(m){Muscle.LEGS->tr("Ноги","Legs");Muscle.GLUTES->tr("Ягодицы","Glutes");Muscle.CHEST->tr("Грудь","Chest");Muscle.BACK->tr("Спина","Back");Muscle.SHOULDERS->tr("Плечи","Shoulders");Muscle.ARMS->tr("Руки","Arms");Muscle.CORE->tr("Пресс и кор","Abs and core")}
fun kindLabel(k:ExerciseKind):String = when(k){ExerciseKind.STRENGTH->tr("Силовое","Strength");ExerciseKind.CARDIO->tr("Кардио","Cardio");ExerciseKind.MOBILITY->tr("Растяжка и мобильность","Stretching and mobility")}
fun categoryLabel(c:ExerciseCategory):String = when(c){ExerciseCategory.WARMUP->tr("Разминка","Warm-up");ExerciseCategory.STRENGTH->tr("Силовые","Strength");ExerciseCategory.AEROBIC->tr("Аэробика","Aerobics");ExerciseCategory.ENDURANCE->tr("Выносливость","Endurance");ExerciseCategory.HIIT->tr("Интервальные (HIIT)","HIIT");ExerciseCategory.STRETCH->tr("Растяжка","Stretching");ExerciseCategory.YOGA->tr("Йога и пилатес","Yoga and Pilates");ExerciseCategory.BALANCE->tr("Баланс","Balance")}
fun levelLabel(level:Int):String = listOf(tr("Новичок","Beginner"),tr("Средний","Intermediate"),tr("Продвинутый","Advanced"))[level.coerceIn(1,3)-1]
fun workoutLabel(w:WorkoutEntry):String = listOfNotNull(
    w.sets?.let{s->w.reps?.let{"$s × $it"}?:w.seconds?.let{"$s × $it ${tr("с","s")}"}},w.loadKg?.let{"${plain(it)} ${tr("кг","kg")}"},"${number(w.minutes)} ${tr("мин","min")}",w.kcal?.let{"≈ ${number(it)} $KCAL"}).joinToString(" · ")
@Composable fun SectionTitle(title:String,subtitle:String?=null) {
    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
    subtitle?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
