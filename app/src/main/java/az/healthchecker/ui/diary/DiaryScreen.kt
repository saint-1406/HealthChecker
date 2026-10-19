package az.healthchecker.ui.diary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*

@Composable fun DiaryScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,add:()->Unit,edit:(DiaryEntry)->Unit,repeat:(DiaryEntry)->Unit) {
    val day=snapshot.day(date)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{Button(onClick=add,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Icon(Icons.Outlined.Add,"Добавить еду");Spacer(Modifier.width(8.dp));Text("Записать еду")}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(plural(day.entries.size,"запись","записи","записей"),style=MaterialTheme.typography.titleMedium);TextButton(onClick={vm.water(date)}){Icon(Icons.Outlined.WaterDrop,null);Spacer(Modifier.width(4.dp));Text("+250 мл воды")}}}
        if(day.entries.isEmpty())item{Panel{Icon(Icons.Outlined.Restaurant,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary);SectionTitle("Пока ничего не записано","Добавьте то, что съели. Мы посчитаем остальное.")}}
        items(day.entries,key={it.id}) {entry->Panel {
            Row(Modifier.fillMaxWidth().clickable{edit(entry)},verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(entry.name,fontWeight=FontWeight.SemiBold);Text("${quantityLabel(entry.quantity,entry.grams)} · белок ${number(entry.nutrients.protein,1)} г",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text("${number(entry.nutrients.kcal)}\nккал",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            }
            Row{TextButton(onClick={edit(entry)}){Text("Изменить")};IconButton(onClick={vm.delete(entry)}){Icon(Icons.Outlined.DeleteOutline,"Удалить ${entry.name}")}}
        }}
        item{Panel{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=day.completed,onCheckedChange={vm.completeDay(date,it)});Column{Text("День заполнен",fontWeight=FontWeight.Medium);Text("Учитывать в средней статистике",style=MaterialTheme.typography.bodySmall)}}}}
        val recent=snapshot.entries.sortedByDescending{it.instant}.distinctBy{it.foodId}.take(5)
        if(recent.isNotEmpty()){item{SectionTitle("Недавние продукты","Быстро повторите запись")};items(recent,key={"recent:${it.foodId}"}){e->TextButton(onClick={repeat(e)},modifier=Modifier.fillMaxWidth()){Text(e.name,modifier=Modifier.weight(1f));Icon(Icons.Outlined.Add,null)}}}
    }
}
