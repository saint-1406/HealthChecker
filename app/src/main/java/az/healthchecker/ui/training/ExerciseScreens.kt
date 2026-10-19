package az.healthchecker.ui.training
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import kotlinx.coroutines.delay

@Composable fun ExerciseSearchScreen(vm:AppViewModel,dumbbellsByDefault:Boolean,back:()->Unit,select:(Exercise)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    var muscleName by rememberSaveable{mutableStateOf("")}
    var kindName by rememberSaveable{mutableStateOf("")}
    var dumbbells by rememberSaveable{mutableStateOf(dumbbellsByDefault)}
    var results by remember{mutableStateOf(emptyList<Exercise>())}
    val muscle=Muscle.entries.find{it.name==muscleName};val kind=ExerciseKind.entries.find{it.name==kindName}
    LaunchedEffect(query,muscle,kind,dumbbells){delay(150);results=vm.searchExercises(query,muscle,kind,dumbbells)}
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row{IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Назад")};Text("Какое упражнение?",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=8.dp))}
        OutlinedTextField(query,{query=it.take(60)},label={Text("Название упражнения")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("exerciseSearch"))
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(selected=muscle==null,onClick={muscleName=""},label={Text("Все мышцы")})
            Muscle.entries.forEach{m->FilterChip(selected=muscle==m,onClick={muscleName=if(muscle==m)"" else m.name},label={Text(muscleLabel(m))})}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(selected=kind==null,onClick={kindName=""},label={Text("Любые")})
            ExerciseKind.entries.forEach{k->FilterChip(selected=kind==k,onClick={kindName=if(kind==k)"" else k.name},label={Text(kindLabel(k))})}
            FilterChip(selected=dumbbells,onClick={dumbbells=!dumbbells},label={Text("С гантелями")})
        }
        if(results.isEmpty())Text("Ничего не найдено. Измените фильтры или название.",Modifier.padding(vertical=16.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(results,key={it.id}){e->Panel(Modifier.clickable{select(e)}){
            Text(e.name,style=MaterialTheme.typography.titleMedium)
            Text("${e.muscles.joinToString(", "){muscleLabel(it)}} · ${if(e.equipment==Equipment.DUMBBELLS)"гантели" else "без инвентаря"} · ${levelLabel(e.level).lowercase()}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${kindLabel(e.kind)} · ≈ ${number(e.met,1)} MET",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        }}}
    }
}

/** Logs a performed exercise; for sets the time is estimated with rest and stays editable. */
@Composable fun WorkoutEditor(vm:AppViewModel,exercise:Exercise,old:WorkoutEntry?,preset:Prescription?,id:String,date:String,bodyWeightKg:Double?,back:()->Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val start=old?.let{Prescription(it.sets,it.reps,it.seconds,it.minutes)}?:preset?:prescribe(exercise,1)
    var sets by rememberSaveable(id){mutableStateOf(start.sets?.toString()?:if(exercise.measure==ExerciseMeasure.MINUTES)"" else "3")}
    var reps by rememberSaveable(id){mutableStateOf(start.reps?.toString()?:"")}
    var seconds by rememberSaveable(id){mutableStateOf(start.seconds?.toString()?:"")}
    var load by rememberSaveable(id){mutableStateOf(old?.loadKg?.let(::plain)?:"")}
    var minutesText by rememberSaveable(id){mutableStateOf((old?.minutes?:start.minutes)?.let(::plain)?:"")}
    var minutesEdited by rememberSaveable(id){mutableStateOf(old!=null||exercise.measure==ExerciseMeasure.MINUTES)}
    fun int(s:String)=s.trim().toIntOrNull()
    val estimate=estimateMinutes(exercise,int(sets),int(reps),int(seconds))
    val minutes=if(minutesEdited)runCatching{parseQuantity(minutesText)}.getOrNull() else estimate
    val kcal=minutes?.let{burnedKcal(exercise.met,old?.bodyWeightKg?:bodyWeightKg,it)}
    val result=runCatching{
        require(minutes!=null){"Укажите время выполнения"}
        makeWorkout(id,exercise,if(exercise.measure==ExerciseMeasure.MINUTES)null else int(sets)?:throw IllegalArgumentException("Укажите подходы"),
            if(exercise.measure==ExerciseMeasure.REPS)int(reps)?:throw IllegalArgumentException("Укажите повторения") else null,
            if(exercise.measure==ExerciseMeasure.SECONDS)int(seconds)?:throw IllegalArgumentException("Укажите секунды") else null,
            load.takeIf{it.isNotBlank()&&exercise.equipment==Equipment.DUMBBELLS}?.let(::parseQuantity),minutes,date,old,java.time.Instant.now(),java.time.ZoneId.systemDefault(),bodyWeightKg)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Назад")}
        SectionTitle(exercise.name,if(old==null)"Выполнено · $date" else "Редактирование")
        Text(exercise.cue,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(exercise.measure!=ExerciseMeasure.MINUTES)CountField("Подходы",sets,"sets"){sets=it}
        if(exercise.measure==ExerciseMeasure.REPS)CountField("Повторений в подходе",reps,"reps"){reps=it}
        if(exercise.measure==ExerciseMeasure.SECONDS)CountField("Секунд в подходе",seconds,"seconds"){seconds=it}
        if(exercise.equipment==Equipment.DUMBBELLS)OutlinedTextField(load,{load=it.take(6)},label={Text("Вес гантели, кг (необязательно)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(if(minutesEdited)minutesText else estimate?.let{number(it,1)}?:"",{minutesEdited=true;minutesText=it.take(6)},label={Text("Время, мин")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("minutes"))
        if(!minutesEdited)Text("Оценка по темпу упражнения и отдыху ${number(restSeconds(exercise))} с между подходами — можно исправить.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Panel {
            Text("РАСХОД ЭНЕРГИИ",style=MaterialTheme.typography.labelMedium)
            Text(kcal?.let{"≈ ${number(it)} ккал"}?:if(bodyWeightKg==null&&old?.bodyWeightKg==null)"Укажите вес в профиле" else "—",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("workoutKcal"))
            Text("${number(exercise.met,1)} MET · Compendium 2024, код ${exercise.metCode}. Это средняя оценка для класса активности, не измерение.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        result.exceptionOrNull()?.let{Text(it.message?:"Проверьте данные",color=MaterialTheme.colorScheme.error)}
        Button(onClick={vm.saveWorkout(id,exercise,result.getOrThrow().sets,result.getOrThrow().reps,result.getOrThrow().seconds,result.getOrThrow().loadKg,result.getOrThrow().minutes,date,old,back)},enabled=result.isSuccess&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("saveWorkout")){Text(if(old==null)"Сохранить упражнение" else "Сохранить изменения")}
    }
}

@Composable private fun CountField(label:String,value:String,tag:String,change:(String)->Unit) =
    OutlinedTextField(value,{change(it.filter(Char::isDigit).take(4))},label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth().testTag(tag))
