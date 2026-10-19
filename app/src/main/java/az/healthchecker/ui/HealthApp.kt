package az.healthchecker.ui
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import az.healthchecker.domain.*
import az.healthchecker.ui.theme.HealthTheme
import az.healthchecker.ui.diary.*
import az.healthchecker.ui.dashboard.DashboardScreen
import az.healthchecker.ui.profile.ProfileScreen
import az.healthchecker.ui.training.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HealthApp(vm:AppViewModel=viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val restore by vm.restorePreview.collectAsStateWithLifecycle()
    val backupError by vm.backupError.collectAsStateWithLifecycle()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var route by rememberSaveable{mutableStateOf("main")}
    var date by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var lastToday by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var selectedFood by rememberSaveable{mutableStateOf("")}
    var selectedEntry by rememberSaveable{mutableStateOf("")}
    var templateEntry by rememberSaveable{mutableStateOf("")}
    var draftId by rememberSaveable{mutableStateOf(UUID.randomUUID().toString())}
    var calendar by rememberSaveable{mutableStateOf(false)}
    var exerciseId by rememberSaveable{mutableStateOf("")}
    var workoutId by rememberSaveable{mutableStateOf("")}
    var preset by rememberSaveable{mutableStateOf("")}
    var ingredientIndex by rememberSaveable{mutableIntStateOf(-1)}
    var ingredientFood by rememberSaveable{mutableStateOf("")}
    var dishFromInventory by rememberSaveable{mutableStateOf(false)}
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let(vm::selectFolder)}
    val restorePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::previewRestore)}
    LifecycleResumeEffect(Unit){val today=LocalDate.now().toString();date=advanceSelectedDate(date,lastToday,today);lastToday=today;onPauseOrDispose{}}
    fun edit(e:DiaryEntry){selectedEntry=e.id;selectedFood=e.foodId;templateEntry="";draftId=e.id;route="editor"}
    fun add(food:String,template:String=""){selectedFood=food;selectedEntry="";templateEntry=template;draftId=UUID.randomUUID().toString();route="editor"}
    fun logExercise(e:Exercise,p:Prescription?){exerciseId=e.id;workoutId="";preset=p?.let{listOf(it.sets,it.reps,it.seconds,it.minutes).joinToString("|"){v->v?.toString()?:""}}?:"";draftId=UUID.randomUUID().toString();route="workout"}
    fun editWorkout(w:WorkoutEntry){exerciseId=w.exerciseId;workoutId=w.id;preset="";draftId=w.id;route="workout"}
    fun openDish(existing:Food?,fromInventory:Boolean=false){if(existing!=null||vm.dish.value==null)vm.startDish(existing);dishFromInventory=fromInventory;route="dish"}
    fun editIngredient(index:Int){ingredientIndex=index;ingredientFood=vm.dish.value?.ingredients?.getOrNull(index)?.foodId?:"";route="dish-ingredient"}
    BackHandler(route!="main"){route=when(route){"custom"->"search";"dish"->if(dishFromInventory)"main" else "search";"dish-search","dish-ingredient"->"dish";else->"main"}}
    LaunchedEffect(notice){if(notice!=null){delay(8000);vm.dismissNotice()}}
    HealthTheme(settings.theme) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            bottomBar={if(ready && snapshot.onboarded && route=="main")NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
                listOf("Обзор" to Icons.Outlined.SpaceDashboard,"Дневник" to Icons.AutoMirrored.Outlined.MenuBook,"Тренировки" to Icons.Outlined.FitnessCenter,"Профиль" to Icons.Outlined.PersonOutline).forEachIndexed{i,(label,icon)->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icon,label)},label={Text(label)})}
            }},
            topBar={if(ready && snapshot.onboarded && route=="main" && tab!=3)Column(Modifier.statusBarsPadding().padding(horizontal=24.dp,vertical=16.dp)){
                Text("HEALTHCHECKER",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(when(tab){0->"Ваш день";1->"Дневник питания";else->"Тренировки"},style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    IconButton(onClick={calendar=true}){Icon(Icons.Outlined.CalendarMonth,"Выбрать дату")}
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick={date=LocalDate.parse(date).minusDays(1).toString()}){Icon(Icons.Outlined.ChevronLeft,"Предыдущий день")}
                    TextButton(onClick={date=LocalDate.now().toString()},modifier=Modifier.weight(1f)){Text(if(date==LocalDate.now().toString())"Сегодня · "+LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM",RU))else LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy",RU)))}
                    IconButton(onClick={date=LocalDate.parse(date).plusDays(1).toString()},enabled=date<LocalDate.now().toString()){Icon(Icons.Outlined.ChevronRight,"Следующий день")}
                }
            }}
        ){padding->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if(!ready)Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(16.dp));Text("Открываем ваш дневник…")}
                else if(!snapshot.onboarded)ProfileScreen(vm,true,{folderPicker.launch(null)},{restorePicker.launch(arrayOf("application/json","text/plain"))})
                else when(route) {
                    "search" -> FoodSearchScreen(vm,"Что вы съели?",{route="main"},{add(it.id)},{route="custom"},{openDish(null)},{openDish(it)})
                    // Started from the inventory, a saved dish goes back there; from food search it opens straight for eating.
                    "dish" -> DishEditor(vm,{route=if(dishFromInventory)"main" else "search"},{route="dish-search"},::editIngredient){if(dishFromInventory){tab=3;route="main"}else add(it.id)}
                    "dish-search" -> FoodSearchScreen(vm,"Ингредиент блюда",{route="dish"},{ingredientIndex=-1;ingredientFood=it.id;route="dish-ingredient"},null,null,{})
                    "dish-ingredient" -> {
                        val existing=vm.dish.collectAsStateWithLifecycle().value?.ingredients?.getOrNull(ingredientIndex)
                        val food=existing?.let(vm::foodForIngredient)?:vm.food(ingredientFood)
                        if(food!=null)PortionEditor(food,existing?.quantity,"ingredient:$ingredientIndex:${food.id}","Ингредиент блюда",if(existing==null)"Добавить в блюдо" else "Сохранить ингредиент",busy,{route="dish"}){vm.putIngredient(existing?.let{ingredientIndex},food,it);route="dish"}
                        else TextButton(onClick={route="dish"}){Text("Продукт недоступен. Вернуться")}
                    }
                    "exercise-search" -> ExerciseSearchScreen(vm,snapshot.trainingPrefs?.dumbbells==true,{route="main"}){logExercise(it,null)}
                    "workout" -> {
                        val old=snapshot.workouts.find{it.id==workoutId}
                        val exercise=vm.exercise(exerciseId)
                        val p=preset.split("|").takeIf{it.size==4}?.let{v->Prescription(v[0].toIntOrNull(),v[1].toIntOrNull(),v[2].toIntOrNull(),v[3].toDoubleOrNull())}
                        if(exercise!=null)WorkoutEditor(vm,exercise,old,p,draftId,date,snapshot.profile?.currentWeight,{route="main"})else TextButton(onClick={route="main"}){Text("Упражнение недоступно. Вернуться")}
                    }
                    "custom" -> CustomFoodEditor(vm,{route="search"})
                    "editor" -> {
                        val old=snapshot.entries.find{it.id==selectedEntry}
                        val template=if(old==null)snapshot.entries.find{it.id==templateEntry}else null
                        // A repeated entry stays usable even when its catalog product is no longer available.
                        val food=if(old!=null)vm.foodFor(old)else vm.food(selectedFood)?:template?.let(vm::foodFor)
                        if(food!=null)EntryEditor(vm,food,old,template,draftId,date,{route="main"})else TextButton(onClick={route="main"}){Text("Продукт недоступен. Вернуться")}
                    }
                    else -> when(tab) {
                        0 -> DashboardScreen(snapshot,date,backupError,{tab=1;route="search"},{vm.water(date)},::edit,{tab=3},{tab=2})
                        1 -> DiaryScreen(vm,snapshot,date,{route="search"},::edit,{e->add(e.foodId,e.id)})
                        2 -> TrainingScreen(vm,snapshot,date,{route="exercise-search"},::logExercise,::editWorkout,{tab=3})
                        else -> ProfileScreen(vm,false,{folderPicker.launch(null)},{restorePicker.launch(arrayOf("application/json","text/plain"))},date,{openDish(null,true)},{openDish(it,true)})
                    }
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                notice?.let{n->Snackbar(modifier=Modifier.align(Alignment.BottomCenter).padding(16.dp),action=if(n.undo)({TextButton(onClick={vm.undo()}){Text("Отменить")}})else null,dismissAction={IconButton(onClick={vm.dismissNotice()}){Icon(Icons.Outlined.Close,"Закрыть")}}){Text(n.text)}}
            }
        }
        if(error!=null)AlertDialog(onDismissRequest={vm.error.value=null},title={Text("Не удалось выполнить действие")},text={Text(error!!)},confirmButton={TextButton(onClick={vm.error.value=null}){Text("Понятно")}})
        if(restore!=null)AlertDialog(onDismissRequest={vm.restorePreview.value=null},title={Text("Восстановить данные?")},text={val r=restore!!;Text("В копии ${plural(r.entries.size,"запись","записи","записей")}${r.entries.maxOfOrNull{it.localDate}?.let{" (последняя — ${LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMMM yyyy",RU))})"}?:""} и ${plural(r.customFoods.size,"свой продукт","своих продукта","своих продуктов")}${if(r.workouts.isNotEmpty())", "+plural(r.workouts.size,"упражнение","упражнения","упражнений") else ""}. Текущий дневник и профиль будут заменены данными из этого файла. Папку для новых копий потом нужно выбрать заново.")},confirmButton={TextButton(onClick={vm.confirmRestore()},enabled=!busy){Text("Восстановить")}},dismissButton={TextButton(onClick={vm.restorePreview.value=null}){Text("Отмена")}})
        if(calendar){val today=LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val state=rememberDatePickerState(initialSelectedDateMillis=LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),selectableDates=object:SelectableDates{override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis<=today})
            DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{date=java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()};calendar=false}){Text("Выбрать")}},dismissButton={TextButton(onClick={calendar=false}){Text("Отмена")}}){DatePicker(state)}}
    }
}
