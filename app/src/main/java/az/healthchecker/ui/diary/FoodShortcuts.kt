package az.healthchecker.ui.diary
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.kit.FilledTonalButton

/** Horizontal shortcuts keep daily food entry close to the top even with a long diary. */
@Composable fun FoodShortcuts(vm:AppViewModel,snapshot:UserSnapshot,date:String,repeat:(DiaryEntry)->Unit,select:(Food)->Unit,openMeals:()->Unit,openDraft:()->Unit) {
    val loaded by vm.catalogCount.collectAsStateWithLifecycle()
    val favorites=remember(snapshot.favoriteFoods,snapshot.customFoods,loaded,Lang.current){snapshot.favoriteFoods.mapNotNull{vm.food(it)}.sortedBy{it.displayName}.take(12)}
    val recent=remember(snapshot.entries){snapshot.entries.asSequence().filter{it.waterMl==0.0}.sortedByDescending{it.instant}.distinctBy{it.foodId}.take(5).toList()}
    // Slim chip rows with a small caption each: favourites, recent foods (with the amount last time) and saved meals.
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        if(favorites.isNotEmpty()) {
            Caption(tr("Избранное","Favorites"))
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(favorites,key={it.id}){food->AssistChip(onClick={select(food)},label={Text(food.displayName,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=180.dp))},leadingIcon={Icon(Icons.Outlined.Star,null,Modifier.size(16.dp))})}}
        }
        if(recent.isNotEmpty()) {
            Caption(tr("Недавние продукты","Recent foods"))
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(recent,key={it.foodId}){entry->AssistChip(onClick={repeat(entry)},label={Text(vm.entryName(entry)+" · "+quantityLabel(entry.quantity,entry.grams),maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=220.dp))},leadingIcon={Icon(Icons.Outlined.History,null,Modifier.size(16.dp))})}}
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            item{AssistChip(onClick=openMeals,label={Text(tr("Наборы еды","Saved meals"))},leadingIcon={Icon(Icons.Outlined.RestaurantMenu,null,Modifier.size(16.dp))})}
            items(snapshot.savedMeals.sortedBy{it.name}.take(5),key={it.id}){meal->SuggestionChip(onClick={vm.addSavedMeal(meal,date,openDraft)},label={Text(meal.name,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=180.dp))})}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun WaterActions(vm:AppViewModel,date:String) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Glass size and "+ water" on one line (it wraps only on a narrow screen).
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
        FilledTonalButton(onClick={vm.water(date)},contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.height(36.dp)){Icon(Icons.Outlined.WaterDrop,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(tr("+${settings.waterMl} мл воды","+${settings.waterMl} ml water"),style=MaterialTheme.typography.labelLarge)}
        listOf(200,250,500).forEach{ml->FilterChip(selected=settings.waterMl==ml,onClick={vm.waterSize(ml)},label={Text("$ml $ML")})}
    }
}

@Composable private fun Caption(text:String) = Text(text,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
