package az.healthchecker.ui.diary
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*

@Composable fun EntryEditor(vm:AppViewModel,food:Food,old:DiaryEntry?,template:DiaryEntry?,id:String,date:String,back:()->Unit,addMore:()->Unit={},useMealDraft:Boolean=true,logged:(String)->Unit={back()}) {
    val busy by vm.busy.collectAsState()
    val settings by vm.settings.collectAsState()
    val draft by vm.mealDraft.collectAsState()
    // A repeated product starts from the amount recorded last time.
    val initial=(old?:template)?.quantity?:settings.lastPortions[food.id]
    val day=old?.localDate?:(if(useMealDraft)draft?.date else null)?:date
    val hasDraft=useMealDraft&&draft?.items?.isNotEmpty()==true
    PortionEditor(food,initial,id,if(old==null)tr("Добавление · ","Adding · ")+dayMonth(day) else tr("Редактирование записи","Editing the entry"),
        if(old!=null)tr("Сохранить изменения","Save changes")else if(hasDraft)tr("Записать всё","Log all")else tr("Добавить в дневник","Add to diary"),busy,back,
        secondaryLabel=if(old==null&&useMealDraft)tr("Добавить ещё","Add another")else null,
        secondary=if(old==null&&useMealDraft)({q->vm.addAnotherFood(id,food,q,day,addMore)})else null
    ){q->if(old!=null||!useMealDraft)vm.saveEntry(id,food,q,day,old,back)else vm.logFood(id,food,q,day,logged)}
}

/** Amount form shared by diary entries and dish ingredients: units, editable piece weight and a live nutrient preview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PortionEditor(food:Food,initial:Quantity?,id:String,subtitle:String,confirmLabel:String,busy:Boolean,back:()->Unit,secondaryLabel:String?=null,secondary:((Quantity)->Unit)?=null,confirm:(Quantity)->Unit) {
    // ML needs a density this food may lack.
    val start=initial?.takeIf{it.unit!=Measure.ML||food.density!=null}
    // Drinks with a known density (water, espresso) start in millilitres at their usual serving.
    val servingMl=food.density?.let{d->food.portions.firstOrNull()?.let{p->plain(p.grams/d)}}?:"250"
    var amount by rememberSaveable(id){mutableStateOf(start?.value?.let(::plain)?:if(food.density!=null)servingMl else "100")}
    var unitName by rememberSaveable(id){mutableStateOf((start?.unit?:if(food.density!=null)Measure.ML else Measure.GRAMS).name)}
    var portion by rememberSaveable(id){mutableStateOf((start?.portionGrams?:food.portions.firstOrNull()?.grams)?.let(::plain)?:"")}
    var pendingGrams by rememberSaveable(id){mutableStateOf<Double?>(null)}
    val unit=Measure.valueOf(unitName)
    val piece=runCatching{parseQuantity(portion)}.getOrNull()
    val displayedAmount=if(unit==Measure.PIECES&&pendingGrams!=null&&piece!=null)plain(pendingGrams!!/piece)else amount
    val q=runCatching{Quantity(parseQuantity(displayedAmount),unit,if(unit==Measure.PIECES)parseQuantity(portion)else null)}
    val result=q.mapCatching{calculate(food,it)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
        SectionTitle(food.displayName,subtitle)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(Measure.GRAMS to tr("Граммы","Grams"),Measure.PIECES to tr("Штуки","Pieces")).plus(if(food.density!=null)listOf(Measure.ML to tr("Миллилитры","Millilitres"))else emptyList()).forEach{(u,label)->FilterChip(selected=unit==u,onClick=click@{
                if(u==unit)return@click
                val current=q.getOrNull()
                val grams=current?.let{runCatching{calculate(food,it).grams}.getOrNull()}?:pendingGrams
                if(u==Measure.PIECES&&piece==null&&food.portions.isEmpty()&&grams!=null){pendingGrams=grams;unitName=u.name;amount="1"}
                else {
                    val converted=current?.let{runCatching{convertQuantity(food,it,u,piece)}.getOrNull()}
                        ?:grams?.let{runCatching{convertQuantity(food,Quantity(it,Measure.GRAMS),u,piece)}.getOrNull()}
                    unitName=u.name;amount=converted?.value?.let(::plain)?:"";pendingGrams=null
                    if(converted?.unit==Measure.PIECES)portion=converted.portionGrams?.let(::plain)?:portion
                }
            },label={Text(label)},modifier=Modifier.heightIn(min=48.dp))}
        }
        OutlinedTextField(displayedAmount,{amount=it.take(24);pendingGrams=null},label={Text(if(unit==Measure.PIECES)tr("Количество штук","Number of pieces") else if(unit==Measure.ML)tr("Объём, мл","Volume, ml") else tr("Вес, г","Weight, g"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("quantity"))
        if(unit==Measure.PIECES){
            Text(tr("Размеры продуктов отличаются. Уточните вес одной штуки или порции.","Sizes vary. Check the weight of one piece or portion."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            pendingGrams?.let{Text(tr("Пересчитываем из ","Converting from ")+"${number(it,1)} $G. "+tr("Укажите вес одной штуки.","Enter the weight of one piece."),style=MaterialTheme.typography.bodySmall)}
            OutlinedTextField(portion,{portion=it.take(24)},label={Text(tr("Вес одной штуки, г","Weight of one piece, g"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){food.portions.take(6).forEach{p->SuggestionChip(onClick={portion=plain(p.grams)},label={Text("${p.displayName}: ${number(p.grams,1)} $G")})}}
        }
        Panel {
            val n=result.getOrNull()?.nutrients
            Text(tr("В ЭТОЙ ПОРЦИИ","IN THIS PORTION"),style=MaterialTheme.typography.labelMedium)
            Text("${number(n?.kcal)} $KCAL",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.primary)
            Text("${tr("Белки","Protein")} ${number(n?.protein,1)} $G   ·   ${tr("Жиры","Fat")} ${number(n?.fat,1)} $G\n${tr("Углеводы","Carbs")} ${number(n?.carbs,1)} $G   ·   ${tr("Клетчатка","Fibre")} ${number(n?.fiber,1)} $G",style=MaterialTheme.typography.bodyLarge)
            Text("${tr("Вес","Weight")}: ${number(result.getOrNull()?.grams,1)} $G",style=MaterialTheme.typography.bodySmall)
        }
        if(result.isFailure)Text(result.exceptionOrNull()?.message?:tr("Проверьте количество","Check the amount"),color=MaterialTheme.colorScheme.error)
        Button(onClick={confirm(q.getOrThrow())},enabled=result.isSuccess&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("savePortion")){Text(confirmLabel)}
        if(secondaryLabel!=null&&secondary!=null)OutlinedButton(onClick={secondary(q.getOrThrow())},enabled=result.isSuccess&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("addAnotherFood")){Text(secondaryLabel)}
        Text("${food.displaySource}\n${tr("На 100 г","Per 100 g")}: ${number(food.per100g.kcal)} $KCAL · ${macrosLine(food.per100g)}. ${tr("Значения усреднённые; порции в штуках приблизительные.","Values are averages; piece weights are approximate.")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
