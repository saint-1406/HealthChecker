package az.healthchecker.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.util.UUID

@Composable fun MealDraftCard(draft:MealDraft,open:()->Unit) {
    Panel(Modifier.clickable(onClick=open).testTag("mealDraftCard")) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Restaurant,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){Text(tr("Продолжить приём пищи","Continue your meal"),style=MaterialTheme.typography.titleMedium);Text("${dayMonth(draft.date)} · ${count(draft.items.size,"продукт","продукта","продуктов","food","foods")}",style=MaterialTheme.typography.bodySmall)}
            Icon(Icons.Outlined.ChevronRight,tr("Открыть приём пищи","Open the meal"))
        }
    }
}

@Composable fun MealDraftScreen(vm:AppViewModel,draft:MealDraft,back:()->Unit,addMore:()->Unit,edit:(MealItem)->Unit,commit:()->Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var save by rememberSaveable{mutableStateOf(false)}
    val total=summarize(draft.entries(java.time.Instant.EPOCH,java.time.ZoneId.of("UTC")))
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("mealDraftList"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))};Text(tr("Приём пищи","Meal"),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));IconButton(onClick={vm.discardMealDraft(back)},enabled=!busy){Icon(Icons.Outlined.DeleteOutline,tr("Очистить приём","Clear the meal"))}}}
        item{Text(dayMonth(draft.date,true)+" · ${number(total.kcal.value)}${if(total.kcal.complete)"" else "+"} $KCAL",style=MaterialTheme.typography.titleMedium)}
        item{Text(tr("Продукты запишутся отдельно. Можно изменить порции перед сохранением.","Foods will be logged separately. You can adjust their portions before saving."),style=MaterialTheme.typography.bodySmall)}
            items(draft.items,key={it.id}){item->Panel {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f).clickable{edit(item)}.heightIn(min=48.dp)){
                        Text(item.food.displayName,style=MaterialTheme.typography.titleMedium)
                        val c=calculate(item.food,item.quantity)
                        Text("${quantityLabel(item.quantity,c.grams)} · ${number(c.nutrients.kcal)} $KCAL",style=MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick={edit(item)}){Icon(Icons.Outlined.Edit,tr("Изменить порцию: ","Edit portion: ")+item.food.displayName)}
                    IconButton(onClick={vm.removeMealItem(item.id)},enabled=!busy){Icon(Icons.Outlined.Close,tr("Убрать продукт: ","Remove food: ")+item.food.displayName)}
                }
            }}
        item{OutlinedButton(onClick=addMore,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(tr("Добавить ещё продукт","Add another food"))}}
        item{Button(onClick=commit,enabled=draft.items.isNotEmpty()&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(tr("Записать всё","Log all"))}}
        item{TextButton(onClick={save=true},enabled=draft.items.isNotEmpty()&&!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Сохранить набор","Save as a meal"))}}
    }
    if(save)MealNameDialog(busy,{save=false}){name->vm.persistSavedMeal(SavedMeal("meal:${UUID.randomUUID()}",name,draft.items)){save=false}}
}

@Composable private fun MealNameDialog(busy:Boolean,dismiss:()->Unit,save:(String)->Unit) {
    var name by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=dismiss,title={Text(tr("Название набора","Meal name"))},text={OutlinedTextField(name,{name=it.take(100)},label={Text(tr("Например, мой завтрак","For example, my breakfast"))},modifier=Modifier.fillMaxWidth().testTag("mealName"))},
        confirmButton={TextButton(onClick={save(name.trim())},enabled=name.isNotBlank()&&!busy,modifier=Modifier.testTag("saveMeal")){Text(tr("Сохранить","Save"))}},dismissButton={TextButton(onClick=dismiss){Text(tr("Отмена","Cancel"))}})
}

