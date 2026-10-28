package az.healthchecker.ui.training
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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

/** Today's suggested session; the training tab and the workout screen share it, so both show the same set. */
@Composable fun rememberTodayPlan(vm:AppViewModel,snapshot:UserSnapshot,date:String,variant:Int):Recommendation? {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val prefs=snapshot.trainingPrefs?:TrainingPrefs()
    return remember(exercises,snapshot.workouts,date,prefs,variant){if(exercises.isEmpty())null else recommend(exercises,snapshot,date,prefs,variant)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TrainingScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,plan:Recommendation?,add:()->Unit,start:()->Unit,edit:(WorkoutEntry)->Unit,openProfile:()->Unit,videos:()->Unit) {
    val prefs=snapshot.trainingPrefs?:TrainingPrefs()
    val day=snapshot.training(date)
    val weight=snapshot.bodyWeight(date)
    LazyColumn(Modifier.fillMaxSize().testTag("trainingList"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{Panel {
            Text(tr("Тренировка за день","Today's training"),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=day.trained==true,onClick={vm.markTraining(date,if(day.mark==true)null else true)},label={Text(tr("Была тренировка","Trained"))},leadingIcon={Icon(Icons.Outlined.FitnessCenter,null)})
                FilterChip(selected=day.trained==false,enabled=day.workouts.isEmpty(),onClick={vm.markTraining(date,if(day.mark==false)null else false)},label={Text(tr("Не было","Rest day"))},leadingIcon={Icon(Icons.Outlined.Bedtime,null)})
            }
            if(day.workouts.isNotEmpty())Text(tr("Отмечено по выполненным упражнениям","Marked from the logged exercises"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${tr("Израсходовано","Burned")} ≈ ${number(day.burned.value)}${if(!day.burned.complete)"+" else ""} $KCAL",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
            if(weight==null)Text(tr("Укажите вес в профиле, чтобы считать калории упражнений","Enter your weight in the profile to estimate exercise calories"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.clickable(onClick=openProfile))
        }}
        item{Button(onClick=add,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("addExercise")){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(tr("Добавить упражнение","Add exercise"))}}
        if(day.workouts.isNotEmpty()){
            item{SectionTitle(tr("Выполненные упражнения","Completed exercises"),count(day.workouts.size,"упражнение","упражнения","упражнений","exercise","exercises"))}
            items(day.workouts,key={it.id}){w->Panel {
                Row(Modifier.fillMaxWidth().clickable{edit(w)},verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(vm.workoutName(w),fontWeight=FontWeight.SemiBold);Text(workoutLabel(w),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={vm.deleteWorkout(w)}){Icon(Icons.Outlined.DeleteOutline,tr("Удалить ","Delete ")+vm.workoutName(w))}
                }
            }}
        }
        item{Panel(Modifier.clickable(onClick=videos).testTag("openVideos")){
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Outlined.SmartDisplay,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){
                    Text(tr("Видеотренировки","Workout videos"),style=MaterialTheme.typography.titleMedium)
                    Text(tr("YouTube или свой файл: что делать сейчас и вся тренировка в дневник одной кнопкой","YouTube or your own file: what to do now, and the whole workout to the diary in one tap"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight,null)
            }
        }}
        plan?.let{p->
            item{Panel {
                SectionTitle(tr("Рекомендации на сегодня","Today's suggestions"),if(p.recovery)tr("Мышцы ещё восстанавливаются после последних тренировок — лёгкий день","Muscles are still recovering from recent training — an easy day") else tr("Акцент: ","Focus: ")+p.focus.distinct().joinToString(", "){muscleLabel(it).lowercase(Lang.locale)})
                Text("${tr("Дома","At home")}, ${if(prefs.dumbbells)tr("с гантелями и без","with and without dumbbells") else tr("без инвентаря","no equipment")} · ${tr("уровень","level")}: ${levelLabel(prefs.level).lowercase(Lang.locale)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){p.items.forEach{r->ExerciseThumb(vm,r.exercise.id,56.dp)}}
                val done=p.items.count{r->day.workouts.any{it.exerciseId==r.exercise.id}}
                val minutes=p.items.sumOf{it.prescription.minutesFor(it.exercise)?:0.0}
                Text(listOfNotNull(count(p.items.size,"упражнение","упражнения","упражнений","exercise","exercises"),"≈ ${number(minutes)} ${tr("мин","min")}",if(done>0)tr("выполнено ","done ")+"$done" else null).joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
                if(done>0)LinearProgressIndicator(progress={done.toFloat()/p.items.size},modifier=Modifier.fillMaxWidth())
                Button(onClick=start,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("startWorkout")){
                    Icon(Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(8.dp))
                    Text(when{done==0->tr("Начать тренировку","Start the workout");done<p.items.size->tr("Продолжить тренировку","Continue the workout");else->tr("Открыть тренировку","Open the workout")})
                }
                Text(tr("Картинки движений, таймер отдыха и секундомер — на экране тренировки","Movement pictures, a rest timer and the stopwatch are on the workout screen"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
            item{Panel {
                SectionTitle(tr("За 7 дней","Last 7 days"),tr("Ориентир ВОЗ для взрослых","WHO guideline for adults"))
                val aerobic=p.weekly.aerobicMinutes
                Text("${tr("Аэробная нагрузка","Aerobic activity")}: ${number(aerobic)} ${tr("из","of")} ${number(WHO_AEROBIC_MINUTES)} ${tr("мин","min")}")
                LinearProgressIndicator(progress={(aerobic/WHO_AEROBIC_MINUTES).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth())
                Text("${tr("Силовые дни","Strength days")}: ${p.weekly.strengthDays} ${tr("из","of")} $WHO_STRENGTH_DAYS")
                LinearProgressIndicator(progress={(p.weekly.strengthDays.toFloat()/WHO_STRENGTH_DAYS).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                Text(if(aerobic>=WHO_AEROBIC_MINUTES&&p.weekly.strengthDays>=WHO_STRENGTH_DAYS)tr("Недельный ориентир выполнен — дальше можно поддерживать нагрузку.","The weekly guideline is met — keep the activity up.") else tr("Интенсивные минуты (MET 6 и выше) считаются вдвойне. Учитываются кардио-упражнения.","Vigorous minutes (MET 6 and above) count double. Cardio exercises count."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
        }
        item{Panel {
            SectionTitle(tr("Настройки тренировок","Training settings"))
            Text(tr("Инвентарь","Equipment"),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=!prefs.dumbbells,onClick={vm.trainingPrefs(prefs.copy(dumbbells=false))},label={Text(tr("Без инвентаря","No equipment"))})
                FilterChip(selected=prefs.dumbbells,onClick={vm.trainingPrefs(prefs.copy(dumbbells=true))},label={Text(tr("Есть гантели","I have dumbbells"))})
            }
            Text(tr("Уровень","Level"),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..3).forEach{l->FilterChip(selected=prefs.level==l,onClick={vm.trainingPrefs(prefs.copy(level=l))},label={Text(levelLabel(l))})}}
        }}
        item{Text(tr("Калории упражнений — оценка: MET из Compendium of Physical Activities 2024 × вес тела × время. Включает расход в покое и не прибавляется к цели по еде. Шаги считаются отдельно на обзоре. При боли, заболеваниях сердца, суставов или беременности посоветуйтесь с врачом.",
            "Exercise calories are an estimate: MET from the 2024 Compendium of Physical Activities × body weight × time. They include resting energy and are not added to the food goal. Steps are counted separately on the overview. With pain, heart or joint conditions, or pregnancy, ask a doctor first."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

fun prescriptionLabel(p:Prescription):String = p.minutes?.let{"${number(it)} ${tr("мин","min")}"}?:p.sets?.let{s->p.reps?.let{"$s × $it"}?:p.seconds?.let{"$s × $it ${tr("с","s")}"}}?:""
