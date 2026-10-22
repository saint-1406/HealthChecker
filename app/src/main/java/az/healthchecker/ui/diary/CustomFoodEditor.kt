package az.healthchecker.ui.diary
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.util.UUID
@Composable fun CustomFoodEditor(vm:AppViewModel,back:()->Unit) {
    var name by rememberSaveable{mutableStateOf("")};var kcal by rememberSaveable{mutableStateOf("")};var protein by rememberSaveable{mutableStateOf("")};var fat by rememberSaveable{mutableStateOf("")};var carbs by rememberSaveable{mutableStateOf("")};var fiber by rememberSaveable{mutableStateOf("")};var portion by rememberSaveable{mutableStateOf("")}
    val id=rememberSaveable{"custom:"+UUID.randomUUID()}
    var error by remember{mutableStateOf<String?>(null)}
    val busy by vm.busy.collectAsState()
    fun optional(s:String,max:Double):Double? {if(s.isBlank())return null;val n=s.replace(',','.').toDoubleOrNull();require(n!=null && n.isFinite() && n in 0.0..max){tr("Проверьте значения на этикетке","Check the values from the label")};return n}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TextButton(onClick=back){Text(tr("Назад","Back"))};SectionTitle(tr("Свой продукт","Your own food"),tr("Перепишите пищевую ценность на 100 г с упаковки. Неизвестные поля оставьте пустыми.","Copy the nutrition per 100 g from the package. Leave unknown fields empty."))
        OutlinedTextField(name,{name=it.take(200)},label={Text(tr("Название и бренд","Name and brand"))},modifier=Modifier.fillMaxWidth())
        listOf(Triple(tr("Калории, ккал","Calories, kcal"),kcal,{s:String->kcal=s}),Triple(tr("Белки, г","Protein, g"),protein,{s:String->protein=s}),Triple(tr("Жиры, г","Fat, g"),fat,{s:String->fat=s}),Triple(tr("Углеводы, г","Carbs, g"),carbs,{s:String->carbs=s}),Triple(tr("Клетчатка, г","Fibre, g"),fiber,{s:String->fiber=s}),Triple(tr("Вес пачки / штуки, г (необязательно)","Pack / piece weight, g (optional)"),portion,{s:String->portion=s})).forEach{(label,value,change)->OutlinedTextField(value,{change(it.take(24))},label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={runCatching{require(name.isNotBlank()){tr("Введите название","Enter the name")};val energy=optional(kcal,1000.0)?:error(tr("Укажите калории","Enter the calories"));Food(id,name.trim(),per100g=Nutrients(energy,optional(protein,100.0),optional(fat,100.0),optional(carbs,100.0),optional(fiber,100.0)),portions=if(portion.isBlank())emptyList()else listOf(Portion("Пачка / штука",parseQuantity(portion),"Pack / piece")))}.onSuccess{vm.customFood(it,back)}.onFailure{error=it.message}},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(tr("Сохранить продукт","Save food"))}
    }
}
