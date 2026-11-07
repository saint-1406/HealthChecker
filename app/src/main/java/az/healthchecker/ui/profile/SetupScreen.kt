package az.healthchecker.ui.profile
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.training.prescriptionLabel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.format.TextStyle

private val draftJson=Json{encodeDefaults=true}
private const val STEPS=7

fun limitationLabel(l:Limitation):String = when(l){
    Limitation.KNEE->tr("Колени","Knees");Limitation.LOWER_BACK->tr("Поясница","Lower back");Limitation.SHOULDER->tr("Плечи","Shoulders");Limitation.WRIST->tr("Запястья","Wrists")
    Limitation.NECK->tr("Шея","Neck");Limitation.HERNIA->tr("Грыжа","Hernia");Limitation.HIGH_BLOOD_PRESSURE->tr("Повышенное давление","High blood pressure")
    Limitation.PREGNANCY->tr("Беременность или после родов","Pregnancy or postpartum")
}
fun equipmentLabel(e:HomeEquipment):String = when(e){
    HomeEquipment.DUMBBELLS->tr("Гантели","Dumbbells");HomeEquipment.KETTLEBELL->tr("Гиря","Kettlebell");HomeEquipment.BARBELL->tr("Штанга","Barbell")
    HomeEquipment.PULLUP_BAR->tr("Турник","Pull-up bar");HomeEquipment.DIP_BARS->tr("Брусья","Dip bars");HomeEquipment.BENCH->tr("Скамья","Bench")
    HomeEquipment.BANDS->tr("Резинки","Resistance bands");HomeEquipment.MAT->tr("Коврик","Mat");HomeEquipment.JUMP_ROPE->tr("Скакалка","Jump rope")
}
private fun goalHint(g:FitnessGoal):String = when(g){
    FitnessGoal.BULK->tr("Больше мышц и веса, профицит калорий","More muscle and weight, a calorie surplus");FitnessGoal.GAIN_WEIGHT->tr("Прибавить в весе, если его не хватает","Put on weight if you are underweight")
    FitnessGoal.MUSCLE->tr("Мышцы без лишнего жира, небольшой профицит","Muscle without extra fat, a small surplus");FitnessGoal.CUT->tr("Меньше жира, умеренный дефицит","Less fat, a moderate deficit")
    FitnessGoal.MAINTAIN->tr("Сохранить вес и самочувствие","Keep your weight and wellbeing");FitnessGoal.TONE->tr("Подтянуть тело, лёгкий дефицит","Get toned, a slight deficit")
    FitnessGoal.ENDURANCE->tr("Дольше бегать, ходить, не задыхаться","Last longer without getting out of breath")
}
private fun activityHint(f:Double):String = when(f){
    1.2->tr("Сидячая работа, почти без движения","Desk job, hardly any movement");1.375->tr("Немного ходьбы, 1–3 тренировки в неделю","Some walking, 1–3 workouts a week")
    1.55->tr("Много на ногах или 3–5 тренировок","On your feet a lot or 3–5 workouts");1.725->tr("Физическая работа или 6–7 тренировок","Physical job or 6–7 workouts")
    else->tr("Тяжёлый труд и спорт каждый день","Hard labour and sport every day")
}
fun sessionLabel(t:SessionType):String = when(t){SessionType.FULL_BODY->tr("Всё тело","Full body");SessionType.UPPER->tr("Верх тела","Upper body");SessionType.LOWER->tr("Ноги и ягодицы","Legs and glutes");SessionType.CARDIO->tr("Кардио","Cardio");SessionType.MOBILITY->tr("Мобильность и растяжка","Mobility and stretching")}
private fun weekdayName(d:Int):String = DayOfWeek.of(d).getDisplayName(TextStyle.FULL_STANDALONE,Lang.locale).replaceFirstChar{it.titlecase(Lang.locale)}

