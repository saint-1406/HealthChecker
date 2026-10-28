package az.healthchecker.ui.profile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.inventory.InventorySection
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProfileScreen(vm:AppViewModel,onboarding:Boolean,folder:()->Unit,restore:()->Unit,date:String="",createDish:()->Unit={},editDish:(Food)->Unit={},openAppearance:()->Unit={}) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val settings by vm.settings.collectAsStateWithLifecycle();val revision by vm.revision.collectAsStateWithLifecycle();val backupBusy by vm.backupBusy.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val catalogSize by vm.catalogCount.collectAsStateWithLifecycle();val backupError by vm.backupError.collectAsStateWithLifecycle()
    val profile=snapshot.profile;val goals=snapshot.day(LocalDate.now().toString()).goals
    var sex by rememberSaveable(profile){mutableStateOf(profile?.sex?:"male")};var age by rememberSaveable(profile){mutableStateOf(profile?.age?.toString()?:"")};var height by rememberSaveable(profile){mutableStateOf(profile?.heightCm?.let(::plain)?:"")};var weight by rememberSaveable(profile){mutableStateOf(profile?.currentWeight?.let(::plain)?:"")};var activity by rememberSaveable(profile){mutableDoubleStateOf(profile?.activity?:1.2)}
    var kcal by rememberSaveable(goals){mutableStateOf(goals?.kcal?.let(::plain)?:"")};var protein by rememberSaveable(goals){mutableStateOf(goals?.protein?.let(::plain)?:"")};var water by rememberSaveable(goals){mutableStateOf(goals?.waterMl?.let(::plain)?:"")}
    var message by remember{mutableStateOf<String?>(null)}
    var sources by rememberSaveable{mutableStateOf(false)}
    val hostActivity=androidx.activity.compose.LocalActivity.current
    // The whole interface, dates and system dialogs switch together, so the screen is rebuilt in the new language.
    @Composable fun LanguageChips() = FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){AppLanguage.entries.forEach{l->FilterChip(selected=Lang.current==l,onClick={if(Lang.current!=l){vm.setLanguage(l);hostActivity?.recreate()}},label={Text(if(l==AppLanguage.RU)"Русский" else "English")},modifier=Modifier.testTag("lang-${l.tag}"))}}
    fun readProfile():Profile { val a=age.toIntOrNull()?:error(tr("Введите возраст","Enter your age"));val h=parseQuantity(height);val w=parseQuantity(weight);return Profile(sex,a,h,profile?.initialWeight?:w,w,activity).also{estimateMaintenance(it)} }
    fun optional(s:String)=if(s.isBlank())null else parseQuantity(s)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if(onboarding){LanguageChips();Icon(Icons.Outlined.Spa,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary);Text(tr("Питание.\nБез лишнего.","Nutrition.\nNothing extra."),style=MaterialTheme.typography.displaySmall);Text(tr("Ваш дневник еды и воды. Всё хранится на телефоне — без регистрации.","Your food and water diary. Everything stays on the phone — no sign-up."),color=MaterialTheme.colorScheme.onSurfaceVariant);OutlinedButton(onClick={vm.skipOnboarding()},modifier=Modifier.fillMaxWidth()){Text(tr("Начать без расчёта","Start without a calculation"))};TextButton(onClick=restore){Text(tr("У меня уже есть резервная копия","I already have a backup"))}}
        else {SectionTitle(tr("Профиль","Profile"),tr("Инвентарь блюд, ориентиры, данные и настройки","Dish inventory, targets, data and settings"));InventorySection(vm,date,createDish,editDish)}
        Panel {
            SectionTitle(tr("Параметры тела","Body"),tr("Для приблизительной оценки калорий на поддержание веса","For a rough estimate of maintenance calories"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=sex=="male",onClick={sex="male"},label={Text(tr("Мужской","Male"))});FilterChip(selected=sex=="female",onClick={sex="female"},label={Text(tr("Женский","Female"))})}
            listOf(Triple(tr("Возраст, лет","Age, years"),age,{s:String->age=s}),Triple(tr("Рост, см","Height, cm"),height,{s:String->height=s}),Triple(tr("Текущий вес, кг","Current weight, kg"),weight,{s:String->weight=s})).forEach{(label,value,change)->OutlinedTextField(value,{change(it.take(12))},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}
            profile?.let{Text(tr("Начальный вес: ","Starting weight: ")+"${number(it.initialWeight,1)} ${tr("кг","kg")}",style=MaterialTheme.typography.bodySmall)}
            Text(tr("Активность","Activity"),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(1.2 to tr("Низкая","Low"),1.375 to tr("Лёгкая","Light"),1.55 to tr("Средняя","Moderate"),1.725 to tr("Высокая","High"),1.9 to tr("Очень высокая","Very high")).forEach{(factor,label)->FilterChip(selected=activity==factor,onClick={activity=factor},label={Text(label)})}}
            TextButton(onClick={runCatching{estimateMaintenance(readProfile())}.onSuccess{if(it==null)message=tr("Для возраста до 18 лет укажите цель вручную","Under 18, set the goal yourself")else{kcal=number(it);message=tr("Предложение добавлено в поле калорий. Нажмите «Сохранить».","The estimate is in the calories field. Tap “Save”.")}}.onFailure{message=tr("Проверьте возраст, рост и вес","Check age, height and weight")}}){Text(tr("Рассчитать и подставить калории","Calculate and fill in calories"))}
            Text(tr("Mifflin–St Jeor × $activity. Это ориентир для взрослых, а не ограничение питания.","Mifflin–St Jeor × $activity. A guide for adults, not a diet limit."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            SectionTitle(tr("Дневные цели","Daily goals"),tr("Можно задать вручную. Пустое поле — без цели.","Set them yourself. An empty field means no goal."))
            listOf(Triple(tr("Калории, ккал","Calories, kcal"),kcal,{s:String->kcal=s}),Triple(tr("Белок, г","Protein, g"),protein,{s:String->protein=s}),Triple(tr("Питьевая вода, мл","Drinking water, ml"),water,{s:String->water=s})).forEach{(label,value,change)->OutlinedTextField(value,{change(it.take(12))},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}
            message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            Button(onClick={runCatching{val p=if(age.isBlank()&&height.isBlank()&&weight.isBlank())null else readProfile();p to DailyGoals(LocalDate.now().toString(),optional(kcal),optional(protein),optional(water))}.onSuccess{(p,g)->vm.saveProfile(p,g){message=tr("Сохранено","Saved")}}.onFailure{message=it.message?:tr("Проверьте поля","Check the fields")}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(onboarding)tr("Сохранить и начать","Save and start")else tr("Сохранить","Save"))}
            Text(tr("Изменения целей действуют с сегодняшнего дня. Прошлые дни сохраняют свои цели.","Goal changes apply from today. Past days keep their goals."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!onboarding){
            Panel {
                SectionTitle(tr("Резервная копия","Backup"),tr("Чтобы дневник пережил удаление приложения","So the diary survives reinstalling the app"))
                Text(tr("Выберите папку в памяти телефона. После переустановки откройте сохранённый JSON-файл через «Восстановить». Копия содержит личные данные.","Choose a folder on the phone. After reinstalling, open the saved JSON file with “Restore”. The backup contains personal data."),style=MaterialTheme.typography.bodyMedium)
                if(settings.folder.isEmpty())Text(tr("Папка ещё не выбрана","No folder chosen yet"),color=MaterialTheme.colorScheme.error)else Text(tr("Папка подключена","Folder connected"),color=MaterialTheme.colorScheme.primary)
                Text(if(settings.lastBackup==0L)tr("Успешных копий пока нет","No successful backups yet") else tr("Последняя копия: ","Last backup: ")+Instant.ofEpochMilli(settings.lastBackup).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if(Lang.en)"MMM d, HH:mm" else "d MMM, HH:mm",Lang.locale)),style=MaterialTheme.typography.bodySmall)
                if(revision.current!=revision.backedUp)Text(tr("Есть изменения без резервной копии","Some changes are not backed up yet"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                backupError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                OutlinedButton(onClick=folder,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.FolderOpen,null);Spacer(Modifier.width(8.dp));Text(tr("Выбрать папку","Choose folder"))}
                Button(onClick={vm.backupNow()},enabled=settings.folder.isNotEmpty()&&!backupBusy,modifier=Modifier.fillMaxWidth()){Text(if(backupBusy)tr("Сохранение…","Saving…") else tr("Создать копию сейчас","Back up now"))}
                TextButton(onClick=restore,modifier=Modifier.fillMaxWidth()){Text(tr("Восстановить из файла","Restore from file"))}
            }
            Panel{
                SectionTitle(tr("Оформление и язык","Appearance and language"),tr("Тема: ","Theme: ")+paletteName(settings.appearance.effectivePalette(LocalDate.now()))+" · "+when(settings.theme){"light"->tr("светлая","light");"dark"->tr("тёмная","dark");else->tr("как в системе","as the system")}+(if(settings.appearance.hasBackground)tr(" · свой фон"," · own background") else ""))
                Button(onClick=openAppearance,modifier=Modifier.fillMaxWidth().testTag("openAppearance")){Icon(Icons.Outlined.Palette,null);Spacer(Modifier.width(8.dp));Text(tr("Темы, цвета и фон","Themes, colours and background"))}
                Text(tr("Язык приложения","App language"),style=MaterialTheme.typography.titleSmall)
                LanguageChips()
            }
            Panel {SectionTitle(tr("Каталог продуктов","Food catalog"),"$catalogSize ${tr("позиций · работает без интернета","foods · works offline")}");Text(tr("У каждого продукта есть русское и английское название, поиск понимает оба языка и азербайджанские слова. Повседневные продукты показываются первыми. Региональные бренды можно добавить по этикетке.","Every food has a Russian and an English name; search understands both languages and Azerbaijani words. Everyday foods come first. Add regional brands from the label."),style=MaterialTheme.typography.bodySmall);TextButton(onClick={sources=true}){Text(tr("Источники и точность","Sources and accuracy"))}}
            // Matches the update file's number, so the user can see which update the phone has.
            if(vm.appVersion.isNotBlank())Text("HealthChecker · "+tr("версия ","version ")+vm.appVersion,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth().testTag("appVersion"),textAlign=TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
    }
    if(sources)AlertDialog(onDismissRequest={sources=false},title={Text(tr("О данных","About the data"))},text={Text(tr(
        "USDA FoodData Central · SR Legacy, апрель 2018. Получено 18.10.2026. Данные public domain.\nfdc.nal.usda.gov\nРусские названия — перевод английских описаний USDA; значения не менялись. Клетчатка — USDA «Fiber, total dietary».\n\nПорции и пищевая ценность усреднённые. Qatıq находится через натуральный йогурт как приблизительный аналог, а не конкретный местный бренд. Ресторанные блюда Баку — расчётные оценки; как получено каждое значение, написано в карточке продукта.\n\nOpen Food Facts был недоступен; его данные не включены.\n\nУпражнения: 213 домашних упражнений, разметка двух видеотренировок. Расход энергии — MET из 2024 Adult Compendium of Physical Activities (pacompendium.com): ккал ≈ MET × вес × время. Шаги — ходьба 3,8 MET (код 17190), длина шага ≈ 0,415 × рост. Недельные ориентиры — рекомендации ВОЗ 2020.\n\nПриложение не отправляет дневник на сервер; в интернет выходит только проигрыватель YouTube и только по вашему нажатию.",
        "USDA FoodData Central · SR Legacy, April 2018. Retrieved 18 Oct 2026. Public domain data.\nfdc.nal.usda.gov\nRussian names translate the USDA English descriptions; values are unchanged. Fibre is USDA “Fiber, total dietary”.\n\nPortions and nutrition values are averages. Qatıq is found through plain yogurt as an approximate equivalent, not a specific local brand. Baku restaurant dishes are calculated estimates; each food card says how its values were derived.\n\nOpen Food Facts was unavailable; its data is not included.\n\nExercises: 213 home exercises and the timelines of two workout videos. Energy — MET from the 2024 Adult Compendium of Physical Activities (pacompendium.com): kcal ≈ MET × weight × time. Steps — walking at 3.8 MET (code 17190), step length ≈ 0.415 × height. Weekly targets — WHO 2020 guidelines.\n\nThe app never sends your diary to a server; only the YouTube player goes online, and only when you tap."))},confirmButton={TextButton(onClick={sources=false}){Text(tr("Понятно","Got it"))}})
}
