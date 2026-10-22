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
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import kotlinx.coroutines.delay
/** [dish] is null when picking an ingredient: then only the search is shown. */
@Composable fun FoodSearchScreen(vm:AppViewModel,title:String,back:()->Unit,select:(Food)->Unit,custom:(()->Unit)?,dish:(()->Unit)?,editDish:(Food)->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var results by remember{mutableStateOf(emptyList<Food>())};var loading by remember{mutableStateOf(true)}
    LaunchedEffect(query){loading=true;delay(180);results=vm.search(query);loading=false}
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row{IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))};Text(title,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=8.dp))}
        OutlinedTextField(query,{query=it.take(100)},label={Text(tr("Название продукта","Food name"))},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("foodSearch"))
        custom?.let{TextButton(onClick=it){Text(tr("+ Создать свой продукт по этикетке","+ Create your own food from a label"))}}
        dish?.let{TextButton(onClick=it,modifier=Modifier.testTag("createDish")){Text(tr("+ Создать блюдо из ингредиентов","+ Create a dish from ingredients"))}}
        Text("Русский · Azərbaycan · English",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!loading && results.isEmpty())Text(tr("Ничего не найдено. Попробуйте другое название или создайте свой продукт.","Nothing found. Try another name or create your own food."),Modifier.padding(vertical=24.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) { items(results,key={it.id}){f->Panel(Modifier.clickable{select(f)}){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(f.displayName,style=MaterialTheme.typography.titleMedium)
                    // The name in the other language, so an English query still shows why a Russian name matched.
                    f.nameEn?.let{en->(if(Lang.en)f.name else en).takeIf{it!=f.displayName}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    Text("${number(f.per100g.kcal)} $KCAL · ${tr("Б","P")} ${number(f.per100g.protein,1)} $G / 100 $G",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                    f.recipe?.let{Text(tr("Своё блюдо · ","Your dish · ")+count(it.ingredients.size,"ингредиент","ингредиента","ингредиентов","ingredient","ingredients"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                if(f.recipe!=null)IconButton(onClick={editDish(f)}){Icon(Icons.Outlined.Edit,tr("Изменить блюдо ","Edit dish ")+f.name)}
            }
        }};if(results.size==50)item{Text(tr("Показаны первые 50. Уточните название.","Showing the first 50. Refine the name."),Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)} }
    }
}
