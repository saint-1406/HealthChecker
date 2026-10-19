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

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EntryEditor(vm:AppViewModel,food:Food,old:DiaryEntry?,template:DiaryEntry?,id:String,date:String,back:()->Unit) {
    // A repeated product starts from the amount recorded last time; ML needs a density this food may lack.
    val start=(old?:template)?.quantity?.takeIf{it.unit!=Measure.ML||food.density!=null}
    var amount by rememberSaveable(id){mutableStateOf(start?.value?.let(::plain)?:if(food.isWater)"250" else "100")}
    var unitName by rememberSaveable(id){mutableStateOf((start?.unit?:if(food.isWater)Measure.ML else Measure.GRAMS).name)}
    var portion by rememberSaveable(id){mutableStateOf((start?.portionGrams?:food.portions.firstOrNull()?.grams)?.let(::plain)?:"")}
    val unit=Measure.valueOf(unitName)
    val q=runCatching{Quantity(parseQuantity(amount),unit,if(unit==Measure.PIECES)parseQuantity(portion)else null)}
    val result=q.mapCatching{calculate(food,it)}
    val busy by vm.busy.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Назад")}
        SectionTitle(food.name,if(old==null)"Добавление · $date" else "Редактирование записи")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(Measure.GRAMS to "Граммы",Measure.PIECES to "Штуки").plus(if(food.density!=null)listOf(Measure.ML to "Миллилитры")else emptyList()).forEach{(u,label)->FilterChip(selected=unit==u,onClick={unitName=u.name;amount=if(u==Measure.PIECES)"1" else if(u==Measure.ML)"250" else "100"},label={Text(label)})}
        }
        OutlinedTextField(amount,{amount=it.take(24)},label={Text(if(unit==Measure.PIECES)"Количество штук" else if(unit==Measure.ML)"Объём, мл" else "Вес, г")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("quantity"))
        if(unit==Measure.PIECES){
            Text("Размеры продуктов отличаются. Уточните вес одной штуки или порции.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(portion,{portion=it.take(24)},label={Text("Вес одной штуки, г")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){food.portions.take(6).forEach{p->SuggestionChip(onClick={portion=plain(p.grams)},label={Text("${p.name}: ${number(p.grams,1)} г")})}}
        }
        Panel {
            Text("В ЭТОЙ ПОРЦИИ",style=MaterialTheme.typography.labelMedium)
            Text("${number(result.getOrNull()?.nutrients?.kcal)} ккал",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.primary)
            Text("Белки ${number(result.getOrNull()?.nutrients?.protein,1)} г   ·   Жиры ${number(result.getOrNull()?.nutrients?.fat,1)} г\nУглеводы ${number(result.getOrNull()?.nutrients?.carbs,1)} г",style=MaterialTheme.typography.bodyLarge)
            Text("Вес: ${number(result.getOrNull()?.grams,1)} г",style=MaterialTheme.typography.bodySmall)
        }
        if(result.isFailure)Text(result.exceptionOrNull()?.message?:"Проверьте количество",color=MaterialTheme.colorScheme.error)
        Button(onClick={vm.saveEntry(id,food,q.getOrThrow(),date,old,back)},enabled=result.isSuccess&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(if(old==null)"Добавить в дневник" else "Сохранить изменения")}
        Text("${food.source}\nНа 100 г: ${number(food.per100g.kcal)} ккал · Б ${number(food.per100g.protein,1)} г. Значения усреднённые; порции в штуках приблизительные.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
