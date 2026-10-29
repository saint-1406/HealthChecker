package az.healthchecker.ui.diary

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.healthchecker.barcode.*
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.*

@Composable fun BarcodeScreen(vm:AppViewModel,back:()->Unit,select:(Food)->Unit) {
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
    val client=remember{OpenFoodFactsClient()}
    val lookup=remember{BarcodeLookup(scope,{code->vm.snapshot.value.customFoods.find{it.barcode==code}},{code->withContext(Dispatchers.IO){client.lookup(code)}})}
    val state by lookup.state.collectAsState()
    val busy by vm.busy.collectAsState()
    var input by rememberSaveable{mutableStateOf("")}
    var submitted by rememberSaveable{mutableStateOf("")}
    var review by remember{mutableStateOf<LabelProduct?>(null)}
    var photoError by remember{mutableStateOf<String?>(null)}
    val reading=state.readingPhoto
    fun accept(value:String){input=value;submitted=value;photoError=null;review=null;keyboard?.hide();focus.clearFocus();lookup.accept(value)}
    val scanner=rememberLauncherForActivityResult(ScanContract()){r->
        if(r.contents!=null)runCatching{normalizeScannedCode(r.contents,r.formatName)}.onSuccess{accept(it)}.onFailure{photoError=it.message}
        else if(r.originalIntent?.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION)==true)photoError=tr("Камера недоступна. Разрешите её в настройках или введите код вручную.","Camera access is unavailable. Allow it in settings or enter the code manually.")
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null&&!reading){lookup.readPhoto({readBarcodePhoto(context,uri)}){accept(it)}}}
    DisposableEffect(lookup){onDispose{lookup.cancel()}}
    LaunchedEffect(Unit){if(input.isNotBlank())lookup.accept(input)}
    LaunchedEffect(state.food?.id){state.food?.let(select)}
    BackHandler(review!=null){review=null}
    val preview=review
    if(preview!=null) {
        BarcodeLabelEditor(preview,{review=null},busy){food->vm.saveBarcodeFood(food){select(food)}}
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))};SectionTitle(tr("Сканер товаров","Product scanner"))}
        Text(tr("Наведите камеру на штрихкод упаковки. Можно также выбрать его фотографию или ввести цифры.","Point the camera at the package barcode. You can also choose a barcode photo or enter the digits."))
        Button(onClick={
            lookup.cancel()
            runCatching{scanner.launch(ScanOptions().setDesiredBarcodeFormats(listOf(ScanOptions.EAN_13,ScanOptions.EAN_8,ScanOptions.UPC_A,ScanOptions.UPC_E,ScanOptions.ITF))
                .setPrompt(tr("Наведите камеру на штрихкод упаковки","Point the camera at the product barcode")).setBeepEnabled(false).setOrientationLocked(false).setTimeout(60000))}
                .onFailure{photoError=tr("Не удалось открыть камеру. Введите код вручную.","Could not open the camera. Enter the code manually.")}
        },enabled=!reading,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("barcodeCamera")){Icon(Icons.Outlined.QrCodeScanner,null);Spacer(Modifier.width(8.dp));Text(tr("Сканировать камерой","Scan with camera"))}
        OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!reading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Outlined.PhotoLibrary,null);Spacer(Modifier.width(8.dp));Text(tr("Выбрать фото штрихкода","Choose a barcode photo"))}
        OutlinedTextField(input,{input=it.take(40);submitted="";lookup.cancel();photoError=null},label={Text(tr("Цифры штрихкода","Barcode digits"))},singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={accept(input)}),modifier=Modifier.fillMaxWidth().testTag("barcodeInput"))
        Button(onClick={accept(input)},enabled=input.isNotBlank()&&!reading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("barcodeFind")){Text(tr("Найти на телефоне","Find on this phone"))}
        if(reading||state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        val message=photoError?:state.message.takeIf{submitted==input}
        message?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("barcodeMessage"))}
        val matching=submitted==input&&state.code.isNotEmpty()&&runCatching{normalizeBarcode(input)==state.code}.getOrDefault(false)
        if(matching&&state.food==null)Panel {
            Text(tr("На телефоне этого товара пока нет","This product is not saved on this phone yet"),style=MaterialTheme.typography.titleMedium)
            Text(tr("Поиск онлайн отправит только штрихкод в Open Food Facts. Дневник и фотографии остаются на телефоне.","Online lookup sends only the barcode to Open Food Facts. Your diary and photos stay on this phone."),style=MaterialTheme.typography.bodySmall)
            Button(onClick={lookup.lookupOnline()},enabled=!state.loading&&!reading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("barcodeOnline")){Text(tr("Найти онлайн","Look up online"))}
            state.label?.let{label->
                Text(listOf(label.name,label.brand).filter{it.isNotBlank()}.joinToString(" · "))
                Text(tr("Сверьте данные с упаковкой перед сохранением.","Check these details against the package before saving."))
                Button(onClick={review=label},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("barcodeReview")){Text(tr("Проверить данные","Review details"))}
            }
            TextButton(onClick={lookup.cancel();review=LabelProduct(state.code)},enabled=!reading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("barcodeManual")){Text(tr("Ввести по этикетке","Enter from label"))}
        }
        Text(tr("Сохранённые товары доступны без интернета. Штрихкод определяет товар, пищевые значения берутся из его карточки.","Saved products work offline. The barcode identifies a product; nutrition comes from its product card."),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun BarcodeLabelEditor(label:LabelProduct,back:()->Unit,busy:Boolean=false,save:(Food)->Unit) {
    var name by rememberSaveable(label.code){mutableStateOf(label.name)}
    var brand by rememberSaveable(label.code){mutableStateOf(label.brand)}
    var kcal by rememberSaveable(label.code){mutableStateOf(label.nutrients.kcal?.let(::plain).orEmpty())}
    var protein by rememberSaveable(label.code){mutableStateOf(label.nutrients.protein?.let(::plain).orEmpty())}
    var fat by rememberSaveable(label.code){mutableStateOf(label.nutrients.fat?.let(::plain).orEmpty())}
    var carbs by rememberSaveable(label.code){mutableStateOf(label.nutrients.carbs?.let(::plain).orEmpty())}
    var fiber by rememberSaveable(label.code){mutableStateOf(label.nutrients.fiber?.let(::plain).orEmpty())}
    var pack by rememberSaveable(label.code){mutableStateOf(label.packageAmount?.let(::plain).orEmpty())}
    var basisName by rememberSaveable(label.code){mutableStateOf(label.basis.name)}
    var mass by rememberSaveable(label.code){mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    val basis=NutritionBasis.valueOf(basisName)
    val uriHandler=LocalUriHandler.current
    fun optional(text:String):Double?=if(text.isBlank())null else text.trim().replace(',','.').toDoubleOrNull()?.also{require(it.isFinite()&&it>=0){tr("Проверьте числа на этикетке","Check the label numbers")}}?:throw IllegalArgumentException(tr("Проверьте числа на этикетке","Check the label numbers"))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
        SectionTitle(tr("Проверьте этикетку","Check the label"),label.code)
        if(label.fromOpenFoodFacts){
            Text(tr("Open Food Facts: данные могут быть неполными. Исправьте значения по своей упаковке.","Open Food Facts data may be incomplete. Correct the values using your package."))
            TextButton(onClick={runCatching{uriHandler.openUri("https://world.openfoodfacts.org/product/${label.code}")}}){Text("Open Food Facts · ODbL")}
        }
        OutlinedTextField(name,{name=it.take(150)},label={Text(tr("Название продукта","Product name"))},modifier=Modifier.fillMaxWidth().testTag("barcodeName"))
        OutlinedTextField(brand,{brand=it.take(45)},label={Text(tr("Бренд (необязательно)","Brand (optional)"))},modifier=Modifier.fillMaxWidth())
        Text(tr("Пищевая ценность указана:","Nutrition values are per:"))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=basis==NutritionBasis.MASS,onClick={if(basis!=NutritionBasis.MASS){basisName=NutritionBasis.MASS.name;pack=""}},label={Text(tr("100 г","100 g"))},modifier=Modifier.heightIn(min=48.dp))
            FilterChip(selected=basis==NutritionBasis.VOLUME,onClick={if(basis!=NutritionBasis.VOLUME){basisName=NutritionBasis.VOLUME.name;pack=""}},label={Text(tr("100 мл","100 ml"))},modifier=Modifier.heightIn(min=48.dp).testTag("barcodeBasisMl"))
        }
        Text(tr("Неизвестные БЖУ оставьте пустыми. Значения должны соответствовать выбранным 100 г или 100 мл.","Leave unknown macros blank. Values must refer to the selected 100 g or 100 ml."),style=MaterialTheme.typography.bodySmall)
        fun fieldLabel(ru:String,en:String)=tr(ru,en)
        listOf(
            Triple(fieldLabel("Калории, ккал","Calories, kcal"),kcal,{s:String->kcal=s}),
            Triple(fieldLabel("Белки, г","Protein, g"),protein,{s:String->protein=s}),
            Triple(fieldLabel("Жиры, г","Fat, g"),fat,{s:String->fat=s}),
            Triple(fieldLabel("Углеводы, г","Carbohydrates, g"),carbs,{s:String->carbs=s}),
            Triple(fieldLabel("Клетчатка, г","Fibre, g"),fiber,{s:String->fiber=s})
        ).forEachIndexed{i,(title,value,change)->OutlinedTextField(value,{change(it.take(24))},label={Text(title)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag(if(i==0)"barcodeKcal"else "barcodeNutrient$i"))}
        if(basis==NutritionBasis.VOLUME){
            Text(tr("Для точного пересчёта в граммы взвесьте 100 мл продукта. Плотность воды автоматически не подставляется.","For accurate conversion to grams, weigh 100 ml of this product. Water density is not assumed."),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(mass,{mass=it.take(24)},label={Text(tr("Сколько весят 100 мл, г","Weight of 100 ml, g"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("barcodeDensity"))
        }
        OutlinedTextField(pack,{pack=it.take(24)},label={Text(if(basis==NutritionBasis.MASS)tr("Вес упаковки, г (необязательно)","Package weight, g (optional)")else tr("Объём упаковки, мл (необязательно)","Package volume, ml (optional)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={runCatching{
            require(name.isNotBlank()){tr("Введите название продукта","Enter the product name")}
            val energy=optional(kcal)?:throw IllegalArgumentException(tr("Укажите калории с этикетки","Enter calories from the label"))
            label.copy(name=name,brand=brand,nutrients=Nutrients(energy,optional(protein),optional(fat),optional(carbs),optional(fiber)),packageAmount=optional(pack)).toFood(basis,if(basis==NutritionBasis.VOLUME)optional(mass)else null)
        }.onSuccess(save).onFailure{error=it.message}},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("barcodeSaveFood")){Text(tr("Сохранить и указать порцию","Save and choose portion"))}
    }
}
