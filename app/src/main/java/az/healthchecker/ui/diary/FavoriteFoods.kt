package az.healthchecker.ui.diary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*

/**
 * Diary → Favorites: the starred foods (tap to log a portion, star to remove) and the foods eaten most often lately
 * that are not starred yet (star to add).
 */
fun LazyListScope.favoriteFoods(vm:AppViewModel,snapshot:UserSnapshot,date:String,busy:Boolean,select:(Food)->Unit) {
    val favorites=snapshot.favoriteFoods.mapNotNull{vm.food(it)}.sortedBy{it.displayName.lowercase(Lang.locale)}
    item{SectionTitle(tr("Избранные продукты","Favorite foods"),if(favorites.isEmpty())tr("Отмечайте звездой то, что едите часто: в поиске, в записях дня или ниже — и записывайте отсюда в одно касание.","Star what you eat often — in search, in the day's entries or below — and log it from here in one tap.")
        else tr("Нажмите на продукт, чтобы записать порцию на ","Tap a food to log a portion for ")+dayMonth(date))}
    if(favorites.isEmpty())item{Panel(Modifier.testTag("noFavoriteFoods")){Icon(Icons.Outlined.StarBorder,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary);Text(tr("Пока пусто","Nothing here yet"),style=MaterialTheme.typography.titleMedium)}}
    items(favorites,key={"fav:"+it.id}){f->FoodRow(f,true,busy,{vm.toggleFavorite(f.id)}){select(f)}}
    val frequent=snapshot.frequentFoods(date).mapNotNull{vm.food(it)}
    if(frequent.isNotEmpty()){
        item{SectionTitle(tr("Часто едите","You often eat"),tr("За последние $FREQUENT_DAYS дней. Звезда добавляет в избранное.","Over the last $FREQUENT_DAYS days. The star adds it to favorites."))}
        items(frequent,key={"often:"+it.id}){f->FoodRow(f,false,busy,{vm.toggleFavorite(f.id)}){select(f)}}
    }
}

@Composable private fun FoodRow(f:Food,starred:Boolean,busy:Boolean,star:()->Unit,open:()->Unit) = Panel(Modifier.testTag(if(starred)"favoriteFood" else "frequentFood")) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).clickable(onClick=open),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            EntryBadge(f.displayName,f.isWater,f.recipe!=null)
            Column(Modifier.weight(1f)) {
                Text(f.displayName,fontWeight=FontWeight.SemiBold,maxLines=3)
                Text("${number(f.per100g.kcal)} $KCAL ${tr("на 100 г","per 100 g")} · ${macrosLine(f.per100g)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FavoriteStar(starred,f.displayName,!busy,star)
    }
}
