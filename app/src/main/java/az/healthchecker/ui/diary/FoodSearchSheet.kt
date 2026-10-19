package az.healthchecker.ui.diary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.Food
import az.healthchecker.ui.*
import kotlinx.coroutines.delay
/** [dish] is null when picking an ingredient: then only the search is shown. */
@Composable fun FoodSearchScreen(vm:AppViewModel,title:String,back:()->Unit,select:(Food)->Unit,custom:(()->Unit)?,dish:(()->Unit)?,editDish:(Food)->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var results by remember{mutableStateOf(emptyList<Food>())};var loading by remember{mutableStateOf(true)}
    LaunchedEffect(query){loading=true;delay(180);results=vm.search(query);loading=false}
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row{IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Назад")};Text(title,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=8.dp))}
        OutlinedTextField(query,{query=it.take(100)},label={Text("Название продукта")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("foodSearch"))
        custom?.let{TextButton(onClick=it){Text("+ Создать свой продукт по этикетке")}}
        dish?.let{TextButton(onClick=it,modifier=Modifier.testTag("createDish")){Text("+ Создать блюдо из ингредиентов")}}
        Text("Русский · Azərbaycan · English",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!loading && results.isEmpty())Text("Ничего не найдено. Попробуйте другое название или создайте свой продукт.",Modifier.padding(vertical=24.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) { items(results,key={it.id}){f->Panel(Modifier.clickable{select(f)}){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(f.name,style=MaterialTheme.typography.titleMedium);Text("${number(f.per100g.kcal)} ккал · Б ${number(f.per100g.protein,1)} г / 100 г",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary);f.recipe?.let{Text("Своё блюдо · ${plural(it.ingredients.size,"ингредиент","ингредиента","ингредиентов")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                if(f.recipe!=null)IconButton(onClick={editDish(f)}){Icon(Icons.Outlined.Edit,"Изменить блюдо ${f.name}")}
            }
        }};if(results.size==50)item{Text("Показаны первые 50. Уточните название.",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)} }
    }
}
