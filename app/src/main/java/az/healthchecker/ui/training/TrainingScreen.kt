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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.kit.FilledTonalButton
import az.healthchecker.ui.kit.OutlinedButton
import az.healthchecker.ui.kit.Button
import az.healthchecker.ui.theme.rounded

/** Today's suggested session; the training tab and the workout screen share it, so both show the same set. */
@Composable fun rememberTodayPlan(vm:AppViewModel,snapshot:UserSnapshot,date:String,variant:Int):Recommendation? {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val prefs=snapshot.trainingPrefs?:TrainingPrefs()
    return remember(exercises,snapshot.workouts,date,prefs,variant){if(exercises.isEmpty())null else recommend(exercises,snapshot,date,prefs,variant)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TrainingScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,plan:Recommendation?,add:()->Unit,start:()->Unit,edit:(WorkoutEntry)->Unit,openProfile:()->Unit,log:(Exercise)->Unit,openVideo:(String)->Unit,videos:()->Unit,progress:()->Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val favoritesTab by vm.trainingFavorites.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DayFavoritesTabs(favoritesTab,snapshot.favoriteExercises.size+snapshot.favoriteVideos.size,tr("Сегодня","Today")){vm.trainingFavorites.value=it}
        if(favoritesTab)LazyColumn(Modifier.fillMaxSize().testTag("favoriteWorkoutsList"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){favoriteWorkouts(vm,snapshot,date,busy,log,openVideo)}
        else TrainingDay(vm,snapshot,date,plan,busy,add,start,edit,openProfile,videos,progress)
    }
}

@Composable private fun TrainingDay(vm:AppViewModel,snapshot:UserSnapshot,date:String,plan:Recommendation?,busy:Boolean,add:()->Unit,start:()->Unit,edit:(WorkoutEntry)->Unit,openProfile:()->Unit,videos:()->Unit,progress:()->Unit) {
    val prefs=snapshot.trainingPrefs?:TrainingPrefs()
    val day=snapshot.training(date)
    val weight=snapshot.bodyWeight(date)
    // Records each of the day's entries set when it was logged, for a trophy in the list.
    val trophies=remember(snapshot.workouts,date){day.workouts.map{it.exerciseId}.toSet().flatMap{id->recordsAtTheTime(snapshot.exerciseHistory(id)).entries}.associate{it.key to it.value}}
    LazyColumn(Modifier.fillMaxSize().testTag("trainingList"),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        // The day in one slim card: burned energy on the left, "trained / rest day" chips on the right.
        item{Surface(shape=rounded(20.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Израсходовано","Burned"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("≈ ${number(day.burned.value)}${if(!day.burned.complete)"+" else ""} $KCAL",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
                    }
                    FilterChip(selected=day.trained==true,onClick={vm.markTraining(date,if(day.mark==true)null else true)},label={Text(tr("Была","Trained"))},leadingIcon={Icon(Icons.Outlined.FitnessCenter,tr("Была тренировка","Trained"),Modifier.size(16.dp))})
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected=day.trained==false,enabled=day.workouts.isEmpty(),onClick={vm.markTraining(date,if(day.mark==false)null else false)},label={Text(tr("Не было","Rest"))},leadingIcon={Icon(Icons.Outlined.Bedtime,null,Modifier.size(16.dp))})
                }
                if(day.workouts.isNotEmpty())Text(tr("Отмечено по выполненным упражнениям","Marked from the logged exercises"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(weight==null)Text(tr("Укажите вес в профиле, чтобы считать калории упражнений","Enter your weight in the profile to estimate exercise calories"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.clickable(onClick=openProfile))
            }
        }}
        item{Button(onClick=add,modifier=Modifier.fillMaxWidth().height(46.dp).testTag("addExercise")){Icon(Icons.Outlined.Add,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(tr("Добавить упражнение","Add exercise"))}}
        // Right under "Add exercise", above the day's list, so they are on the first screen however much was logged.
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilledTonalButton(onClick=videos,contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.weight(1f).height(44.dp).testTag("openVideos")){Icon(Icons.Outlined.SmartDisplay,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(tr("Видео","Videos"),maxLines=1)}
            OutlinedButton(onClick=progress,contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.weight(1f).height(44.dp).testTag("openProgress")){Icon(Icons.Outlined.Insights,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(tr("Прогресс","Progress"),maxLines=1)}
        }}
        if(day.workouts.isNotEmpty()){
            item{Text(tr("Выполнено · ","Done · ")+count(day.workouts.size,"упражнение","упражнения","упражнений","exercise","exercises"),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=6.dp))}
            items(day.workouts,key={it.id}){w->Surface(shape=rounded(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable{edit(w)}.padding(start=14.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(vm.workoutName(w),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text(workoutLabel(w),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        trophies[w.id]?.let{Text("🏆 "+tr("Рекорд: ","Record: ")+recordsText(w,it),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Medium,modifier=Modifier.testTag("recordBadge"))}}
                    if(vm.exercise(w.exerciseId)!=null)FavoriteStar(w.exerciseId in snapshot.favoriteExercises,vm.workoutName(w),!busy){vm.toggleFavoriteExercise(w.exerciseId)}
                    IconButton(onClick={vm.deleteWorkout(w)}){Icon(Icons.Outlined.DeleteOutline,tr("Удалить ","Delete ")+vm.workoutName(w))}
                }
            }}
        }
        plan?.let{p->
            item{Panel {
                Text(tr("Рекомендации на сегодня","Today's suggestions"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text(if(p.recovery)tr("Мышцы ещё восстанавливаются после последних тренировок — лёгкий день","Muscles are still recovering from recent training — an easy day") else tr("Акцент: ","Focus: ")+p.focus.distinct().joinToString(", "){muscleLabel(it).lowercase(Lang.locale)},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${tr("Дома","At home")}, ${if(prefs.dumbbells)tr("с гантелями и без","with and without dumbbells") else tr("без инвентаря","no equipment")} · ${tr("уровень","level")}: ${levelLabel(prefs.level).lowercase(Lang.locale)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){p.items.forEach{r->ExerciseThumb(vm,r.exercise.id,48.dp)}}
                val done=p.items.count{r->day.workouts.any{it.exerciseId==r.exercise.id}}
                val minutes=p.items.sumOf{it.prescription.minutesFor(it.exercise)?:0.0}
                Text(listOfNotNull(count(p.items.size,"упражнение","упражнения","упражнений","exercise","exercises"),"≈ ${number(minutes)} ${tr("мин","min")}",if(done>0)tr("выполнено ","done ")+"$done" else null).joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
                if(done>0)LinearProgressIndicator(progress={done.toFloat()/p.items.size},modifier=Modifier.fillMaxWidth())
                Button(onClick=start,modifier=Modifier.fillMaxWidth().height(46.dp).testTag("startWorkout")){
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
