package az.healthchecker.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
// Editable form value: keeps full precision so re-saving an unchanged field does not round it.
fun plain(value:Double):String = if(value%1.0==0.0&&value<1e15)value.toLong().toString() else value.toString().replace('.',if(Lang.en)'.' else ',')
fun number(value:Double?,decimals:Int=0):String = value?.let{String.format(Lang.locale,"%.${decimals}f",it)}?:"—"
/** "19 октября" / "October 19", optionally with the year. */
fun dayMonth(date:String,year:Boolean=false):String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern(if(Lang.en)(if(year)"MMMM d, yyyy" else "MMMM d") else if(year)"d MMMM yyyy" else "d MMMM",Lang.locale))
fun shortDate(date:String):String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern(if(Lang.en)"MMM d" else "d MMM",Lang.locale))
fun shortDayMonth(date:String):String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern(if(Lang.en)"EEE, MMM d" else "EE, d MMM",Lang.locale))
fun weekday(date:String):String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EE",Lang.locale))
val G:String get() = tr("г","g")
val KCAL:String get() = tr("ккал","kcal")
val ML:String get() = tr("мл","ml")
fun quantityLabel(q:Quantity,grams:Double):String = when(q.unit){Measure.GRAMS->"${number(grams)} $G";Measure.ML->"${number(q.value)} $ML";Measure.PIECES->"${plain(q.value)} ${tr("шт","pcs")} · ${number(grams)} $G"}
/** "Б 12,6 · Ж 10,6 · У 1,1 · Кл 0,0 г" — every macro of an amount, unknown values as "—". */
fun macrosLine(n:Nutrients):String = "${tr("Б","P")} ${number(n.protein,1)} · ${tr("Ж","F")} ${number(n.fat,1)} · ${tr("У","C")} ${number(n.carbs,1)} · ${tr("Кл","Fb")} ${number(n.fiber,1)} $G"
fun muscleLabel(m:Muscle):String = when(m){Muscle.LEGS->tr("Ноги","Legs");Muscle.GLUTES->tr("Ягодицы","Glutes");Muscle.CHEST->tr("Грудь","Chest");Muscle.BACK->tr("Спина","Back");Muscle.SHOULDERS->tr("Плечи","Shoulders");Muscle.ARMS->tr("Руки","Arms");Muscle.CORE->tr("Пресс и кор","Abs and core")}
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

/** The "day | favorites" switch at the top of Diary and Training; [count] shows how many favorites there are. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DayFavoritesTabs(favorites:Boolean,count:Int,dayLabel:String,onChange:(Boolean)->Unit) {
    // A slim two-way switch instead of a full-height tab row: the day's content starts right under it.
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)) {
        SegmentedButton(selected=!favorites,onClick={onChange(false)},shape=SegmentedButtonDefaults.itemShape(0,2),modifier=Modifier.height(38.dp).testTag("tabDay"),
            icon={}){Text(dayLabel,style=MaterialTheme.typography.labelLarge)}
        SegmentedButton(selected=favorites,onClick={onChange(true)},shape=SegmentedButtonDefaults.itemShape(1,2),modifier=Modifier.height(38.dp).testTag("tabFavorites"),
            icon={Icon(Icons.Outlined.Star,null,Modifier.size(16.dp))}){Text(tr("Избранное","Favorites")+if(count>0)" · $count" else "",style=MaterialTheme.typography.labelLarge,maxLines=1)}
    }
}

/** A star that adds to or removes from favorites; [name] goes into the spoken label. */
@Composable fun FavoriteStar(on:Boolean,name:String,enabled:Boolean=true,toggle:()->Unit) {
    IconButton(onClick=toggle,enabled=enabled,modifier=Modifier.testTag(if(on)"unstar" else "star")) {
        Icon(if(on)Icons.Outlined.Star else Icons.Outlined.StarBorder,
            (if(on)tr("Убрать из избранного: ","Remove favorite: ") else tr("В избранное: ","Favorite: "))+name,
            tint=if(on)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A notice that can be flicked away in any direction: it follows the finger, fades as it goes, and past [limit] it
 * leaves and [dismiss] runs; a shorter drag springs back. Buttons inside still work: a drag starts only after the
 * touch slop. [key] restarts the position for every new notice.
 */
@Composable fun SwipeAway(key:Any,dismiss:()->Unit,modifier:Modifier=Modifier,limit:androidx.compose.ui.unit.Dp=80.dp,content:@Composable ()->Unit) {
    androidx.compose.runtime.key(key) {
        val offset=remember{androidx.compose.animation.core.Animatable(androidx.compose.ui.geometry.Offset.Zero,androidx.compose.ui.geometry.Offset.VectorConverter)}
        val scope=rememberCoroutineScope()
        val limitPx=with(androidx.compose.ui.platform.LocalDensity.current){limit.toPx()}
        Box(modifier
            .offset{androidx.compose.ui.unit.IntOffset(offset.value.x.toInt(),offset.value.y.toInt())}
            .graphicsLayer{alpha=1f-(offset.value.getDistance()/(limitPx*2.5f)).coerceIn(0f,0.8f)}
            .pointerInput(Unit){
                detectDragGestures(
                    onDragEnd={scope.launch{
                        val d=offset.value.getDistance()
                        if(d>limitPx){offset.animateTo(offset.value*(limitPx*4/d),androidx.compose.animation.core.tween(160));dismiss()}
                        else offset.animateTo(androidx.compose.ui.geometry.Offset.Zero,androidx.compose.animation.core.spring())
                    }},
                    onDragCancel={scope.launch{offset.animateTo(androidx.compose.ui.geometry.Offset.Zero)}}
                ){change,drag->change.consume();scope.launch{offset.snapTo(offset.value+drag)}}
            }
            .testTag("noticeSwipe")){content()}
    }
}
