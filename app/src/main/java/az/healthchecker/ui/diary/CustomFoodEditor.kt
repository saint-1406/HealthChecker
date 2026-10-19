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
    var name by rememberSaveable{mutableStateOf("")};var kcal by rememberSaveable{mutableStateOf("")};var protein by rememberSaveable{mutableStateOf("")};var fat by rememberSaveable{mutableStateOf("")};var carbs by rememberSaveable{mutableStateOf("")};var portion by rememberSaveable{mutableStateOf("")}
    val id=rememberSaveable{"custom:"+UUID.randomUUID()}
    var error by remember{mutableStateOf<String?>(null)}
    val busy by vm.busy.collectAsState()
    fun optional(s:String,max:Double):Double? {if(s.isBlank())return null;val n=s.replace(',','.').toDoubleOrNull();require(n!=null && n.isFinite() && n in 0.0..max){"Проверьте значения на этикетке"};return n}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TextButton(onClick=back){Text("Назад")};SectionTitle("Свой продукт","Перепишите пищевую ценность на 100 г с упаковки. Неизвестные поля оставьте пустыми.")
        OutlinedTextField(name,{name=it.take(200)},label={Text("Название и бренд")},modifier=Modifier.fillMaxWidth())
        listOf(Triple("Калории, ккал",kcal,{s:String->kcal=s}),Triple("Белки, г",protein,{s:String->protein=s}),Triple("Жиры, г",fat,{s:String->fat=s}),Triple("Углеводы, г",carbs,{s:String->carbs=s}),Triple("Вес пачки / штуки, г (необязательно)",portion,{s:String->portion=s})).forEach{(label,value,change)->OutlinedTextField(value,{change(it.take(24))},label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={runCatching{require(name.isNotBlank()){ "Введите название" };val energy=optional(kcal,1000.0)?:error("Укажите калории");Food(id,name.trim(),per100g=Nutrients(energy,optional(protein,100.0),optional(fat,100.0),optional(carbs,100.0)),portions=if(portion.isBlank())emptyList()else listOf(Portion("Пачка / штука",parseQuantity(portion))))}.onSuccess{vm.customFood(it,back)}.onFailure{error=it.message}},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Сохранить продукт")}
    }
}
