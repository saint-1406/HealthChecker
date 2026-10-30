package az.healthchecker.ui
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import az.healthchecker.ui.explore.ExploreHomeScreen
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HealthApp(vm:AppViewModel=viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val mealDraft by vm.mealDraft.collectAsStateWithLifecycle()
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
    var foodDate by rememberSaveable{mutableStateOf(date)}
    var mealItemId by rememberSaveable{mutableStateOf("")}
    var foodSearchBack by rememberSaveable{mutableStateOf("main")}
    var barcodeBack by rememberSaveable{mutableStateOf("search")}
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
    var videoId by rememberSaveable{mutableStateOf("")}
    val photos by vm.photos.collectAsStateWithLifecycle()
    var planVariant by rememberSaveable{mutableIntStateOf(0)}
    var planDate by rememberSaveable{mutableStateOf("")}
    val variant=if(planDate==date)planVariant else 0
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){it?.let(vm::selectFolder)}
    val restorePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::previewRestore)}
    LifecycleResumeEffect(Unit){val today=LocalDate.now().toString();date=advanceSelectedDate(date,lastToday,today);lastToday=today;onPauseOrDispose{}}
    fun edit(e:DiaryEntry){selectedEntry=e.id;selectedFood=e.foodId;templateEntry="";draftId=e.id;foodDate=e.localDate;entryBack="main";route="editor"}
    fun add(food:String,template:String="",from:String="main"){selectedFood=food;selectedEntry="";templateEntry=template;draftId=UUID.randomUUID().toString();foodDate=mealDraft?.date?:date;entryBack=from;route="editor"}
    fun foodSearch(backTo:String="main"){foodSearchBack=backTo;route="search"}
    fun leaveFor(next:String){if(route=="main"||route=="workout-now")returnTo=route;route=next}
    fun logExercise(e:Exercise,p:Prescription?){exerciseId=e.id;workoutId="";preset=p?.let{listOf(it.sets,it.reps,it.seconds,it.minutes).joinToString("|"){v->v?.toString()?:""}}?:"";draftId=UUID.randomUUID().toString();leaveFor("workout")}
    fun editWorkout(w:WorkoutEntry){exerciseId=w.exerciseId;workoutId=w.id;preset="";draftId=w.id;leaveFor("workout")}
    fun openDish(existing:Food?,fromInventory:Boolean=false){if(existing!=null||vm.dish.value==null)vm.startDish(existing);dishFromInventory=fromInventory;route="dish"}
    fun editMedication(m:Medication?){medicationId=m?.id?:"";route="medication"}
    fun editIngredient(index:Int){ingredientIndex=index;ingredientFood=vm.dish.value?.ingredients?.getOrNull(index)?.foodId?:"";route="dish-ingredient"}
    BackHandler(route!="main"){route=when(route){"search"->foodSearchBack;"barcode"->barcodeBack;"custom"->"search";"dish"->if(dishFromInventory)"main" else "search";"dish-search","dish-ingredient"->"dish";"workout","exercise-search"->returnTo;"editor"->entryBack;"meal-item"->"meal";"video"->"videos";else->"main"}}
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
                listOf(Triple(0,tr("Обзор","Overview"),Icons.Outlined.SpaceDashboard),Triple(1,tr("Дневник","Diary"),Icons.AutoMirrored.Outlined.MenuBook),Triple(2,tr("Тренировки","Training"),Icons.Outlined.FitnessCenter),Triple(4,tr("Находки","Findings"),Icons.Outlined.Explore),Triple(3,tr("Профиль","Profile"),Icons.Outlined.PersonOutline)).forEach{(id,label,icon)->NavigationBarItem(selected=tab==id,onClick={tab=id},icon={Icon(icon,label)},label={Text(label)})}
            }},
            topBar={if(ready && snapshot.onboarded && route=="main" && tab!=3 && tab!=4)Column(Modifier.statusBarsPadding().padding(horizontal=24.dp,vertical=if(LocalDensity.current.fontScale>1.3f)8.dp else 16.dp)){
                if(LocalDensity.current.fontScale<=1.3f)Text("HEALTHCHECKER",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(when(tab){0->tr("Ваш день","Your day");1->tr("Дневник питания","Food diary");else->tr("Тренировки","Training")},style=if(LocalDensity.current.fontScale>1.3f)MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    IconButton(onClick={calendar=true}){Icon(Icons.Outlined.CalendarMonth,tr("Выбрать дату","Choose a date"))}
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick={date=LocalDate.parse(date).minusDays(1).toString()}){Icon(Icons.Outlined.ChevronLeft,tr("Предыдущий день","Previous day"))}
                    TextButton(onClick={date=LocalDate.now().toString()},modifier=Modifier.weight(1f)){Text(if(date==LocalDate.now().toString())tr("Сегодня · ","Today · ")+dayMonth(date) else dayMonth(date,true))}
                    IconButton(onClick={date=LocalDate.parse(date).plusDays(1).toString()},enabled=date<LocalDate.now().toString()){Icon(Icons.Outlined.ChevronRight,tr("Следующий день","Next day"))}
                }
            }}
        ){padding->
            Column(Modifier.fillMaxSize().padding(padding)) {
              Box(Modifier.fillMaxWidth().weight(1f)) {
                if(!ready)Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(16.dp));Text(tr("Открываем ваш дневник…","Opening your diary…"))}
                else if(!snapshot.onboarded)ProfileScreen(vm,true,{folderPicker.launch(null)},{restorePicker.launch(arrayOf("application/zip","application/octet-stream","application/json","text/plain"))})
                else when(route) {
                    "search" -> FoodSearchScreen(vm,tr("Что вы съели?","What did you eat?"),{route=foodSearchBack},{add(it.id)},{route="custom"},{openDish(null)},{openDish(it)},scan={barcodeBack="search";route="barcode"})
                    "barcode" -> BarcodeScreen(vm,{route=barcodeBack}){food->if(barcodeBack=="dish-search"){ingredientIndex=-1;ingredientFood=food.id;route="dish-ingredient"}else add(food.id)}
                    "meals" -> SavedMealsScreen(vm,snapshot,date,{route="main"}){meal->vm.addSavedMeal(meal,date){route="meal"}}
                    "meal" -> mealDraft?.let{d->MealDraftScreen(vm,d,{route="main"},{foodSearch("meal")},{item->mealItemId=item.id;route="meal-item"}){vm.commitMealDraft{day->date=day;tab=1;route="main"}}}
                        ?:TextButton(onClick={foodSearch()}){Text(tr("Добавить продукт","Add food"))}
                    "meal-item" -> mealDraft?.items?.find{it.id==mealItemId}?.let{item->
                        PortionEditor(item.food,item.quantity,"meal-item:${item.id}",tr("Порция в приёме пищи","Meal portion"),tr("Сохранить порцию","Save portion"),busy,{route="meal"}){q->vm.updateMealItem(item,q){route="meal"}}
                    }?:TextButton(onClick={route="meal"}){Text(tr("Вернуться к приёму пищи","Back to the meal"))}
                    // Started from the inventory, a saved dish goes back there; from food search it opens straight for eating.
                    "dish" -> DishEditor(vm,{route=if(dishFromInventory)"main" else "search"},{route="dish-search"},::editIngredient){if(dishFromInventory){tab=3;route="main"}else add(it.id)}
                    "dish-search" -> FoodSearchScreen(vm,tr("Ингредиент блюда","Dish ingredient"),{route="dish"},{ingredientIndex=-1;ingredientFood=it.id;route="dish-ingredient"},null,null,{},scan={barcodeBack="dish-search";route="barcode"})
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
                    "videos" -> VideoLibraryScreen(vm,snapshot,{route="main"}){videoId=it;route="video"}
                    "video" -> VideoScreen(vm,snapshot,date,videoId,{route="videos"})
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
                        if(food!=null)EntryEditor(vm,food,old,template,draftId,foodDate,{route=entryBack},{foodSearch("meal")}){day->date=day;route=entryBack}else TextButton(onClick={route=entryBack}){Text(tr("Продукт недоступен. Вернуться","The food is unavailable. Go back"))}
                    }
                    else -> when(tab) {
                        0 -> DashboardScreen(vm,snapshot,date,backupError,{tab=1;foodSearch()},{vm.water(date)},::edit,{tab=3},{tab=2},{route="steps"},{route="protein"},{tab=1},{route="meal"},{folderPicker.launch(null)})
                        1 -> DiaryScreen(vm,snapshot,date,{foodSearch()},::edit,{e->add(e.foodId,e.id)},::editMedication,
                            openPhoto={photoName=it.fileName;route="photo"},selectFood={add(it.id)},openMeals={route="meals"},openDraft={route="meal"},openProfile={tab=3},chooseFolder={folderPicker.launch(null)})
                        2 -> TrainingScreen(vm,snapshot,date,rememberTodayPlan(vm,snapshot,date,variant),{leaveFor("exercise-search")},{route="workout-now"},::editWorkout,{tab=3}){route="videos"}
                        4 -> ExploreHomeScreen()
                        else -> ProfileScreen(vm,false,{folderPicker.launch(null)},{restorePicker.launch(arrayOf("application/zip","application/octet-stream","application/json","text/plain"))},date,{openDish(null,true)},{openDish(it,true)}){route="appearance"}
                    }
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
              }
              notice?.let{n->Snackbar(modifier=Modifier.padding(16.dp),action=if(n.undo)({TextButton(onClick={vm.undo()}){Text(tr("Отменить","Undo"))}})else null,dismissAction={IconButton(onClick={vm.dismissNotice()}){Icon(Icons.Outlined.Close,tr("Закрыть","Close"))}}){Text(n.text,maxLines=3)}}
            }
        }
        if(error!=null)AlertDialog(onDismissRequest={vm.error.value=null},title={Text(tr("Не удалось выполнить действие","Something went wrong"))},text={Text(error!!)},confirmButton={TextButton(onClick={vm.error.value=null}){Text(tr("Понятно","OK"))}})
        if(restore!=null)AlertDialog(onDismissRequest={vm.cancelRestore()},title={Text(tr("Восстановить данные?","Restore the data?"))},text={
            val r=restore!!
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(tr("Записей питания: ","Diary entries: ")+r.entries.size+tr("; своих продуктов: ","; own foods: ")+r.customFoods.size)
                Text(tr("Тренировок: ","Workouts: ")+r.workouts.size+tr("; лекарств: ","; medicine cards: ")+r.medications.size)
                val e=r.explore
                Text(tr("Находок: ","Findings: ")+(e?.findings?.size?:0)+tr("; мест: ","; places: ")+(e?.places?.size?:0)+tr("; прогулок: ","; walks: ")+(e?.walks?.size?:0))
                Text(tr("Текущий дневник, профиль, находки, места и прогулки будут заменены данными из этого файла. Папку для новых копий потом нужно выбрать заново.","The current diary, profile, findings, places and walks will be replaced with this file. Choose the folder for new backups again afterwards."))
            }
        },confirmButton={TextButton(onClick={vm.confirmRestore()},enabled=!busy){Text(tr("Восстановить","Restore"))}},dismissButton={TextButton(onClick={vm.cancelRestore()},enabled=!busy){Text(tr("Отмена","Cancel"))}})
        if(calendar){val today=LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val state=rememberDatePickerState(initialSelectedDateMillis=LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),selectableDates=object:SelectableDates{override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis<=today})
            DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{date=java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()};calendar=false}){Text(tr("Выбрать","Select"))}},dismissButton={TextButton(onClick={calendar=false}){Text(tr("Отмена","Cancel"))}}){DatePicker(state)}}
    }
}
