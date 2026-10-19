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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.inventory.InventorySection
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProfileScreen(vm:AppViewModel,onboarding:Boolean,folder:()->Unit,restore:()->Unit,date:String="",createDish:()->Unit={},editDish:(Food)->Unit={}) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle();val settings by vm.settings.collectAsStateWithLifecycle();val revision by vm.revision.collectAsStateWithLifecycle();val backupBusy by vm.backupBusy.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val count by vm.catalogCount.collectAsStateWithLifecycle();val backupError by vm.backupError.collectAsStateWithLifecycle()
    val profile=snapshot.profile;val goals=snapshot.day(LocalDate.now().toString()).goals
    var sex by rememberSaveable(profile){mutableStateOf(profile?.sex?:"male")};var age by rememberSaveable(profile){mutableStateOf(profile?.age?.toString()?:"")};var height by rememberSaveable(profile){mutableStateOf(profile?.heightCm?.let(::plain)?:"")};var weight by rememberSaveable(profile){mutableStateOf(profile?.currentWeight?.let(::plain)?:"")};var activity by rememberSaveable(profile){mutableDoubleStateOf(profile?.activity?:1.2)}
    var kcal by rememberSaveable(goals){mutableStateOf(goals?.kcal?.let(::plain)?:"")};var protein by rememberSaveable(goals){mutableStateOf(goals?.protein?.let(::plain)?:"")};var water by rememberSaveable(goals){mutableStateOf(goals?.waterMl?.let(::plain)?:"")}
    var message by remember{mutableStateOf<String?>(null)}
    var sources by rememberSaveable{mutableStateOf(false)}
    fun readProfile():Profile { val a=age.toIntOrNull()?:error("Введите возраст");val h=parseQuantity(height);val w=parseQuantity(weight);return Profile(sex,a,h,profile?.initialWeight?:w,w,activity).also{estimateMaintenance(it)} }
    fun optional(s:String)=if(s.isBlank())null else parseQuantity(s)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if(onboarding){Icon(Icons.Outlined.Spa,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary);Text("Питание.\nБез лишнего.",style=MaterialTheme.typography.displaySmall);Text("Ваш дневник еды и воды. Всё хранится на телефоне — без регистрации.",color=MaterialTheme.colorScheme.onSurfaceVariant);OutlinedButton(onClick={vm.skipOnboarding()},modifier=Modifier.fillMaxWidth()){Text("Начать без расчёта")};TextButton(onClick=restore){Text("У меня уже есть резервная копия")}}
        else {SectionTitle("Профиль","Инвентарь блюд, ориентиры, данные и настройки");InventorySection(vm,date,createDish,editDish)}
        Panel {
            SectionTitle("Параметры тела","Для приблизительной оценки калорий на поддержание веса")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=sex=="male",onClick={sex="male"},label={Text("Мужской")});FilterChip(selected=sex=="female",onClick={sex="female"},label={Text("Женский")})}
            listOf(Triple("Возраст, лет",age,{s:String->age=s}),Triple("Рост, см",height,{s:String->height=s}),Triple("Текущий вес, кг",weight,{s:String->weight=s})).forEach{(label,value,change)->OutlinedTextField(value,{change(it.take(12))},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}
            profile?.let{Text("Начальный вес: ${number(it.initialWeight,1)} кг",style=MaterialTheme.typography.bodySmall)}
            Text("Активность",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(1.2 to "Низкая",1.375 to "Лёгкая",1.55 to "Средняя",1.725 to "Высокая",1.9 to "Очень высокая").forEach{(factor,label)->FilterChip(selected=activity==factor,onClick={activity=factor},label={Text(label)})}}
            TextButton(onClick={runCatching{estimateMaintenance(readProfile())}.onSuccess{if(it==null)message="Для возраста до 18 лет укажите цель вручную"else{kcal=number(it);message="Предложение добавлено в поле калорий. Нажмите «Сохранить»."}}.onFailure{message="Проверьте возраст, рост и вес"}}){Text("Рассчитать и подставить калории")}
            Text("Mifflin–St Jeor × $activity. Это ориентир для взрослых, а не ограничение питания.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            SectionTitle("Дневные цели","Можно задать вручную. Пустое поле — без цели.")
            listOf(Triple("Калории, ккал",kcal,{s:String->kcal=s}),Triple("Белок, г",protein,{s:String->protein=s}),Triple("Питьевая вода, мл",water,{s:String->water=s})).forEach{(label,value,change)->OutlinedTextField(value,{change(it.take(12))},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())}
            message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            Button(onClick={runCatching{val p=if(age.isBlank()&&height.isBlank()&&weight.isBlank())null else readProfile();p to DailyGoals(LocalDate.now().toString(),optional(kcal),optional(protein),optional(water))}.onSuccess{(p,g)->vm.saveProfile(p,g){message="Сохранено"}}.onFailure{message=it.message?:"Проверьте поля"}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(onboarding)"Сохранить и начать"else"Сохранить")}
            Text("Изменения целей действуют с сегодняшнего дня. Прошлые дни сохраняют свои цели.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!onboarding){
            Panel {
                SectionTitle("Резервная копия","Чтобы дневник пережил удаление приложения")
                Text("Выберите папку в памяти телефона. После переустановки откройте сохранённый JSON-файл через «Восстановить». Копия содержит личные данные.",style=MaterialTheme.typography.bodyMedium)
                if(settings.folder.isEmpty())Text("Папка ещё не выбрана",color=MaterialTheme.colorScheme.error)else Text("Папка подключена",color=MaterialTheme.colorScheme.primary)
                Text(if(settings.lastBackup==0L)"Успешных копий пока нет" else "Последняя копия: "+Instant.ofEpochMilli(settings.lastBackup).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm",RU)),style=MaterialTheme.typography.bodySmall)
                if(revision.current!=revision.backedUp)Text("Есть изменения без резервной копии",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                backupError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                OutlinedButton(onClick=folder,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.FolderOpen,null);Spacer(Modifier.width(8.dp));Text("Выбрать папку")}
                Button(onClick={vm.backupNow()},enabled=settings.folder.isNotEmpty()&&!backupBusy,modifier=Modifier.fillMaxWidth()){Text(if(backupBusy)"Сохранение…" else "Создать копию сейчас")}
                TextButton(onClick=restore,modifier=Modifier.fillMaxWidth()){Text("Восстановить из файла")}
            }
            Panel{SectionTitle("Оформление");FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("system" to "Системное","light" to "Светлое","dark" to "Тёмное").forEach{(value,label)->FilterChip(selected=settings.theme==value,onClick={vm.theme(value)},label={Text(label)})}}}
            Panel {SectionTitle("Каталог продуктов","$count позиций · работает без интернета");Text("103 распространённых продукта переведены на русский и имеют азербайджанские поисковые названия. Расширенный каталог — на английском. Региональные бренды можно добавить по этикетке.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={sources=true}){Text("Источники и точность")}}
        }
        Spacer(Modifier.height(16.dp))
    }
    if(sources)AlertDialog(onDismissRequest={sources=false},title={Text("О данных")},text={Text("USDA FoodData Central · SR Legacy, апрель 2018. Получено 18.10.2026. Данные public domain.\nfdc.nal.usda.gov\n\nПорции и пищевая ценность усреднённые. Qatıq находится через натуральный йогурт как приблизительный аналог, а не конкретный местный бренд.\n\nOpen Food Facts был недоступен; его данные не включены.\n\nУпражнения: 141 домашнее упражнение. Расход энергии — MET из 2024 Adult Compendium of Physical Activities (pacompendium.com): ккал ≈ MET × вес × время. Недельные ориентиры — рекомендации ВОЗ 2020.\n\nПриложение не отправляет дневник на сервер.")},confirmButton={TextButton(onClick={sources=false}){Text("Понятно")}})
}
