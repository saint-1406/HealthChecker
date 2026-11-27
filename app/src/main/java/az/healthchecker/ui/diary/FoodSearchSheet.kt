package az.healthchecker.ui.diary
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import kotlinx.coroutines.delay
import az.healthchecker.ui.theme.rounded

fun foodCategoryIcon(c:FoodCategory?):ImageVector = when(c) {
    FoodCategory.FAST_FOOD->Icons.Outlined.Fastfood;FoodCategory.DISHES->Icons.Outlined.RamenDining;FoodCategory.COFFEE->Icons.Outlined.LocalCafe
    FoodCategory.DRINKS->Icons.Outlined.LocalDrink;FoodCategory.ALCOHOL->Icons.Outlined.WineBar;FoodCategory.SWEETS->Icons.Outlined.Cake
    FoodCategory.SNACKS->Icons.Outlined.Cookie;FoodCategory.DAIRY->Icons.Outlined.Egg;FoodCategory.MEAT->Icons.Outlined.KebabDining
    FoodCategory.FISH->Icons.Outlined.SetMeal;FoodCategory.FRUIT_VEG->Icons.Outlined.Eco;FoodCategory.GRAINS->Icons.Outlined.BakeryDining
    FoodCategory.INGREDIENTS->Icons.Outlined.Kitchen;null->Icons.Outlined.Restaurant
}

/**
 * "What did you eat?": a search bar, shelves to jump to (fast food, coffee, drinks, ingredients…), the all / favourites /
 * own switch and compact result rows. [dish] is null when picking an ingredient: then only the search is shown.
 */
