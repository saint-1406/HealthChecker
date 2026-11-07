package az.healthchecker.ui.profile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.barcode.OfflineBarcodeCatalog
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter

/** Profile → body: sex, age, height, weight and activity. Saving changes only these, never the goals. */
@Composable fun BodyScreen(vm:AppViewModel,back:()->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    val profile=snapshot.profile;val body=rememberBodyFields(profile)
    var message by remember{mutableStateOf<String?>(null)}
    ProfilePage(tr("Тело и активность","Body and activity"),tr("Для оценки калорий и расхода на тренировках","For calorie and workout estimates"),back,"bodyScreen") {
        Panel {
            BodyFieldsView(body)
            profile?.let{Text(tr("Начальный вес: ","Starting weight: ")+"${number(it.initialWeight,1)} ${tr("кг","kg")}",style=MaterialTheme.typography.bodySmall)}
            message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            Button(onClick={runCatching{body.read(profile)}.onSuccess{vm.saveBody(it){message=tr("Сохранено","Saved")}}.onFailure{message=if(body.age.toIntOrNull()==null)tr("Введите возраст","Enter your age") else tr("Проверьте возраст, рост и вес","Check age, height and weight")}},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("saveBody")){Text(tr("Сохранить","Save"))}
            Text(tr("Цели питания здесь не меняются — пересчитать их можно в «Целях питания» или в настройке программы.","Nutrition targets do not change here — recalculate them under “Nutrition targets” or in the program setup."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Profile → nutrition targets: calories, protein, fat, carbs and water from today on. */
@Composable fun TargetsScreen(vm:AppViewModel,back:()->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    val goals=snapshot.day(LocalDate.now().toString()).goals;val profile=snapshot.profile
    var kcal by rememberSaveable(goals){mutableStateOf(goals?.kcal?.let(::plain)?:"")};var protein by rememberSaveable(goals){mutableStateOf(goals?.protein?.let(::plain)?:"")}
    var fat by rememberSaveable(goals){mutableStateOf(goals?.fat?.let(::plain)?:"")};var carbs by rememberSaveable(goals){mutableStateOf(goals?.carbs?.let(::plain)?:"")}
    var water by rememberSaveable(goals){mutableStateOf(goals?.waterMl?.let(::plain)?:"")}
    var message by remember{mutableStateOf<String?>(null)}
    ProfilePage(tr("Цели питания","Nutrition targets"),tr("Пустое поле — без цели","An empty field means no target"),back,"targetsScreen") {
        Panel {
            NumberField(tr("Калории, ккал","Calories, kcal"),kcal){kcal=it}
            NumberField(tr("Белок, г","Protein, g"),protein){protein=it}
            NumberField(tr("Жиры, г","Fat, g"),fat){fat=it}
            NumberField(tr("Углеводы, г","Carbs, g"),carbs){carbs=it}
            NumberField(tr("Питьевая вода, мл","Drinking water, ml"),water){water=it}
            if(profile!=null)TextButton(onClick={runCatching{estimateMaintenance(profile)}.onSuccess{if(it==null)message=tr("Для возраста до 18 лет укажите цель вручную","Under 18, set the goal yourself") else{kcal=number(it);message=tr("Калории на поддержание подставлены. Нажмите «Сохранить».","Maintenance calories filled in. Tap “Save”.")}}}){Text(tr("Подставить калории на поддержание","Fill in maintenance calories"))}
            Text(tr("Mifflin–St Jeor × активность. Это ориентир для взрослых, а не ограничение питания.","Mifflin–St Jeor × activity. A guide for adults, not a diet limit."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            Button(onClick={runCatching{DailyGoals(LocalDate.now().toString(),optional(kcal),optional(protein),optional(water),fat=optional(fat),carbs=optional(carbs))}.onSuccess{vm.saveGoals(it){message=tr("Сохранено","Saved")}}.onFailure{message=tr("Проверьте поля","Check the fields")}},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("saveTargets")){Text(tr("Сохранить","Save"))}
            Text(tr("Изменения целей действуют с сегодняшнего дня. Прошлые дни сохраняют свои цели.","Goal changes apply from today. Past days keep their goals."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Profile → home screen: which overview cards are shown and the quick water glass. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun HomeSettingsScreen(vm:AppViewModel,back:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    ProfilePage(tr("Главный экран и вода","Home screen and water"),null,back,"homeScreen") {
        Panel {
            SectionTitle(tr("Карточки обзора","Overview cards"),tr("Оставьте на главном экране то, чем пользуетесь. Карточки можно вернуть здесь.","Keep the cards you use on the main screen. You can bring them back here."))
            listOf(Triple("weight",tr("Вес","Weight"),settings.overviewCards.weight),Triple("steps",tr("Шаги","Steps"),settings.overviewCards.steps),Triple("training",tr("Тренировки","Training"),settings.overviewCards.training)).forEach{(key,label,checked)->
                val tag=when(key){"weight"->"showWeight";"steps"->"showSteps";else->"showTraining"}
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=checked,role=Role.Switch,onValueChange={vm.showOverviewCard(key,it)}).testTag(tag),verticalAlignment=Alignment.CenterVertically){Text(label,modifier=Modifier.weight(1f));Switch(checked=checked,onCheckedChange=null)}
            }
        }
        Panel {
            Text(tr("Стакан для быстрого добавления воды","Glass size for quick water logging"),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(200,250,500).forEach{ml->FilterChip(selected=settings.waterMl==ml,onClick={vm.waterSize(ml)},label={Text("$ml $ML")},modifier=Modifier.heightIn(min=48.dp))}}
        }
    }
}

/** Profile → backup: folder, back up now, restore. */
@Composable fun BackupScreen(vm:AppViewModel,folder:()->Unit,restore:()->Unit,back:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle();val revision by vm.revision.collectAsStateWithLifecycle();val backupBusy by vm.backupBusy.collectAsStateWithLifecycle();val backupError by vm.backupError.collectAsStateWithLifecycle()
    ProfilePage(tr("Резервная копия","Backup"),tr("Чтобы дневник пережил удаление приложения","So the diary survives reinstalling the app"),back,"backupScreen") {
        Panel {
            Text(tr("Выберите папку в памяти телефона. После переустановки откройте сохранённый ZIP-файл через «Восстановить». Прежние JSON-копии тоже принимаются. Копия содержит дневник, находки, фото, места, записанные маршруты и ответы настройки программы.","Choose a folder on the phone. After reinstalling, open the saved ZIP file with “Restore”. Older JSON backups are also accepted. Backups include your diary, findings, photos, places, recorded routes and program setup answers."),style=MaterialTheme.typography.bodyMedium)
            if(settings.folder.isEmpty())Text(tr("Папка ещё не выбрана","No folder chosen yet"),color=MaterialTheme.colorScheme.error)else Text(tr("Папка подключена","Folder connected"),color=MaterialTheme.colorScheme.primary)
            Text(if(settings.lastBackup==0L)tr("Успешных копий пока нет","No successful backups yet") else tr("Последняя копия: ","Last backup: ")+Instant.ofEpochMilli(settings.lastBackup).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if(Lang.en)"MMM d, HH:mm" else "d MMM, HH:mm",Lang.locale)),style=MaterialTheme.typography.bodySmall)
            if(revision.current!=revision.backedUp)Text(tr("Есть изменения без резервной копии","Some changes are not backed up yet"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            backupError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            OutlinedButton(onClick=folder,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.FolderOpen,null);Spacer(Modifier.width(8.dp));Text(tr("Выбрать папку","Choose folder"))}
            Button(onClick={vm.backupNow()},enabled=settings.folder.isNotEmpty()&&!backupBusy,modifier=Modifier.fillMaxWidth()){Text(if(backupBusy)tr("Сохранение…","Saving…") else tr("Создать копию сейчас","Back up now"))}
            TextButton(onClick=restore,modifier=Modifier.fillMaxWidth()){Text(tr("Восстановить из файла","Restore from file"))}
        }
    }
}

/** Profile → appearance and language. Themes, colours and the background have their own screen. */
@Composable fun LookScreen(vm:AppViewModel,openAppearance:()->Unit,back:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    ProfilePage(tr("Оформление и язык","Appearance and language"),null,back,"lookScreen") {
        Panel {
            SectionTitle(tr("Оформление","Appearance"),tr("Тема: ","Theme: ")+paletteName(settings.appearance.effectivePalette(LocalDate.now()))+" · "+when(settings.theme){"light"->tr("светлая","light");"dark"->tr("тёмная","dark");else->tr("как в системе","as the system")}+(if(settings.appearance.hasBackground)tr(" · свой фон"," · own background") else ""))
            Button(onClick=openAppearance,modifier=Modifier.fillMaxWidth().testTag("openAppearance")){Icon(Icons.Outlined.Palette,null);Spacer(Modifier.width(8.dp));Text(tr("Темы, цвета и фон","Themes, colours and background"))}
        }
        Panel {
            SectionTitle(tr("Язык приложения","App language"))
            LanguageChips(vm)
        }
    }
}

/** Profile → about: version, the bundled data and its licences. An app update entry belongs here too. */
@Composable fun AboutScreen(vm:AppViewModel,back:()->Unit,extra:@Composable ColumnScope.()->Unit={}) {
    val catalogSize by vm.catalogCount.collectAsStateWithLifecycle()
    var sources by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    val barcodeManifest by produceState<az.healthchecker.barcode.BarcodeManifest?>(null){value=withContext(Dispatchers.IO){runCatching{OfflineBarcodeCatalog.shared(context).manifest}.getOrNull()}}
    ProfilePage(tr("О приложении","About"),null,back,"aboutScreen") {
        Panel {
            SectionTitle("HealthChecker")
            // Matches the update file's number, so the user can see which update the phone has.
            if(vm.appVersion.isNotBlank())Text(tr("Версия ","Version ")+vm.appVersion,style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("appVersionLine"))
            extra()
        }
        Panel {SectionTitle(tr("Каталог продуктов","Food catalog"),"$catalogSize ${tr("позиций · работает без интернета","foods · works offline")}");Text(tr("У каждого продукта есть русское и английское название, поиск понимает оба языка и азербайджанские слова. Повседневные продукты показываются первыми. Региональные бренды можно добавить по этикетке.","Every food has a Russian and an English name; search understands both languages and Azerbaijani words. Everyday foods come first. Add regional brands from the label."),style=MaterialTheme.typography.bodySmall);TextButton(onClick={sources=true}){Text(tr("Источники и точность","Sources and accuracy"))}}
        barcodeManifest?.let{m->Panel {
            SectionTitle(tr("Товары по штрихкоду","Products by barcode"),"${m.products} "+tr("карточек · без интернета","cards · offline"))
            Text(tr("Встроенный снимок Open Food Facts от ","Bundled Open Food Facts snapshot: ")+m.snapshotDate+tr(". Сканируйте упаковку и сверьте этикетку. Отсутствующий товар можно добавить вручную с фото.",". Scan a package and verify the label. Missing products can be added manually with a photo."),style=MaterialTheme.typography.bodySmall)
            Text("Open Food Facts · ODbL 1.0 · DbCL 1.0",style=MaterialTheme.typography.bodySmall)
        }}
        Panel {
            SectionTitle(tr("Офлайн-карта Азербайджана","Offline Azerbaijan map"))
            Text(tr("Карта: OpenStreetMap contributors · ODbL 1.0; снимок Mapsforge от 26.09.2026. Данные карты могут быть неполными. GPS работает по кнопке начала прогулки.\nwww.openstreetmap.org/copyright\ndownload.mapsforge.org/maps/v5/asia/azerbaijan.map", "Map: OpenStreetMap contributors · ODbL 1.0; Mapsforge snapshot dated 26 Sep 2026. Map data may be incomplete. GPS recording starts only when you start a walk.\nwww.openstreetmap.org/copyright\ndownload.mapsforge.org/maps/v5/asia/azerbaijan.map"),style=MaterialTheme.typography.bodySmall)
            Text("Mapsforge 0.25.0 · LGPL v3 (waiver §4d/e) · github.com/mapsforge/mapsforge\nAndroidSVG 1.4 · Apache 2.0 · © Paul LeBeau, CaveRock Software\n"+tr("Полные лицензии включены в APK: assets/licenses.","Full license texts are bundled in the APK: assets/licenses."),style=MaterialTheme.typography.bodySmall)
        }
        if(vm.appVersion.isNotBlank())Text("HealthChecker · "+tr("версия ","version ")+vm.appVersion,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth().testTag("appVersion"),textAlign=TextAlign.Center)
    }
    if(sources)AlertDialog(onDismissRequest={sources=false},title={Text(tr("О данных","About the data"))},text={Text(tr(
        "USDA FoodData Central · SR Legacy, апрель 2018. Получено 18.10.2026. Данные public domain.\nfdc.nal.usda.gov\nРусские названия — перевод английских описаний USDA; значения не менялись. Клетчатка — USDA «Fiber, total dietary».\n\nПорции и пищевая ценность усреднённые. Qatıq находится через натуральный йогурт как приблизительный аналог, а не конкретный местный бренд. Ресторанные блюда Баку — расчётные оценки; как получено каждое значение, написано в карточке продукта.\n\nOpen Food Facts: полная встроенная база штрихкодов. ODbL 1.0; отдельные данные DbCL 1.0. Снимок и число карточек указаны в профиле. Копия базы и исходники импорта: github.com/saint-1406/HealthChecker. Сверяйте значения с этикеткой.\n\nУпражнения: 229 домашних упражнений и разметка 143 видеотренировок канала 재호 - Fitness (по главам автора). Расход энергии — MET из 2024 Adult Compendium of Physical Activities (pacompendium.com): ккал ≈ MET × вес × время. Шаги — ходьба 3,8 MET (код 17190), длина шага ≈ 0,415 × рост. Недельные ориентиры — рекомендации ВОЗ 2020.\n\nНастройка программы: калории — Mifflin–St Jeor × активность с умеренной поправкой на цель и безопасным минимумом; это общие рекомендации, а не медицинская консультация.\n\nПриложение не имеет разрешения на доступ к интернету. Ссылки YouTube открываются внешним приложением по нажатию.",
        "USDA FoodData Central · SR Legacy, April 2018. Retrieved 18 Oct 2026. Public domain data.\nfdc.nal.usda.gov\nRussian names translate the USDA English descriptions; values are unchanged. Fibre is USDA “Fiber, total dietary”.\n\nPortions and nutrition values are averages. Qatıq is found through plain yogurt as an approximate equivalent, not a specific local brand. Baku restaurant dishes are calculated estimates; each food card says how its values were derived.\n\nOpen Food Facts: full bundled barcode database. ODbL 1.0; individual contents DbCL 1.0. The snapshot date and card count are shown in the profile. Database and importer: github.com/saint-1406/HealthChecker. Verify values against the label.\n\nExercises: 229 home exercises and the timelines of 143 workout videos of the channel 재호 - Fitness (by the author's chapters). Energy — MET from the 2024 Adult Compendium of Physical Activities (pacompendium.com): kcal ≈ MET × weight × time. Steps — walking at 3.8 MET (code 17190), step length ≈ 0.415 × height. Weekly targets — WHO 2020 guidelines.\n\nProgram setup: calories are Mifflin–St Jeor × activity with a moderate goal adjustment and a safe minimum; this is general guidance, not medical advice.\n\nThe app has no internet permission. YouTube links open in an external app only when you tap."))},confirmButton={TextButton(onClick={sources=false}){Text(tr("Понятно","Got it"))}})
}
