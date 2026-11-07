package az.healthchecker.ui.profile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.*

/** Sub-screens the profile opens; HealthApp shows each under this route. */
object ProfileRoutes {
    const val BODY="profile-body";const val TARGETS="profile-targets";const val INVENTORY="inventory";const val HOME="profile-home"
    const val BACKUP="profile-backup";const val LOOK="profile-look";const val ABOUT="profile-about";const val SETUP="setup";const val PROGRAM="program"
    val all=listOf(BODY,TARGETS,INVENTORY,HOME,BACKUP,LOOK,ABOUT,SETUP,PROGRAM)
}

/**
 * New users get the welcome form (the program setup first, a quick manual form below it, or a restore); everyone else a
 * short summary of body and today's targets with grouped entries that open their own screens.
 */
@Composable fun ProfileScreen(vm:AppViewModel,onboarding:Boolean,folder:()->Unit,restore:()->Unit,open:(String)->Unit={}) {
    if(onboarding)OnboardingForm(vm,restore,open) else ProfileHub(vm,open)
}

fun goalLabel(g:FitnessGoal):String = when(g){
    FitnessGoal.BULK->tr("Набрать массу","Build mass");FitnessGoal.GAIN_WEIGHT->tr("Просто набрать вес","Just gain weight");FitnessGoal.MUSCLE->tr("Нарастить мышцы","Build muscle")
    FitnessGoal.CUT->tr("Сбросить жир","Lose fat");FitnessGoal.MAINTAIN->tr("Поддерживать вес","Maintain weight");FitnessGoal.TONE->tr("Подтянуть тело","Get toned")
    FitnessGoal.ENDURANCE->tr("Выносливость","Endurance")
}
fun activityLabel(factor:Double):String = when(factor){1.2->tr("Низкая","Low");1.375->tr("Лёгкая","Light");1.55->tr("Средняя","Moderate");1.725->tr("Высокая","High");else->tr("Очень высокая","Very high")}
fun bodyLine(p:Profile):String = listOf(if(p.sex=="male")tr("Мужчина","Male") else tr("Женщина","Female"),count(p.age,"год","года","лет","year","years"),
    "${number(p.heightCm)} ${tr("см","cm")}","${number(p.currentWeight,1)} ${tr("кг","kg")}").joinToString(" · ")
fun goalsLine(g:DailyGoals?):String = if(g==null||listOf(g.kcal,g.protein,g.fat,g.carbs,g.waterMl).all{it==null})tr("Цели не заданы","No targets yet") else listOfNotNull(
    g.kcal?.let{"${number(it)} $KCAL"},g.protein?.let{"${tr("Б","P")} ${number(it)}"},g.fat?.let{"${tr("Ж","F")} ${number(it)}"},g.carbs?.let{"${tr("У","C")} ${number(it)}"},
    g.waterMl?.let{"${tr("вода","water")} ${number(it)} $ML"}).joinToString(" · ")

