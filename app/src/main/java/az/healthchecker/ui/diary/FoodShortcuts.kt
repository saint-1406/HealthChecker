package az.healthchecker.ui.diary
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*

/** Horizontal shortcuts keep daily food entry close to the top even with a long diary. */
@Composable fun FoodShortcuts(vm:AppViewModel,snapshot:UserSnapshot,date:String,repeat:(DiaryEntry)->Unit,select:(Food)->Unit,openMeals:()->Unit,openDraft:()->Unit) {
    val loaded by vm.catalogCount.collectAsStateWithLifecycle()
    val favorites=remember(snapshot.favoriteFoods,snapshot.customFoods,loaded,Lang.current){snapshot.favoriteFoods.mapNotNull{vm.food(it)}.sortedBy{it.displayName}.take(12)}
    val recent=remember(snapshot.entries){snapshot.entries.asSequence().filter{it.waterMl==0.0}.sortedByDescending{it.instant}.distinctBy{it.foodId}.take(5).toList()}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(favorites.isNotEmpty()) {
            Text(tr("Избранное","Favorites"),style=MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(favorites,key={it.id}){food->AssistChip(onClick={select(food)},label={Text(food.displayName,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=200.dp))},leadingIcon={Icon(Icons.Outlined.Star,null,Modifier.size(18.dp))},modifier=Modifier.heightIn(min=48.dp))}}
        }
        if(recent.isNotEmpty()) {
            Text(tr("Недавние продукты","Recent foods"),style=MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(recent,key={it.foodId}){entry->AssistChip(onClick={repeat(entry)},label={Column{Text(vm.entryName(entry),maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=200.dp));Text(quantityLabel(entry.quantity,entry.grams),style=MaterialTheme.typography.labelSmall)}},modifier=Modifier.heightIn(min=48.dp))}}
        }
        TextButton(onClick=openMeals,modifier=Modifier.heightIn(min=48.dp)){Icon(Icons.Outlined.RestaurantMenu,null);Spacer(Modifier.width(8.dp));Text(tr("Наборы еды","Saved meals"))}
        if(snapshot.savedMeals.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(snapshot.savedMeals.sortedBy{it.name}.take(5),key={it.id}){meal->SuggestionChip(onClick={vm.addSavedMeal(meal,date,openDraft)},label={Text(meal.name,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=200.dp))},modifier=Modifier.heightIn(min=48.dp))}}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun WaterActions(vm:AppViewModel,date:String) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(200,250,500).forEach{ml->FilterChip(selected=settings.waterMl==ml,onClick={vm.waterSize(ml)},label={Text("$ml $ML")},modifier=Modifier.heightIn(min=48.dp))}
        }
        TextButton(onClick={vm.water(date)},modifier=Modifier.heightIn(min=48.dp)){Icon(Icons.Outlined.WaterDrop,null);Spacer(Modifier.width(6.dp));Text(tr("+${settings.waterMl} мл воды","+${settings.waterMl} ml water"))}
    }
}
