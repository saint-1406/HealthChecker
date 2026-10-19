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
    val preview=recipe.mapCatching{buildDish(d.id,d.name.ifBlank{"Блюдо"},it)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Назад")}
        SectionTitle(if(editing)"Изменить блюдо" else "Новое блюдо","Добавьте ингредиенты в том количестве, в каком они пошли в блюдо. Сырые и готовые продукты различаются.")
        OutlinedTextField(d.name,{v->vm.editDish{it.copy(name=v.take(200))}},label={Text("Название блюда")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("dishName"))
        Text("Иконка в инвентаре",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){DISH_ICONS.forEach{(key,icon,label)->FilterChip(selected=d.icon==key,onClick={vm.editDish{it.copy(icon=key)}},label={Icon(icon,label,Modifier.size(22.dp))})}}
        Text("Ингредиенты",style=MaterialTheme.typography.titleMedium)
        if(d.ingredients.isEmpty())Text("Пока пусто. Например: куриная грудка 500 г, морковь, картофель 5 шт, зелень.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        d.ingredients.forEachIndexed{index,i->
            Panel(Modifier.clickable{editIngredient(index)}) {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(i.name,fontWeight=FontWeight.SemiBold);Text("${quantityLabel(i.quantity,i.grams)} · ${number(i.nutrients.kcal)} ккал",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={vm.editDish{it.copy(ingredients=it.ingredients.filterIndexed{n,_->n!=index})}}){Icon(Icons.Outlined.DeleteOutline,"Убрать ${i.name}")}
                }
            }
        }
        OutlinedButton(onClick=addIngredient,modifier=Modifier.fillMaxWidth().testTag("addIngredient")){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Добавить ингредиент")}
        totals?.let{t->Panel{
            Text("ВСЕГО В БЛЮДЕ",style=MaterialTheme.typography.labelMedium)
            Text("${number(t.nutrients.kcal)} ккал",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
            Text("Б ${number(t.nutrients.protein,1)} г · Ж ${number(t.nutrients.fat,1)} г · У ${number(t.nutrients.carbs,1)} г",style=MaterialTheme.typography.bodyMedium)
            Text("Вес сырых ингредиентов: ${number(t.rawGrams)} г",style=MaterialTheme.typography.bodySmall)
        }}
        OutlinedTextField(d.cooked,{v->vm.editDish{it.copy(cooked=v.take(12))}},label={Text("Вес готового блюда, г (необязательно)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("dishCooked"))
        Text("При варке и жарке вес меняется: вода выкипает или впитывается. Взвесьте готовое блюдо без посуды — тогда калории на 100 г будут точнее. Без веса расчёт идёт по сырым ингредиентам.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(d.servings,{v->vm.editDish{it.copy(servings=v.filter(Char::isDigit).take(3))}},label={Text("Количество порций (необязательно)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
        preview.getOrNull()?.let{f->Panel{
            Text("НА 100 г ГОТОВОГО БЛЮДА",style=MaterialTheme.typography.labelMedium)
            Text("${number(f.per100g.kcal)} ккал",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("dishPer100"))
            Text("Б ${number(f.per100g.protein,1)} г · Ж ${number(f.per100g.fat,1)} г · У ${number(f.per100g.carbs,1)} г")
            f.portions.drop(1).firstOrNull()?.let{p->Text("${p.name}: ${number(p.grams)} г ≈ ${number(f.per100g.kcal!!*p.grams/100)} ккал",style=MaterialTheme.typography.bodyMedium)}
            Text("В дневник записывайте, сколько граммов съели — калории посчитаются от этих значений.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        if(d.ingredients.isNotEmpty())preview.exceptionOrNull()?.let{Text(it.message?:"Проверьте данные",color=MaterialTheme.colorScheme.error)}
        Button(onClick={vm.saveDish(saved)},enabled=preview.isSuccess&&d.name.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("saveDish")){Text(if(editing)"Сохранить изменения" else "Сохранить блюдо")}
        if(editing)Text("Изменения применятся к новым записям. Уже записанная еда сохраняет прежние значения.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