@Composable private fun ProfileHub(vm:AppViewModel,open:(String)->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val settings by vm.settings.collectAsStateWithLifecycle();val revision by vm.revision.collectAsStateWithLifecycle()
    val today=LocalDate.now().toString();val goals=snapshot.day(today).goals;val profile=snapshot.profile;val setup=snapshot.setup
    val dishes=snapshot.customFoods.count{it.recipe!=null}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=20.dp).testTag("profileHub"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        // Header: who the plan is for and today's targets at a glance.
        Column(Modifier.padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(tr("Профиль","Profile"),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            Text(profile?.let(::bodyLine)?:tr("Параметры тела не заданы","Body parameters not set"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            setup?.takeIf{it.appliedOn!=null}?.let{Text(tr("Цель: ","Goal: ")+goalLabel(it.goal).lowercase(Lang.locale)+" · "+tr("${it.daysPerWeek} трен. в неделю по ${it.minutesPerSession} мин","${it.daysPerWeek} sessions a week, ${it.minutesPerSession} min"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)}
        }
        TargetsStrip(goals)
        SetupCard(setup,open)
        GroupLabel(tr("Тело и питание","Body and nutrition"))
        ProfileRow(Icons.Outlined.MonitorWeight,tr("Тело и активность","Body and activity"),profile?.let{"${number(it.currentWeight,1)} ${tr("кг","kg")} · ${tr("активность","activity")} ${activityLabel(it.activity).lowercase(Lang.locale)}"}?:tr("Пол, возраст, рост, вес","Sex, age, height, weight"),"open-${ProfileRoutes.BODY}"){open(ProfileRoutes.BODY)}
        ProfileRow(Icons.Outlined.Restaurant,tr("Цели питания","Nutrition targets"),goalsLine(goals),"open-${ProfileRoutes.TARGETS}"){open(ProfileRoutes.TARGETS)}
        GroupLabel(tr("Мои данные","My data"))
        ProfileRow(Icons.Outlined.Inventory2,tr("Инвентарь","Inventory"),if(dishes==0)tr("Приготовленные блюда","Dishes you cook") else count(dishes,"блюдо","блюда","блюд","dish","dishes"),"open-${ProfileRoutes.INVENTORY}",badge=dishes.takeIf{it>0}?.toString()){open(ProfileRoutes.INVENTORY)}
        val backupWarning=settings.folder.isEmpty()||revision.current!=revision.backedUp
        ProfileRow(Icons.Outlined.Backup,tr("Резервная копия","Backup"),when{settings.folder.isEmpty()->tr("Папка не выбрана","No folder chosen");revision.current!=revision.backedUp->tr("Есть изменения без копии","Changes not backed up");else->tr("Всё сохранено","Everything is backed up")},"open-${ProfileRoutes.BACKUP}",warning=backupWarning){open(ProfileRoutes.BACKUP)}
        GroupLabel(tr("Приложение","App"))
        ProfileRow(Icons.Outlined.SpaceDashboard,tr("Главный экран и вода","Home screen and water"),tr("Карточки обзора, стакан ","Overview cards, glass ")+"${settings.waterMl} $ML","open-${ProfileRoutes.HOME}"){open(ProfileRoutes.HOME)}
        ProfileRow(Icons.Outlined.Palette,tr("Оформление и язык","Appearance and language"),paletteName(settings.appearance.effectivePalette(LocalDate.now()))+" · "+if(Lang.en)"English" else "Русский","open-${ProfileRoutes.LOOK}"){open(ProfileRoutes.LOOK)}
        ProfileRow(Icons.Outlined.Info,tr("О приложении","About"),if(vm.appVersion.isNotBlank())tr("Версия ","Version ")+vm.appVersion else tr("Данные и лицензии","Data and licences"),"open-${ProfileRoutes.ABOUT}"){open(ProfileRoutes.ABOUT)}
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TargetsStrip(goals:DailyGoals?) {
    Panel(Modifier.testTag("todayTargets")) {
        Text(tr("Цели на сегодня","Today's targets"),style=MaterialTheme.typography.titleMedium)
        if(goals==null||listOf(goals.kcal,goals.protein,goals.fat,goals.carbs,goals.waterMl).all{it==null})Text(tr("Пока не заданы — пройдите настройку программы или задайте вручную.","Not set yet — run the program setup or set them yourself."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        else FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOfNotNull(goals.kcal?.let{number(it) to KCAL},goals.protein?.let{"${number(it)} $G" to tr("белок","protein")},goals.fat?.let{"${number(it)} $G" to tr("жиры","fat")},
                goals.carbs?.let{"${number(it)} $G" to tr("углеводы","carbs")},goals.waterMl?.let{"${number(it)} $ML" to tr("вода","water")}).forEach{(value,label)->
                Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.secondaryContainer){Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)){
                    Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSecondaryContainer)}}
            }
        }
    }
}

@Composable private fun SetupCard(setup:ProgramSetup?,open:(String)->Unit) {
    val applied=setup?.appliedOn!=null
    Surface(shape=RoundedCornerShape(24.dp),color=if(applied)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(Icons.Outlined.Tune,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){
                    Text(tr("Настройка программы","Program setup"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                    Text(if(applied)tr("План от ","Plan from ")+dayMonth(setup!!.appliedOn!!)+" · "+goalLabel(setup.goal).lowercase(Lang.locale)
                        else tr("Пара минут вопросов — и приложение посчитает калории, БЖУ, воду и составит тренировки.","A couple of minutes of questions, and the app works out calories, macros, water and a training week."),style=MaterialTheme.typography.bodyMedium)
                }
            }
            if(applied)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={open(ProfileRoutes.PROGRAM)},modifier=Modifier.weight(1f).testTag("openProgram")){Text(tr("Моя программа","My program"))}
                OutlinedButton(onClick={open(ProfileRoutes.SETUP)},modifier=Modifier.weight(1f).testTag("startSetup")){Text(tr("Изменить","Change"))}
            } else Button(onClick={open(ProfileRoutes.SETUP)},modifier=Modifier.fillMaxWidth().testTag("startSetup")){Text(if(setup==null)tr("Начать настройку","Start the setup") else tr("Продолжить настройку","Continue the setup"))}
        }
    }
}

