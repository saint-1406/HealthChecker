package az.healthchecker.ui.diary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
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
    var filterName by rememberSaveable{mutableStateOf(FoodFilter.ALL.name)}
    val filter=FoodFilter.valueOf(filterName)
    val snapshot by vm.snapshot.collectAsState()
    val busy by vm.busy.collectAsState()
    val list=rememberLazyListState()
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    LaunchedEffect(query,filter,snapshot.favoriteFoods,snapshot.customFoods){loading=true;delay(180);results=vm.search(query,filter);loading=false;list.requestScrollToItem(0)}
    Column(Modifier.fillMaxSize().imePadding().padding(20.dp)) {
        // Only the compact search bar and one horizontal filter row stay fixed; everything else scrolls.
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            OutlinedTextField(query,{query=it.take(100)},label={Text(tr("Название продукта","Food name"))},
                trailingIcon=if(query.isNotEmpty())({IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,tr("Очистить поиск","Clear search"))}})else null,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();focus.clearFocus()}),
                singleLine=true,modifier=Modifier.weight(1f).testTag("foodSearch"))
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            items(listOf(FoodFilter.ALL to tr("Все","All"),FoodFilter.FAVORITES to tr("Избранное","Favorites"),FoodFilter.OWN to tr("Мои продукты","My foods"))){(value,label)->FilterChip(selected=filter==value,onClick={filterName=value.name},label={Text(label)},modifier=Modifier.heightIn(min=48.dp))}
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("foodResultsList"),state=list,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(query.isBlank()&&filter==FoodFilter.ALL)item{SectionTitle(title);FoodCreationActions(custom,dish)}
            if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(!loading&&results.isEmpty())item {
                Text(if(filter==FoodFilter.FAVORITES&&snapshot.favoriteFoods.isEmpty())tr("Отмечайте продукты звездой — они появятся здесь.","Mark foods with a star to find them here.")else tr("Ничего не найдено. Попробуйте другое название или создайте свой продукт.","Nothing found. Try another name or create your own food."),Modifier.padding(vertical=16.dp))
                if(filter!=FoodFilter.FAVORITES)FoodCreationActions(custom,dish)
            }
            items(results,key={it.id}){f->Panel(Modifier.clickable{select(f)}){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(f.displayName,style=MaterialTheme.typography.titleMedium)
                        f.nameEn?.let{en->(if(Lang.en)f.name else en).takeIf{it!=f.displayName}?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                        Text("${number(f.per100g.kcal)} $KCAL · ${tr("Б","P")} ${number(f.per100g.protein,1)} $G / 100 $G",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                        f.recipe?.let{Text(tr("Своё блюдо · ","Your dish · ")+count(it.ingredients.size,"ингредиент","ингредиента","ингредиентов","ingredient","ingredients"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        FoodBadges(f)
                    }
                    Column {
                        val favorite=f.id in snapshot.favoriteFoods
                        IconButton(onClick={vm.toggleFavorite(f.id)},enabled=!busy){Icon(if(favorite)Icons.Outlined.Star else Icons.Outlined.StarBorder,(if(favorite)tr("Убрать из избранного: ","Remove favorite: ")else tr("В избранное: ","Favorite: "))+f.displayName,tint=if(favorite)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                        if(f.recipe!=null)IconButton(onClick={editDish(f)}){Icon(Icons.Outlined.Edit,tr("Изменить блюдо ","Edit dish ")+f.name)}
                    }
                }
            }}
            if(results.size==50)item{Text(tr("Показаны первые 50. Уточните название.","Showing the first 50. Refine the name."),Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@Composable private fun FoodCreationActions(custom:(()->Unit)?,dish:(()->Unit)?) {
    Column {
        custom?.let{TextButton(onClick=it){Text(tr("+ Создать свой продукт по этикетке","+ Create your own food from a label"))}}
        dish?.let{TextButton(onClick=it,modifier=Modifier.testTag("createDish")){Text(tr("+ Создать блюдо из ингредиентов","+ Create a dish from ingredients"))}}
        Text("Русский · Azərbaycan · English",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FoodBadges(food:Food) {
    val preparation=remember(food.nameEn){foodPreparations(food)}
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        val labels=preparation.map{when(it){FoodPreparation.RAW->tr("Сырой","Raw");FoodPreparation.COOKED->tr("Готовый","Cooked");FoodPreparation.DRY->tr("Сухой","Dry")}}+
            if(food.id.startsWith("local:"))listOf(tr("≈ Оценка","≈ Estimate"))else emptyList()
        labels.forEach{label->Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.small,modifier=Modifier.padding(top=4.dp)){Text(label,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp))}}
    }
}