/**
 * "Program setup": seven short steps, then the plan. Nothing changes in the app until the user taps "Apply"; then the
 * answers, body parameters, today's targets and training settings are saved together.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SetupScreen(vm:AppViewModel,back:()->Unit,done:()->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val catalog by vm.exercises.collectAsStateWithLifecycle()
    val initial=remember{initialSetup(snapshot.setup,snapshot.profile,snapshot.trainingPrefs)}
    var raw by rememberSaveable{mutableStateOf(draftJson.encodeToString(initial))}
    val s=remember(raw){draftJson.decodeFromString<ProgramSetup>(raw)}
    fun update(f:(ProgramSetup)->ProgramSetup){raw=draftJson.encodeToString(f(s))}
    var step by rememberSaveable{mutableIntStateOf(0)}
    var age by rememberSaveable{mutableStateOf(initial.age.toString())};var height by rememberSaveable{mutableStateOf(plain(initial.heightCm))}
    var weight by rememberSaveable{mutableStateOf(plain(initial.weightKg))};var target by rememberSaveable{mutableStateOf(initial.targetWeightKg?.let(::plain)?:"")}
    var message by remember{mutableStateOf<String?>(null)}
    BackHandler(step>0){step--;message=null}
    fun next() {
        if(step==0){
            val checked=runCatching{s.copy(age=age.trim().toIntOrNull()?:error(tr("Введите возраст","Enter your age")),heightCm=parseQuantity(height),weightKg=parseQuantity(weight),targetWeightKg=optional(target)).also(::validateSetup)}
            checked.onSuccess{update{_->it};message=null;step++}.onFailure{message=it.message?.takeIf{m->m.isNotBlank()}?:tr("Проверьте возраст, рост и вес","Check age, height and weight")}
        } else {message=null;step++}
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start=8.dp,end=16.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(step>0){step--;message=null}else back()},modifier=Modifier.testTag("back")){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            Column(Modifier.weight(1f)){
                Text(tr("Настройка программы","Program setup"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(tr("Шаг ${step+1} из $STEPS","Step ${step+1} of $STEPS"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(progress={(step+1f)/STEPS},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp).testTag("setupStep$step"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(step){
                0->Panel {
                    SectionTitle(tr("О вас","About you"),tr("Нужно для расчёта калорий","Needed to estimate calories"))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilterChip(selected=s.sex=="male",onClick={update{it.copy(sex="male",limitations=it.limitations-Limitation.PREGNANCY)}},label={Text(tr("Мужской","Male"))},modifier=Modifier.testTag("sex-male"))
                        FilterChip(selected=s.sex=="female",onClick={update{it.copy(sex="female")}},label={Text(tr("Женский","Female"))},modifier=Modifier.testTag("sex-female"))
                    }
                    NumberField(tr("Возраст, лет","Age, years"),age,"setupAge"){age=it}
                    NumberField(tr("Рост, см","Height, cm"),height,"setupHeight"){height=it}
                    NumberField(tr("Текущий вес, кг","Current weight, kg"),weight,"setupWeight"){weight=it}
                    NumberField(tr("Желаемый вес, кг (необязательно)","Target weight, kg (optional)"),target,"setupTarget"){target=it}
                }
                1->Panel {
                    SectionTitle(tr("Ограничения и здоровье","Limitations and health"),tr("Отметьте всё, что относится к вам. Упражнения с нагрузкой на эти места не попадут в программу.","Tick everything that applies. Exercises that load these areas stay out of the program."))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        FilterChip(selected=s.limitations.isEmpty(),onClick={update{it.copy(limitations=emptySet())}},label={Text(tr("Нет ограничений","None"))},modifier=Modifier.testTag("limit-none"))
                        Limitation.entries.filter{it!=Limitation.PREGNANCY||s.sex=="female"}.forEach{l->
                            FilterChip(selected=l in s.limitations,onClick={update{it.copy(limitations=if(l in it.limitations)it.limitations-l else it.limitations+l)}},label={Text(limitationLabel(l))},modifier=Modifier.testTag("limit-${l.name}"))
                        }
                    }
                    if(s.limitations.any{it==Limitation.PREGNANCY||it==Limitation.HIGH_BLOOD_PRESSURE||it==Limitation.HERNIA})Warning(tr("С этими состояниями нагрузку и питание нужно согласовать с врачом. Программа будет щадящей.","With these conditions, agree on exercise and diet with your doctor. The program will be gentle."))
                }
                2->Panel {
                    SectionTitle(tr("Инвентарь дома","Equipment at home"),tr("Можно выбрать несколько","Choose any that apply"))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        FilterChip(selected=s.equipment.isEmpty(),onClick={update{it.copy(equipment=emptySet())}},label={Text(tr("Ничего нет","Nothing"))},modifier=Modifier.testTag("equip-none"))
                        HomeEquipment.entries.forEach{e->
                            FilterChip(selected=e in s.equipment,onClick={update{it.copy(equipment=if(e in it.equipment)it.equipment-e else it.equipment+e)}},label={Text(equipmentLabel(e))},modifier=Modifier.testTag("equip-${e.name}"))
                        }
                    }
                }
                3->Panel {
                    SectionTitle(tr("Ваша цель","Your goal"))
                    FitnessGoal.entries.forEach{g->ChoiceRow(goalLabel(g),goalHint(g),s.goal==g,"goal-${g.name}"){update{it.copy(goal=g)}}}
                }
                4->{
                    Panel {
                        SectionTitle(tr("Активность в течение дня","Daily activity"),tr("Вне запланированных тренировок — работа, ходьба, дела","Outside planned workouts — work, walking, chores"))
                        ACTIVITY_FACTORS.forEach{f->ChoiceRow(activityLabel(f),activityHint(f),s.activity==f,"activity-$f"){update{it.copy(activity=f)}}}
                    }
                    Panel {
                        SectionTitle(tr("Опыт тренировок","Training experience"))
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..3).forEach{l->FilterChip(selected=s.experience==l,onClick={update{it.copy(experience=l)}},label={Text(levelLabel(l))},modifier=Modifier.testTag("level-$l"))}}
                    }
                }
                5->Panel {
                    SectionTitle(tr("Время на тренировки","Time for training"))
                    Text(tr("Дней в неделю","Days a week"),style=MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..7).forEach{d->FilterChip(selected=s.daysPerWeek==d,onClick={update{it.copy(daysPerWeek=d)}},label={Text("$d")},modifier=Modifier.testTag("days-$d"))}}
                    Text(tr("Минут за тренировку","Minutes per session"),style=MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(15,20,30,45,60,90).forEach{m->FilterChip(selected=s.minutesPerSession==m,onClick={update{it.copy(minutesPerSession=m)}},label={Text("$m ${tr("мин","min")}")},modifier=Modifier.testTag("minutes-$m"))}}
                }
                else->{
                    if(catalog.isEmpty())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(24.dp));Text(tr("Подбираем упражнения…","Choosing exercises…"))}
                    else {
                        val plan=remember(s,catalog){buildPlan(catalog,s)}
                        PlanView(plan,null)
                        Panel {
                            Text(tr("Что изменится","What changes"),style=MaterialTheme.typography.titleMedium)
                            Text(tr("С сегодняшнего дня: калории, БЖУ и вода; параметры тела; гантели и уровень в тренировках. Прошлые дни сохраняют свои цели, начальный вес не меняется. Ответы можно изменить в любой момент в профиле.","From today: calories, macros and water; body parameters; dumbbells and level in training. Past days keep their goals and the starting weight stays. You can change the answers any time in the profile."),style=MaterialTheme.typography.bodySmall)
                            Button(onClick={vm.applySetup(plan,done)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("applySetup")){Icon(Icons.Outlined.Check,null);Spacer(Modifier.width(8.dp));Text(tr("Применить план","Apply the plan"))}
                            TextButton(onClick={step=0},modifier=Modifier.fillMaxWidth()){Text(tr("Изменить ответы","Change the answers"))}
                        }
                    }
                }
            }
            message?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("setupMessage"))}
        }
        if(step<STEPS-1)Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            if(step>0)OutlinedButton(onClick={step--;message=null},modifier=Modifier.weight(1f)){Text(tr("Назад","Back"))}
            Button(onClick=::next,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("setupNext")){Text(if(step==STEPS-2)tr("Показать план","Show the plan") else tr("Далее","Next"))}
        }
    }
}

@Composable private fun ChoiceRow(title:String,hint:String,selected:Boolean,tag:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected,onClick=onClick,role=Role.RadioButton).padding(vertical=6.dp).testTag(tag),verticalAlignment=Alignment.CenterVertically){
        RadioButton(selected=selected,onClick=null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyLarge);Text(hint,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable private fun Warning(text:String,strong:Boolean=true) {
    Surface(shape=RoundedCornerShape(16.dp),color=if(strong)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(if(strong)Icons.Outlined.HealthAndSafety else Icons.Outlined.Info,null,tint=if(strong)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text,style=MaterialTheme.typography.bodySmall,color=if(strong)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Calories, macros, water, the training week and advice; [openExercise] lets a tap log the exercise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PlanView(plan:ProgramPlan,openExercise:((Exercise,Prescription)->Unit)?) {
    val n=plan.nutrition
    Warning(setupDisclaimer()+if(plan.consultDoctor)"\n"+tr("По вашим ответам сначала обсудите нагрузку и питание с врачом.","Based on your answers, discuss exercise and diet with a doctor first.") else "",plan.consultDoctor)
    Panel(Modifier.testTag("planNutrition")) {
        Text(tr("ПИТАНИЕ НА ДЕНЬ","DAILY NUTRITION"),style=MaterialTheme.typography.labelMedium)
        if(n.kcal!=null){
            Text("${number(n.kcal)} $KCAL",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("planKcal"))
            Text(tr("Поддержание ≈ ","Maintenance ≈ ")+"${number(n.maintenance)} $KCAL · "+tr("базовый обмен ≈ ","basal rate ≈ ")+"${number(n.bmr)} $KCAL",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(tr("Белок","Protein") to n.protein,tr("Жиры","Fat") to n.fat,tr("Углеводы","Carbs") to n.carbs).forEach{(label,v)->
                    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.secondaryContainer){Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)){Text("${number(v)} $G",style=MaterialTheme.typography.titleMedium);Text(label,style=MaterialTheme.typography.labelSmall)}}
                }
            }
            n.weeklyChangeKg?.takeIf{kotlin.math.abs(it)>=0.05}?.let{Text(tr("Ожидаемо ","Expected ")+(if(it>0)"+" else "−")+number(kotlin.math.abs(it),2)+tr(" кг в неделю"," kg a week"),style=MaterialTheme.typography.bodySmall)}
        } else Text(tr("Калории и БЖУ не рассчитываются для возраста до 18 лет.","Calories and macros are not calculated under 18."),style=MaterialTheme.typography.bodyMedium)
        Text(tr("Вода: ","Water: ")+"${number(n.waterMl)} $ML",style=MaterialTheme.typography.titleMedium)
    }
    Panel(Modifier.testTag("planProgram")) {
        Text(tr("ТРЕНИРОВКИ НА НЕДЕЛЮ","TRAINING WEEK"),style=MaterialTheme.typography.labelMedium)
        plan.program.sessions.forEach{session->
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text("${weekdayName(session.weekday)} · ${sessionLabel(session.type)} · ≈ ${number(session.minutes)} ${tr("мин","min")}",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                session.items.forEach{r->
                    val line="${r.exercise.displayName} — ${prescriptionLabel(r.prescription)}"
                    if(openExercise!=null)Text(line,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.fillMaxWidth().selectable(false,onClick={openExercise(r.exercise,r.prescription)},role=Role.Button).padding(vertical=4.dp))
                    else Text("• $line",style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if(plan.program.sessions.all{it.items.isEmpty()})Text(tr("Подходящих упражнений не нашлось — начните с прогулок и согласуйте нагрузку с врачом.","No suitable exercises found — start with walks and agree on exercise with a doctor."))
    }
    if(plan.tips.isNotEmpty())Panel(Modifier.testTag("planTips")) {
        Text(tr("РЕКОМЕНДАЦИИ","RECOMMENDATIONS"),style=MaterialTheme.typography.labelMedium)
        plan.tips.forEach{Text("• $it",style=MaterialTheme.typography.bodyMedium)}
    }
}

/** Profile → my program: the plan from the saved answers, with exercises that open the workout form. */
@Composable fun ProgramScreen(vm:AppViewModel,back:()->Unit,edit:()->Unit,openExercise:(Exercise,Prescription)->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val catalog by vm.exercises.collectAsStateWithLifecycle()
    val setup=snapshot.setup
    ProfilePage(tr("Моя программа","My program"),setup?.appliedOn?.let{tr("План от ","Plan from ")+dayMonth(it)},back,"programScreen") {
        if(setup==null){Text(tr("Программа ещё не настроена.","The program is not set up yet."));Button(onClick=edit,modifier=Modifier.fillMaxWidth()){Text(tr("Начать настройку","Start the setup"))}}
        else if(catalog.isEmpty())CircularProgressIndicator()
        else {
            val plan=remember(setup,catalog){buildPlan(catalog,setup)}
            Text(goalLabel(setup.goal)+" · "+tr("${setup.daysPerWeek} дн. × ${setup.minutesPerSession} мин","${setup.daysPerWeek} days × ${setup.minutesPerSession} min")+" · "+levelLabel(setup.experience).lowercase(Lang.locale),style=MaterialTheme.typography.bodyMedium)
            if(setup.limitations.isNotEmpty())Text(tr("Учтено: ","Taken into account: ")+setup.limitations.joinToString{limitationLabel(it).lowercase(Lang.locale)},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Нажмите упражнение, чтобы записать его в дневник тренировок.","Tap an exercise to log it."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            PlanView(plan,openExercise)
            OutlinedButton(onClick=edit,modifier=Modifier.fillMaxWidth().testTag("editSetup")){Icon(Icons.Outlined.Tune,null);Spacer(Modifier.width(8.dp));Text(tr("Изменить ответы","Change the answers"))}
        }
    }
}
