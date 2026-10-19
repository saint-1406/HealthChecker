package az.healthchecker.ui.diary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.Food
import az.healthchecker.ui.*
import kotlinx.coroutines.delay
@Composable fun FoodSearchScreen(vm:AppViewModel,back:()->Unit,select:(Food)->Unit,custom:()->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var results by remember{mutableStateOf(emptyList<Food>())};var loading by remember{mutableStateOf(true)}
    LaunchedEffect(query){loading=true;delay(180);results=vm.search(query);loading=false}
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row{IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Назад")};Text("Что вы съели?",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=8.dp))}
        OutlinedTextField(query,{query=it.take(100)},label={Text("Название продукта")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("foodSearch"))
        TextButton(onClick=custom){Text("+ Создать свой продукт по этикетке")}
        Text("Русский · Azərbaycan · English",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!loading && results.isEmpty())Text("Ничего не найдено. Попробуйте другое название или создайте свой продукт.",Modifier.padding(vertical=24.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) { items(results,key={it.id}){f->Panel(Modifier.clickable{select(f)}){Text(f.name,style=MaterialTheme.typography.titleMedium);Text("${number(f.per100g.kcal)} ккал · Б ${number(f.per100g.protein,1)} г / 100 г",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)}};if(results.size==50)item{Text("Показаны первые 50. Уточните название.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)} }
    }
}
