package az.healthchecker.ui.diary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.inventory.DISH_ICONS

/** Dish builder: raw ingredients in, nutrition per 100 g of the finished dish out; saved as the user's own product. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun DishEditor(vm:AppViewModel,back:()->Unit,addIngredient:()->Unit,editIngredient:(Int)->Unit,saved:(Food)->Unit) {
    val draft by vm.dish.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val d=draft?:return
    val editing=snapshot.customFoods.any{it.id==d.id}
    val recipe=runCatching{d.recipe()}
    val totals=recipe.getOrNull()?.takeIf{it.ingredients.isNotEmpty()}?.let(::dishTotals)
    val preview=recipe.mapCatching{buildDish(d.id,d.name.ifBlank{tr("Блюдо","Dish")},it)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
        SectionTitle(if(editing)tr("Изменить блюдо","Edit dish") else tr("Новое блюдо","New dish"),tr("Добавьте ингредиенты в том количестве, в каком они пошли в блюдо. Сырые и готовые продукты различаются.","Add the ingredients in the amounts that went into the dish. Raw and cooked foods differ."))
        OutlinedTextField(d.name,{v->vm.editDish{it.copy(name=v.take(200))}},label={Text(tr("Название блюда","Dish name"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("dishName"))
        Text(tr("Иконка в инвентаре","Inventory icon"),style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){DISH_ICONS.forEach{(key,icon,label)->FilterChip(selected=d.icon==key,onClick={vm.editDish{it.copy(icon=key)}},label={Icon(icon,label,Modifier.size(22.dp))})}}
        Text(tr("Ингредиенты","Ingredients"),style=MaterialTheme.typography.titleMedium)
        if(d.ingredients.isEmpty())Text(tr("Пока пусто. Например: куриная грудка 500 г, морковь, картофель 5 шт, зелень.","Empty so far. For example: chicken breast 500 g, carrots, 5 potatoes, herbs."),color=MaterialTheme.colorScheme.onSurfaceVariant)
        d.ingredients.forEachIndexed{index,i->
            Panel(Modifier.clickable{editIngredient(index)}) {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(vm.food(i.foodId)?.takeIf{!it.id.startsWith("custom:")}?.displayName?:i.name,fontWeight=FontWeight.SemiBold);Text("${quantityLabel(i.quantity,i.grams)} · ${number(i.nutrients.kcal)} $KCAL",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={vm.editDish{it.copy(ingredients=it.ingredients.filterIndexed{n,_->n!=index})}}){Icon(Icons.Outlined.DeleteOutline,tr("Убрать ","Remove ")+i.name)}
                }
            }
        }
        OutlinedButton(onClick=addIngredient,modifier=Modifier.fillMaxWidth().testTag("addIngredient")){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(tr("Добавить ингредиент","Add ingredient"))}
        totals?.let{t->Panel{
            Text(tr("ВСЕГО В БЛЮДЕ","WHOLE DISH"),style=MaterialTheme.typography.labelMedium)
            Text("${number(t.nutrients.kcal)} $KCAL",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
            Text(macrosLine(t.nutrients),style=MaterialTheme.typography.bodyMedium)
            Text(tr("Вес сырых ингредиентов: ","Raw ingredient weight: ")+"${number(t.rawGrams)} $G",style=MaterialTheme.typography.bodySmall)
        }}
        OutlinedTextField(d.cooked,{v->vm.editDish{it.copy(cooked=v.take(12))}},label={Text(tr("Вес готового блюда, г (необязательно)","Cooked weight, g (optional)"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("dishCooked"))
        Text(tr("При варке и жарке вес меняется: вода выкипает или впитывается. Взвесьте готовое блюдо без посуды — тогда калории на 100 г будут точнее. Без веса расчёт идёт по сырым ингредиентам.","Cooking changes the weight: water boils off or is absorbed. Weigh the finished dish without the pot for more accurate calories per 100 g. Without it the raw ingredients are used."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(d.servings,{v->vm.editDish{it.copy(servings=v.filter(Char::isDigit).take(3))}},label={Text(tr("Количество порций (необязательно)","Number of servings (optional)"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
        preview.getOrNull()?.let{f->Panel{
            Text(tr("НА 100 г ГОТОВОГО БЛЮДА","PER 100 g OF THE DISH"),style=MaterialTheme.typography.labelMedium)
            Text("${number(f.per100g.kcal)} $KCAL",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("dishPer100"))
            Text(macrosLine(f.per100g))
            f.portions.drop(1).firstOrNull()?.let{p->Text("${p.displayName}: ${number(p.grams)} $G ≈ ${number(f.per100g.kcal!!*p.grams/100)} $KCAL",style=MaterialTheme.typography.bodyMedium)}
            Text(tr("В дневник записывайте, сколько граммов съели — калории посчитаются от этих значений.","Log how many grams you ate — calories follow from these values."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        if(d.ingredients.isNotEmpty())preview.exceptionOrNull()?.let{Text(it.message?:tr("Проверьте данные","Check the values"),color=MaterialTheme.colorScheme.error)}
        Button(onClick={vm.saveDish(saved)},enabled=preview.isSuccess&&d.name.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("saveDish")){Text(if(editing)tr("Сохранить изменения","Save changes") else tr("Сохранить блюдо","Save dish"))}
        if(editing)Text(tr("Изменения применятся к новым записям. Уже записанная еда сохраняет прежние значения.","Changes apply to new entries. Food already logged keeps its values."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
