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
import az.healthchecker.ui.theme.AppBackdrop
import az.healthchecker.ui.theme.HealthTheme
import androidx.compose.ui.graphics.Color
import az.healthchecker.ui.diary.*
import az.healthchecker.ui.dashboard.DashboardScreen
import az.healthchecker.ui.dashboard.ProteinScreen
import az.healthchecker.ui.profile.AppearanceScreen
import az.healthchecker.ui.profile.ProfileScreen
import az.healthchecker.ui.training.*
import az.healthchecker.ui.steps.StepsScreen
import java.time.LocalDate
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
    var medicationId by rememberSaveable{mutableStateOf("")}
    // Exercise search and the exercise form go back to where they were opened: the training tab or the workout screen.
    var returnTo by rememberSaveable{mutableStateOf("main")}
    // The food form returns to the protein screen when a food was picked there.
    var entryBack by rememberSaveable{mutableStateOf("main")}
    var photoName by rememberSaveable{mutableStateOf("")}
    val photos by vm.photos.collectAsStateWithLifecycle()
    var planVariant by rememberSaveable{mutableIntStateOf(0)}
    var planDate by rememberSaveable{mutableStateOf("")}
    val variant=if(planDate==date)planVariant else 0
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let(vm::selectFolder)}
    val restorePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::previewRestore)}
    LifecycleResumeEffect(Unit){val today=LocalDate.now().toString();date=advanceSelectedDate(date,lastToday,today);lastToday=today;onPauseOrDispose{}}
    fun edit(e:DiaryEntry){selectedEntry=e.id;selectedFood=e.foodId;templateEntry="";draftId=e.id;entryBack="main";route="editor"}
    fun add(food:String,template:String="",from:String="main"){selectedFood=food;selectedEntry="";templateEntry=template;draftId=UUID.randomUUID().toString();entryBack=from;route="editor"}
    fun leaveFor(next:String){if(route=="main"||route=="workout-now")returnTo=route;route=next}
    fun logExercise(e:Exercise,p:Prescription?){exerciseId=e.id;workoutId="";preset=p?.let{listOf(it.sets,it.reps,it.seconds,it.minutes).joinToString("|"){v->v?.toString()?:""}}?:"";draftId=UUID.randomUUID().toString();leaveFor("workout")}
    fun editWorkout(w:WorkoutEntry){exerciseId=w.exerciseId;workoutId=w.id;preset="";draftId=w.id;leaveFor("workout")}
    fun openDish(existing:Food?,fromInventory:Boolean=false){if(existing!=null||vm.dish.value==null)vm.startDish(existing);dishFromInventory=fromInventory;route="dish"}
    fun editMedication(m:Medication?){medicationId=m?.id?:"";route="medication"}
    fun editIngredient(index:Int){ingredientIndex=index;ingredientFood=vm.dish.value?.ingredients?.getOrNull(index)?.foodId?:"";route="dish-ingredient"}
    BackHandler(route!="main"){route=when(route){"custom"->"search";"dish"->if(dishFromInventory)"main" else "search";"dish-search","dish-ingredient"->"dish";"workout","exercise-search"->returnTo;"editor"->entryBack;else->"main"}}
    LaunchedEffect(notice){if(notice!=null){delay(8000);vm.dismissNotice()}}
    val today=remember(lastToday){LocalDate.now()}
    val appearance=settings.appearance
    HealthTheme(appearance,today) {
        // The backdrop paints the background (colour, own picture, holiday decorations); the screens lie on top of it.
        val picture=if(appearance.hasBackground)rememberPicture(vm.backgroundFile(),appearance.background,1200.dp) else null
        AppBackdrop(picture,appearance.dim,appearance.decorationSeason(today),appearance.decorations)
        // A transparent container gives no content colour of its own, so text would fall back to black: name it.
        Scaffold(containerColor=Color.Transparent,contentColor=MaterialTheme.colorScheme.onBackground,
            bottomBar={if(ready && snapshot.onboarded && route=="main")NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
                listOf(tr("Обзор","Overview") to Icons.Outlined.SpaceDashboard,tr("Дневник","Diary") to Icons.AutoMirrored.Outlined.MenuBook,tr("Тренировки","Training") to Icons.Outlined.FitnessCenter,tr("Профиль","Profile") to Icons.Outlined.PersonOutline).forEachIndexed{i,(label,icon)->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icon,label)},label={Text(label)})}
            }},
            topBar={if(ready && snapshot.onboarded && route=="main" && tab!=3)Column(Modifier.statusBarsPadding().padding(horizontal=24.dp,vertical=16.dp)){
                Text("HEALTHCHECKER",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(when(tab){0->tr("Ваш день","Your day");1->tr("Дневник питания","Food diary");else->tr("Тренировки","Training")},style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    IconButton(onClick={calendar=true}){Icon(Icons.Outlined.CalendarMonth,tr("Выбрать дату","Choose a date"))}
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick={date=LocalDate.parse(date).minusDays(1).toString()}){Icon(Icons.Outlined.ChevronLeft,tr("Предыдущий день","Previous day"))}
                    TextButton(onClick={date=LocalDate.now().toString()},modifier=Modifier.weight(1f)){Text(if(date==LocalDate.now().toString())tr("Сегодня · ","Today · ")+dayMonth(date) else dayMonth(date,true))}
                    IconButton(onClick={date=LocalDate.parse(date).plusDays(1).toString()},enabled=date<LocalDate.now().toString()){Icon(Icons.Outlined.ChevronRight,tr("Следующий день","Next day"))}
                }
            }}
        ){padding->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if(!ready)Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(16.dp));Text(tr("Открываем ваш дневник…","Opening your diary…"))}
                else if(!snapshot.onboarded)ProfileScreen(vm,true,{folderPicker.launch(null)},{restorePicker.launch(arrayOf("application/json","text/plain"))})
                else when(route) {
                    "search" -> FoodSearchScreen(vm,tr("Что вы съели?","What did you eat?"),{route="main"},{add(it.id)},{route="custom"},{openDish(null)},{openDish(it)})
                    // Started from the inventory, a saved dish goes back there; from food search it opens straight for eating.
                    "dish" -> DishEditor(vm,{route=if(dishFromInventory)"main" else "search"},{route="dish-search"},::editIngredient){if(dishFromInventory){tab=3;route="main"}else add(it.id)}
                    "dish-search" -> FoodSearchScreen(vm,tr("Ингредиент блюда","Dish ingredient"),{route="dish"},{ingredientIndex=-1;ingredientFood=it.id;route="dish-ingredient"},null,null,{})
                    "dish-ingredient" -> {
                        val existing=vm.dish.collectAsStateWithLifecycle().value?.ingredients?.getOrNull(ingredientIndex)
                        val food=existing?.let(vm::foodForIngredient)?:vm.food(ingredientFood)
                        if(food!=null)PortionEditor(food,existing?.quantity,"ingredient:$ingredientIndex:${food.id}",tr("Ингредиент блюда","Dish ingredient"),if(existing==null)tr("Добавить в блюдо","Add to the dish") else tr("Сохранить ингредиент","Save ingredient"),busy,{route="dish"}){vm.putIngredient(existing?.let{ingredientIndex},food,it);route="dish"}
                        else TextButton(onClick={route="dish"}){Text(tr("Продукт недоступен. Вернуться","The food is unavailable. Go back"))}
                    }
                    "exercise-search" -> ExerciseSearchScreen(vm,snapshot.trainingPrefs?.dumbbells==true,{route=returnTo}){logExercise(it,null)}
                    "workout-now" -> WorkoutNowScreen(vm,snapshot,date,rememberTodayPlan(vm,snapshot,date,variant),{route="main"},{planVariant=variant+1;planDate=date},::logExercise,::editWorkout){leaveFor("exercise-search")}
                    "workout" -> {
                        val old=snapshot.workouts.find{it.id==workoutId}
                        val exercise=vm.exercise(exerciseId)
                        val p=preset.split("|").takeIf{it.size==4}?.let{v->Prescription(v[0].toIntOrNull(),v[1].toIntOrNull(),v[2].toIntOrNull(),v[3].toDoubleOrNull())}
                        if(exercise!=null)WorkoutEditor(vm,exercise,old,p,draftId,date,snapshot.bodyWeight(date),{route=returnTo})else TextButton(onClick={route=returnTo}){Text(tr("Упражнение недоступно. Вернуться","The exercise is unavailable. Go back"))}
                    }
                    "custom" -> CustomFoodEditor(vm,{route="search"})
                    "medication" -> MedicationEditor(vm,snapshot.medications.find{it.id==medicationId},date,{route="main"})
                    "steps" -> StepsScreen(vm,snapshot,date,{route="main"})
                    "photo" -> photos.find{it.fileName==photoName}?.let{p->PhotoEntryScreen(vm,snapshot,p,{route="main"}){moved->photoName=moved.fileName}}
                        ?:TextButton(onClick={route="main"}){Text(tr("Фото недоступно. Вернуться","The photo is unavailable. Go back"))}
                    "appearance" -> AppearanceScreen(vm){route="main"}
                    "protein" -> ProteinScreen(vm,snapshot,date,{route="main"}){add(it.id,from="protein")}
                    "editor" -> {
                        val old=snapshot.entries.find{it.id==selectedEntry}
                        val template=if(old==null)snapshot.entries.find{it.id==templateEntry}else null
                        // A repeated entry stays usable even when its catalog product is no longer available.
                        val food=if(old!=null)vm.foodFor(old)else vm.food(selectedFood)?:template?.let(vm::foodFor)
                        if(food!=null)EntryEditor(vm,food,old,template,draftId,date,{route=entryBack})else TextButton(onClick={route=entryBack}){Text(tr("Продукт недоступен. Вернуться","The food is unavailable. Go back"))}
                    }
                    else -> when(tab) {
                        0 -> DashboardScreen(vm,snapshot,date,backupError,{tab=1;route="search"},{vm.water(date)},::edit,{tab=3},{tab=2},{route="steps"},{route="protein"},{tab=1})
                        1 -> DiaryScreen(vm,snapshot,date,{route="search"},::edit,{e->add(e.foodId,e.id)},::editMedication){photoName=it.fileName;route="photo"}
                        2 -> TrainingScreen(vm,snapshot,date,rememberTodayPlan(vm,snapshot,date,variant),{leaveFor("exercise-search")},{route="workout-now"},::editWorkout,{tab=3})
                        else -> ProfileScreen(vm,false,{folderPicker.launch(null)},{restorePicker.launch(arrayOf("application/json","text/plain"))},date,{openDish(null,true)},{openDish(it,true)}){route="appearance"}
                    }
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                notice?.let{n->Snackbar(modifier=Modifier.align(Alignment.BottomCenter).padding(16.dp),action=if(n.undo)({TextButton(onClick={vm.undo()}){Text(tr("Отменить","Undo"))}})else null,dismissAction={IconButton(onClick={vm.dismissNotice()}){Icon(Icons.Outlined.Close,tr("Закрыть","Close"))}}){Text(n.text)}}
            }
        }
        if(error!=null)AlertDialog(onDismissRequest={vm.error.value=null},title={Text(tr("Не удалось выполнить действие","Something went wrong"))},text={Text(error!!)},confirmButton={TextButton(onClick={vm.error.value=null}){Text(tr("Понятно","OK"))}})
        if(restore!=null)AlertDialog(onDismissRequest={vm.restorePreview.value=null},title={Text(tr("Восстановить данные?","Restore the data?"))},text={val r=restore!!;Text(tr("В копии ","The backup has ")+count(r.entries.size,"запись","записи","записей","entry","entries")+(r.entries.maxOfOrNull{it.localDate}?.let{tr(" (последняя — "," (latest ")+dayMonth(it,true)+")"}?:"")+tr(" и "," and ")+count(r.customFoods.size,"свой продукт","своих продукта","своих продуктов","own food","own foods")+(if(r.workouts.isNotEmpty())", "+count(r.workouts.size,"упражнение","упражнения","упражнений","exercise","exercises") else "")+(if(r.medications.isNotEmpty())", "+count(r.medications.size,"препарат","препарата","препаратов","medicine card","medicine cards") else "")+tr(". Текущий дневник и профиль будут заменены данными из этого файла. Папку для новых копий потом нужно выбрать заново.",". The current diary and profile will be replaced with this file. Choose the folder for new backups again afterwards."))},confirmButton={TextButton(onClick={vm.confirmRestore()},enabled=!busy){Text(tr("Восстановить","Restore"))}},dismissButton={TextButton(onClick={vm.restorePreview.value=null}){Text(tr("Отмена","Cancel"))}})
        if(calendar){val today=LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val state=rememberDatePickerState(initialSelectedDateMillis=LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),selectableDates=object:SelectableDates{override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis<=today})
            DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{date=java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()};calendar=false}){Text(tr("Выбрать","Select"))}},dismissButton={TextButton(onClick={calendar=false}){Text(tr("Отмена","Cancel"))}}){DatePicker(state)}}
    }
}