@Composable fun FoodSearchScreen(vm:AppViewModel,title:String,back:()->Unit,select:(Food)->Unit,custom:(()->Unit)?,dish:(()->Unit)?,editDish:(Food)->Unit,scan:(()->Unit)?=null) {
    var query by rememberSaveable{mutableStateOf("")};var results by remember{mutableStateOf(emptyList<Food>())};var loading by remember{mutableStateOf(true)}
    var filterName by rememberSaveable{mutableStateOf(FoodFilter.ALL.name)}
    var categoryId by rememberSaveable{mutableStateOf<String?>(null)}
    val filter=FoodFilter.valueOf(filterName);val category=FoodCategory.of(categoryId)
    val snapshot by vm.snapshot.collectAsState()
    val busy by vm.busy.collectAsState()
    val list=rememberLazyListState()
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    LaunchedEffect(query,filter,category,snapshot.favoriteFoods,snapshot.customFoods){loading=true;delay(180);results=vm.search(query,filter,category);loading=false;list.requestScrollToItem(0)}
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        // Big tiles where there is room; one line of chips on a short screen or with very large text.
        val roomy=maxHeight>=480.dp&&LocalDensity.current.fontScale<=1.3f
        Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp)) {
            // The search bar, the shelves and the switch stay fixed; the results scroll.
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
                OutlinedTextField(query,{query=it.take(100)},placeholder={Text(tr("Название продукта","Food name"))},leadingIcon={Icon(Icons.Outlined.Search,null)},
                    trailingIcon=if(query.isNotEmpty())({IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,tr("Очистить поиск","Clear search"))}})else null,
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();focus.clearFocus()}),
                    singleLine=true,shape=rounded(28.dp),modifier=Modifier.weight(1f).testTag("foodSearch"))
                scan?.let{IconButton(onClick=it,modifier=Modifier.testTag("barcodeAction")){Icon(Icons.Outlined.QrCodeScanner,tr("Сканировать штрихкод","Scan a barcode"))}}
            }
            Spacer(Modifier.height(10.dp))
            CategoryShelves(category,roomy){categoryId=it?.id}
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                items(listOf(FoodFilter.ALL to tr("Все","All"),FoodFilter.FAVORITES to tr("Избранное","Favorites"),FoodFilter.OWN to tr("Мои продукты","My foods"))){(value,label)->
                    FilterChip(selected=filter==value,onClick={filterName=value.name},label={Text(label)})}
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("foodResultsList"),state=list,verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(top=4.dp,bottom=12.dp)) {
                if(query.isBlank()&&filter==FoodFilter.ALL&&category==null)item{
                    Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=4.dp))
                    FoodCreationActions(custom,dish)
                }
                if(category!=null)item{Text(foodCategoryLabel(category)+if(query.isBlank())tr(" · популярное"," · popular") else "",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=4.dp).testTag("categoryTitle"))}
                if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
                if(!loading&&results.isEmpty())item {
                    Text(when{
                        filter==FoodFilter.FAVORITES&&snapshot.favoriteFoods.isEmpty()->tr("Отмечайте продукты звездой — они появятся здесь.","Mark foods with a star to find them here.")
                        category!=null&&filter==FoodFilter.OWN->tr("Свои продукты не делятся по категориям — выберите «Все» вверху.","Your own foods have no category — choose “All” above.")
                        else->tr("Ничего не найдено. Попробуйте другое название или создайте свой продукт.","Nothing found. Try another name or create your own food.")
                    },Modifier.padding(vertical=16.dp))
                    if(filter!=FoodFilter.FAVORITES)FoodCreationActions(custom,dish)
                }
                items(results,key={it.id}){f->FoodRow(f,f.id in snapshot.favoriteFoods,busy,{select(f)},{vm.toggleFavorite(f.id)},if(f.recipe!=null)({editDish(f)})else null)}
                if(results.size==50)item{Text(tr("Показаны первые 50. Уточните название.","Showing the first 50. Refine the name."),Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
}

/** Quick navigation between shelves: "All" plus every category, tapping the chosen one again clears it. */
@Composable private fun CategoryShelves(current:FoodCategory?,roomy:Boolean,choose:(FoodCategory?)->Unit) {
    val shelves=listOf<FoodCategory?>(null)+FoodCategory.entries
    LazyRow(horizontalArrangement=Arrangement.spacedBy(if(roomy)10.dp else 8.dp),modifier=Modifier.fillMaxWidth().testTag("categoryShelves")) {
        items(shelves,key={it?.id?:"all"}){c->
            val on=c==current
            val label=c?.let(::foodCategoryLabel)?:tr("Все категории","All categories")
            if(roomy)Column(Modifier.width(72.dp).clickable{choose(if(on)null else c)}.semantics(mergeDescendants=true){selected=on;role=Role.Tab}.testTag("category-${c?.id?:"all"}"),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Surface(shape=rounded(18.dp),color=if(on)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border=if(on)null else androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.size(56.dp)) {
                    Box(contentAlignment=Alignment.Center){Icon(c?.let(::foodCategoryIcon)?:Icons.Outlined.Apps,null,tint=if(on)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)}
                }
                Text(label,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis,
                    color=if(on)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,fontWeight=if(on)FontWeight.SemiBold else FontWeight.Normal)
            }
            else FilterChip(selected=on,onClick={choose(if(on)null else c)},label={Text(label,maxLines=1)},
                leadingIcon={Icon(c?.let(::foodCategoryIcon)?:Icons.Outlined.Apps,null,Modifier.size(18.dp))},modifier=Modifier.testTag("category-${c?.id?:"all"}"))
        }
    }
}

/** One result: shelf icon, name, energy and macros per 100 g on one line, and a quiet line with the other name and notes. */
@Composable private fun FoodRow(f:Food,favorite:Boolean,busy:Boolean,open:()->Unit,toggle:()->Unit,edit:(()->Unit)?) {
    Surface(Modifier.fillMaxWidth().clickable(onClick=open),shape=rounded(16.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(start=12.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=0.14f),CircleShape),contentAlignment=Alignment.Center) {
                Icon(if(f.recipe!=null)Icons.Outlined.MenuBook else foodCategoryIcon(FoodCategory.of(f.category)),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                Text(f.displayName,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                val n=f.per100g
                Text("${number(n.kcal)} $KCAL · ${tr("Б","P")} ${number(n.protein,1)} · ${tr("Ж","F")} ${number(n.fat,1)} · ${tr("У","C")} ${number(n.carbs,1)} · 100 $G",
                    style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
                val other=f.nameEn?.let{en->(if(Lang.en)f.name else en).takeIf{it!=f.displayName}}
                val notes=listOfNotNull(f.recipe?.let{tr("своё блюдо, ","your dish, ")+count(it.ingredients.size,"ингредиент","ингредиента","ингредиентов","ingredient","ingredients")},other)+foodBadges(f)
                if(notes.isNotEmpty())Text(notes.joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Row {
                edit?.let{IconButton(onClick=it){Icon(Icons.Outlined.Edit,tr("Изменить блюдо ","Edit dish ")+f.name)}}
                IconButton(onClick=toggle,enabled=!busy){Icon(if(favorite)Icons.Outlined.Star else Icons.Outlined.StarBorder,(if(favorite)tr("Убрать из избранного: ","Remove favorite: ")else tr("В избранное: ","Favorite: "))+f.displayName,
                    tint=if(favorite)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FoodCreationActions(custom:(()->Unit)?,dish:(()->Unit)?) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=4.dp)) {
        custom?.let{AssistChip(onClick=it,label={Text(tr("Свой продукт по этикетке","Own food from a label"))},leadingIcon={Icon(Icons.Outlined.Add,null,Modifier.size(18.dp))})}
        dish?.let{AssistChip(onClick=it,label={Text(tr("Блюдо из ингредиентов","Dish from ingredients"))},leadingIcon={Icon(Icons.Outlined.Add,null,Modifier.size(18.dp))},modifier=Modifier.testTag("createDish"))}
    }
    Text("Русский · Azərbaycan · English",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Short notes for a result: raw, cooked or dry, and "≈ estimate" for the app's own modelled dishes. */
@Composable fun foodBadges(food:Food):List<String> {
    val preparation=remember(food.nameEn){foodPreparations(food)}
    return preparation.map{when(it){FoodPreparation.RAW->tr("сырой","raw");FoodPreparation.COOKED->tr("готовый","cooked");FoodPreparation.DRY->tr("сухой","dry")}}+
        if(food.id.startsWith("local:"))listOf(tr("≈ оценка","≈ estimate"))else emptyList()
}