@Composable fun SavedMealsScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,back:()->Unit,add:(SavedMeal)->Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by rememberSaveable{mutableStateOf<String?>(null)}
    var name by rememberSaveable{mutableStateOf("")}
    var removed by rememberSaveable{mutableStateOf(setOf<String>())}
    var selected by rememberSaveable{mutableStateOf(setOf<String>())}
    val existing=snapshot.savedMeals.find{it.id==editing}
    fun open(meal:SavedMeal?){editing=meal?.id?:"new";name=meal?.name?:"";removed=emptySet();selected=emptySet()}
    if(editing!=null) {
        val source=snapshot.day(date).entries
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton(onClick={editing=null}){Text(tr("Назад","Back"))}
            SectionTitle(if(existing==null)tr("Новый набор","New saved meal")else tr("Изменить набор","Edit saved meal"),tr("Сохраните привычную еду для быстрого повтора.","Save foods you often eat together to log them quickly."))
            OutlinedTextField(name,{name=it.take(100)},label={Text(tr("Название набора","Meal name"))},modifier=Modifier.fillMaxWidth().testTag("mealName"))
            existing?.items?.filter{it.id !in removed}?.forEach{item->Row(verticalAlignment=Alignment.CenterVertically){Text(item.food.displayName,modifier=Modifier.weight(1f));IconButton(onClick={removed=removed+item.id}){Icon(Icons.Outlined.Close,tr("Убрать ","Remove ")+item.food.displayName)}}}
            SectionTitle(tr("Добавить из дневника","Add from diary"),dayMonth(date))
            if(source.isEmpty())Text(tr("Сначала запишите продукты в дневник или сохраните набор из корзины приёма пищи.","Log foods first, or save a meal from the meal basket."))
            source.forEach{e->Row(Modifier.fillMaxWidth().clickable{selected=if(e.id in selected)selected-e.id else selected+e.id}.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=e.id in selected,onCheckedChange=null);Column(Modifier.weight(1f)){Text(vm.entryName(e));Text(quantityLabel(e.quantity,e.grams),style=MaterialTheme.typography.bodySmall)}}}
            val retained=existing?.items?.filter{it.id !in removed}.orEmpty()
            Button(onClick={
                val additions=source.filter{it.id in selected}.map{MealItem(foodSnapshot(vm.foodFor(it)),it.quantity)}
                vm.persistSavedMeal(SavedMeal(existing?.id?:"meal:${UUID.randomUUID()}",name.trim(),retained+additions)){editing=null}
            },enabled=name.isNotBlank()&&(retained.isNotEmpty()||selected.isNotEmpty())&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("saveMeal")){Text(tr("Сохранить набор","Save meal"))}
        }
    } else LazyColumn(Modifier.fillMaxSize().testTag("savedMealsList"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}}
        item{SectionTitle(tr("Наборы еды","Saved meals"),tr("Например, обычный завтрак. При добавлении порции можно изменить.","For example, your usual breakfast. You can adjust portions before logging."))}
        item{OutlinedButton(onClick={open(null)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(tr("Новый набор из дневника","New meal from diary"))}}
            if(snapshot.savedMeals.isEmpty())item{Text(tr("Наборов пока нет. Их также можно сохранять во время записи нескольких продуктов.","No saved meals yet. You can also save one while logging several foods."))}
            items(snapshot.savedMeals.sortedBy{it.name},key={it.id}){meal->Panel {
                Text(meal.name,style=MaterialTheme.typography.titleMedium)
                Text(meal.items.joinToString(" · "){it.food.displayName},style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={open(meal)},modifier=Modifier.weight(1f)){Text(tr("Изменить","Edit"))}
                    IconButton(onClick={vm.deleteSavedMeal(meal)},enabled=!busy){Icon(Icons.Outlined.DeleteOutline,tr("Удалить набор: ","Delete meal: ")+meal.name)}
                    FilledTonalIconButton(onClick={add(meal)},enabled=!busy){Icon(Icons.Outlined.Add,tr("Добавить набор: ","Add meal: ")+meal.name)}
                }
            }}
    }
}
