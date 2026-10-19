package az.healthchecker.ui.training
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TrainingScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,add:()->Unit,log:(Exercise,Prescription?)->Unit,edit:(WorkoutEntry)->Unit,openProfile:()->Unit) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val prefs=snapshot.trainingPrefs?:TrainingPrefs()
    val day=snapshot.training(date)
    val weight=snapshot.profile?.currentWeight
    var variant by rememberSaveable(date){mutableIntStateOf(0)}
    val plan=remember(exercises,snapshot.workouts,date,prefs,variant){if(exercises.isEmpty())null else recommend(exercises,snapshot,date,prefs,variant)}
    LazyColumn(Modifier.fillMaxSize().testTag("trainingList"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{Panel {
            Text("Тренировка за день",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=day.trained==true,onClick={vm.markTraining(date,if(day.mark==true)null else true)},label={Text("Была тренировка")},leadingIcon={Icon(Icons.Outlined.FitnessCenter,null)})
                FilterChip(selected=day.trained==false,enabled=day.workouts.isEmpty(),onClick={vm.markTraining(date,if(day.mark==false)null else false)},label={Text("Не было")},leadingIcon={Icon(Icons.Outlined.Bedtime,null)})
            }
            if(day.workouts.isNotEmpty())Text("Отмечено по выполненным упражнениям",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Израсходовано ≈ ${number(day.burned.value)}${if(!day.burned.complete)"+" else ""} ккал",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
            if(weight==null)Text("Укажите вес в профиле, чтобы считать калории упражнений",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.clickable(onClick=openProfile))
        }}
        item{Button(onClick=add,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("addExercise")){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Добавить упражнение")}}
        if(day.workouts.isNotEmpty()){
            item{SectionTitle("Выполненные упражнения",plural(day.workouts.size,"упражнение","упражнения","упражнений"))}
            items(day.workouts,key={it.id}){w->Panel {
                Row(Modifier.fillMaxWidth().clickable{edit(w)},verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(w.name,fontWeight=FontWeight.SemiBold);Text(workoutLabel(w),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={vm.deleteWorkout(w)}){Icon(Icons.Outlined.DeleteOutline,"Удалить ${w.name}")}
                }
            }}
        }
        plan?.let{p->
            item{Panel {
                SectionTitle("Рекомендации на сегодня",if(p.recovery)"Мышцы ещё восстанавливаются после последних тренировок — лёгкий день" else "Акцент: "+p.focus.distinct().joinToString(", "){muscleLabel(it).lowercase()})
                Text("Дома, ${if(prefs.dumbbells)"с гантелями и без" else "без инвентаря"} · уровень: ${levelLabel(prefs.level).lowercase()}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                p.items.forEach{r->RecommendedRow(r,weight){log(r.exercise,r.prescription)}}
                TextButton(onClick={variant++}){Icon(Icons.Outlined.Refresh,null);Spacer(Modifier.width(6.dp));Text("Другая подборка")}
            }}
            item{Panel {
                SectionTitle("За 7 дней","Ориентир ВОЗ для взрослых")
                val aerobic=p.weekly.aerobicMinutes
                Text("Аэробная нагрузка: ${number(aerobic)} из ${number(WHO_AEROBIC_MINUTES)} мин")
                LinearProgressIndicator(progress={(aerobic/WHO_AEROBIC_MINUTES).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth())
                Text("Силовые дни: ${p.weekly.strengthDays} из $WHO_STRENGTH_DAYS")
                LinearProgressIndicator(progress={(p.weekly.strengthDays.toFloat()/WHO_STRENGTH_DAYS).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                Text(if(aerobic>=WHO_AEROBIC_MINUTES&&p.weekly.strengthDays>=WHO_STRENGTH_DAYS)"Недельный ориентир выполнен — дальше можно поддерживать нагрузку." else "Интенсивные минуты (MET 6 и выше) считаются вдвойне. Учитываются кардио-упражнения.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
        }
        item{Panel {
            SectionTitle("Настройки тренировок")
            Text("Инвентарь",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=!prefs.dumbbells,onClick={vm.trainingPrefs(prefs.copy(dumbbells=false))},label={Text("Без инвентаря")})
                FilterChip(selected=prefs.dumbbells,onClick={vm.trainingPrefs(prefs.copy(dumbbells=true))},label={Text("Есть гантели")})
            }
            Text("Уровень",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..3).forEach{l->FilterChip(selected=prefs.level==l,onClick={vm.trainingPrefs(prefs.copy(level=l))},label={Text(levelLabel(l))})}}
        }}
        item{Text("Калории упражнений — оценка: MET из Compendium of Physical Activities 2024 × вес тела × время. Включает расход в покое и не прибавляется к цели по еде. При боли, заболеваниях сердца, суставов или беременности посоветуйтесь с врачом.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable private fun RecommendedRow(r:RecommendedExercise,weight:Double?,done:()->Unit) {
    val e=r.exercise;val p=r.prescription
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(when(r.role){Role.WARMUP->"РАЗМИНКА";Role.MAIN->"ОСНОВНАЯ ЧАСТЬ · ${muscleLabel(e.muscles.first()).uppercase()}";Role.CARDIO->"КАРДИО";Role.COOLDOWN->"ЗАМИНКА"},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(e.name,fontWeight=FontWeight.SemiBold)
                val minutes=p.minutesFor(e)
                Text(listOfNotNull(prescriptionLabel(p),if(e.equipment==Equipment.DUMBBELLS)"гантели" else null,minutes?.let{m->burnedKcal(e.met,weight,m)?.let{"≈ ${number(it)} ккал"}}).joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onClick=done){Text("Выполнено")}
        }
        Text(e.cue,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
fun prescriptionLabel(p:Prescription):String = p.minutes?.let{"${number(it)} мин"}?:p.sets?.let{s->p.reps?.let{"$s × $it"}?:p.seconds?.let{"$s × $it с"}}?:""