@Composable private fun GroupLabel(text:String) = Text(text.uppercase(Lang.locale),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=8.dp,top=8.dp))

@Composable fun ProfileRow(icon:ImageVector,title:String,subtitle:String,tag:String,badge:String?=null,warning:Boolean=false,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).testTag(tag)) {
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=if(warning)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
            badge?.let{Badge(containerColor=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer){Text(it)}}
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A profile sub-screen: back arrow, title and a scrolling column. */
@Composable fun ProfilePage(title:String,subtitle:String?,back:()->Unit,tag:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start=8.dp,end=16.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=back,modifier=Modifier.testTag("back")){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            Column{Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);subtitle?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp).testTag(tag),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
    }
}

/**
 * First start: language, the program setup as the main way in, then the short manual form (body and goals) that was the
 * whole start screen before, a skip button and a restore link.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun OnboardingForm(vm:AppViewModel,restore:()->Unit,open:(String)->Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val body=rememberBodyFields(null)
    var kcal by rememberSaveable{mutableStateOf("")};var protein by rememberSaveable{mutableStateOf("")};var water by rememberSaveable{mutableStateOf("")}
    var message by remember{mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        LanguageChips(vm)
        Icon(Icons.Outlined.Spa,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary)
        Text(tr("Питание.\nБез лишнего.","Nutrition.\nNothing extra."),style=MaterialTheme.typography.displaySmall)
        Text(tr("Ваш дневник еды, воды и тренировок. Всё хранится на телефоне — без регистрации.","Your food, water and training diary. Everything stays on the phone — no sign-up."),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick={open(ProfileRoutes.SETUP)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("startSetup")){Icon(Icons.Outlined.Tune,null);Spacer(Modifier.width(8.dp));Text(tr("Настроить программу","Set up my program"))}
        Text(tr("Несколько вопросов о теле, цели и времени — и готов план питания и тренировок. Можно пройти позже в профиле.","A few questions about your body, goal and time, and you get a food and training plan. You can also do it later in the profile."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={vm.skipOnboarding()},modifier=Modifier.fillMaxWidth()){Text(tr("Начать без расчёта","Start without a calculation"))}
        TextButton(onClick=restore){Text(tr("У меня уже есть резервная копия","I already have a backup"))}
        Panel {
            SectionTitle(tr("Или коротко вручную","Or briefly by hand"),tr("Для приблизительной оценки калорий на поддержание веса","For a rough estimate of maintenance calories"))
            BodyFieldsView(body)
            TextButton(onClick={runCatching{estimateMaintenance(body.read(null))}.onSuccess{if(it==null)message=tr("Для возраста до 18 лет укажите цель вручную","Under 18, set the goal yourself")else{kcal=number(it);message=tr("Предложение добавлено в поле калорий. Нажмите «Сохранить».","The estimate is in the calories field. Tap “Save”.")}}.onFailure{message=tr("Проверьте возраст, рост и вес","Check age, height and weight")}}){Text(tr("Рассчитать и подставить калории","Calculate and fill in calories"))}
            NumberField(tr("Калории, ккал","Calories, kcal"),kcal){kcal=it}
            NumberField(tr("Белок, г","Protein, g"),protein){protein=it}
            NumberField(tr("Питьевая вода, мл","Drinking water, ml"),water){water=it}
            message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            Button(onClick={runCatching{val p=if(body.blank)null else body.read(null);p to DailyGoals(LocalDate.now().toString(),optional(kcal),optional(protein),optional(water))}.onSuccess{(p,g)->vm.saveProfile(p,g){}}.onFailure{message=it.message?:tr("Проверьте поля","Check the fields")}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Сохранить и начать","Save and start"))}
        }
        Spacer(Modifier.height(16.dp))
    }
}

fun optional(s:String):Double? = if(s.isBlank())null else parseQuantity(s)

/** The body form's text, kept across rotation; [read] checks it and builds the profile. */
class BodyFields(sex:MutableState<String>,age:MutableState<String>,height:MutableState<String>,weight:MutableState<String>,activity:MutableState<Double>) {
    var sex by sex;var age by age;var height by height;var weight by weight;var activity by activity
    val blank get() = age.isBlank()&&height.isBlank()&&weight.isBlank()
    fun read(old:Profile?):Profile { val a=age.toIntOrNull()?:error(tr("Введите возраст","Enter your age"));val w=parseQuantity(weight);return Profile(sex,a,parseQuantity(height),old?.initialWeight?:w,w,activity).also{estimateMaintenance(it)} }
}
@Composable fun rememberBodyFields(profile:Profile?):BodyFields {
    val sex=rememberSaveable(profile){mutableStateOf(profile?.sex?:"male")};val age=rememberSaveable(profile){mutableStateOf(profile?.age?.toString()?:"")}
    val height=rememberSaveable(profile){mutableStateOf(profile?.heightCm?.let(::plain)?:"")};val weight=rememberSaveable(profile){mutableStateOf(profile?.currentWeight?.let(::plain)?:"")}
    val activity=rememberSaveable(profile){mutableDoubleStateOf(profile?.activity?:1.2)}
    return remember(sex,age,height,weight,activity){BodyFields(sex,age,height,weight,activity)}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun BodyFieldsView(b:BodyFields) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=b.sex=="male",onClick={b.sex="male"},label={Text(tr("Мужской","Male"))});FilterChip(selected=b.sex=="female",onClick={b.sex="female"},label={Text(tr("Женский","Female"))})}
    NumberField(tr("Возраст, лет","Age, years"),b.age){b.age=it}
    NumberField(tr("Рост, см","Height, cm"),b.height){b.height=it}
    NumberField(tr("Текущий вес, кг","Current weight, kg"),b.weight){b.weight=it}
    Text(tr("Активность","Activity"),style=MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){ACTIVITY_FACTORS.forEach{f->FilterChip(selected=b.activity==f,onClick={b.activity=f},label={Text(activityLabel(f))})}}
}
@Composable fun NumberField(label:String,value:String,tag:String?=null,change:(String)->Unit) =
    OutlinedTextField(value,{change(it.take(12))},label={Text(label)},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().let{if(tag!=null)it.testTag(tag) else it})

/** The whole interface, dates and system dialogs switch together, so the screen is rebuilt in the new language. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun LanguageChips(vm:AppViewModel) {
    val hostActivity=androidx.activity.compose.LocalActivity.current
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){AppLanguage.entries.forEach{l->FilterChip(selected=Lang.current==l,onClick={if(Lang.current!=l){vm.setLanguage(l);hostActivity?.recreate()}},label={Text(if(l==AppLanguage.RU)"Русский" else "English")},modifier=Modifier.testTag("lang-${l.tag}"))}}
}

@Composable fun CenteredNote(text:String) = Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
