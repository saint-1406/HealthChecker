package az.healthchecker.ui.inventory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.LocalDate

/** Minimal outline pictures a dish can carry in the inventory; the key is stored in the recipe. */
val DISH_ICONS:List<Triple<String,ImageVector,String>> get() = listOf(
    Triple("plate",Icons.Outlined.DinnerDining,tr("Блюдо","Dish")),Triple("soup",Icons.Outlined.SoupKitchen,tr("Суп","Soup")),Triple("bowl",Icons.Outlined.RiceBowl,tr("Гарнир, каша","Side, porridge")),
    Triple("meat",Icons.Outlined.KebabDining,tr("Мясо","Meat")),Triple("salad",Icons.Outlined.Eco,tr("Салат","Salad")),Triple("bakery",Icons.Outlined.BakeryDining,tr("Выпечка","Baking")),
    Triple("breakfast",Icons.Outlined.EggAlt,tr("Завтрак","Breakfast")),Triple("dessert",Icons.Outlined.Cake,tr("Десерт","Dessert")),Triple("drink",Icons.Outlined.LocalCafe,tr("Напиток","Drink")))
fun dishIcon(key:String?):ImageVector = DISH_ICONS.find{it.first==key}?.second?:Icons.Outlined.DinnerDining

/** Profile → Inventory: every cooked dish as a cell; tapping opens the dish sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun InventorySection(vm:AppViewModel,date:String,create:()->Unit,edit:(Food)->Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val dishes=snapshot.customFoods.filter{it.recipe!=null}.sortedBy{it.name.lowercase()}
    var openId by rememberSaveable{mutableStateOf("")}
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(Icons.Outlined.Inventory2,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){Text(tr("Инвентарь","Inventory"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text(if(dishes.isEmpty())tr("Здесь хранятся приготовленные вами блюда","Dishes you cook are kept here") else count(dishes.size,"блюдо","блюда","блюд","dish","dishes"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),maxItemsInEachRow=3){
            dishes.forEach{d->InventoryCell(dishIcon(d.recipe?.icon),d.name,"${number(d.per100g.kcal)} $KCAL/100 $G",Modifier.weight(1f)){openId=d.id}}
            InventoryCell(Icons.Outlined.Add,tr("Новое блюдо","New dish"),tr("из ингредиентов","from ingredients"),Modifier.weight(1f).testTag("newDishCell"),accent=true,onClick=create)
            // Keeps the last row's cells the same width as full rows.
            repeat((3-(dishes.size+1)%3)%3){Spacer(Modifier.weight(1f))}
        }
    }
    dishes.find{it.id==openId}?.let{d->DishSheet(vm,d,date,{openId=""},{openId="";edit(d)})}
}

@Composable private fun InventoryCell(icon:ImageVector,title:String,caption:String,modifier:Modifier,accent:Boolean=false,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=modifier.heightIn(min=118.dp).semantics{contentDescription=title},shape=RoundedCornerShape(18.dp),
        color=if(accent)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
            Icon(icon,null,Modifier.size(30.dp),tint=MaterialTheme.colorScheme.primary)
            Text(title,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(caption,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
        }
    }
}

/** Dish information with the two actions asked for: eat an entered amount, or delete the dish. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable private fun DishSheet(vm:AppViewModel,dish:Food,date:String,close:()->Unit,edit:()->Unit) {
    val recipe=dish.recipe!!;val total=dish.portions.firstOrNull()?.grams
    val busy by vm.busy.collectAsStateWithLifecycle()
    var amount by rememberSaveable(dish.id){mutableStateOf("")}
    var confirmDelete by remember{mutableStateOf(false)}
    val grams=runCatching{parseQuantity(amount)}
    val eaten=grams.mapCatching{calculate(dish,Quantity(it,Measure.GRAMS))}
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer){Icon(dishIcon(recipe.icon),null,Modifier.padding(12.dp).size(32.dp),tint=MaterialTheme.colorScheme.primary)}
                Column(Modifier.weight(1f)){Text(dish.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text(tr("Своё блюдо · ","Your dish · ")+count(recipe.ingredients.size,"ингредиент","ингредиента","ингредиентов","ingredient","ingredients"),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick=edit){Icon(Icons.Outlined.Edit,tr("Изменить блюдо","Edit dish"))}
            }
            Panel {
                Text(tr("НА 100 г","PER 100 g"),style=MaterialTheme.typography.labelMedium)
                Text("${number(dish.per100g.kcal)} $KCAL",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                Text(macrosLine(dish.per100g))
                total?.let{Text(tr("Всё блюдо: ","Whole dish: ")+"${number(it)} $G ≈ ${number(dish.per100g.kcal!!*it/100)} $KCAL${if(recipe.cookedGrams==null)tr(" (по весу сырых ингредиентов)"," (by raw ingredient weight)") else ""}",style=MaterialTheme.typography.bodySmall)}
            }
            Text(tr("Ингредиенты","Ingredients"),style=MaterialTheme.typography.titleSmall)
            recipe.ingredients.forEach{i->Text("• ${vm.food(i.foodId)?.takeIf{!it.id.startsWith("custom:")}?.displayName?:i.name} — ${quantityLabel(i.quantity,i.grams)}",style=MaterialTheme.typography.bodySmall)}
            HorizontalDivider()
            Text(tr("Сколько съели","How much you ate"),style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(amount,{amount=it.take(12)},label={Text(tr("Граммы","Grams"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("eatGrams"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                dish.portions.drop(1).forEach{p->SuggestionChip(onClick={amount=plain(p.grams)},label={Text("${p.displayName}: ${number(p.grams)} $G")})}
                listOf(150.0,250.0,350.0).forEach{g->SuggestionChip(onClick={amount=plain(g)},label={Text("${number(g)} $G")})}
            }
            Text(eaten.getOrNull()?.let{"≈ ${number(it.nutrients.kcal)} $KCAL · ${tr("Б","P")} ${number(it.nutrients.protein,1)} $G"}?:tr("Введите вес съеденной порции","Enter the weight you ate"),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("eatPreview"))
            Text(tr("Запись в дневник за ","Logged to the diary for ")+if(date==LocalDate.now().toString())tr("сегодня","today") else dayMonth(date),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={vm.eat(dish,Quantity(grams.getOrThrow(),Measure.GRAMS),date,close)},enabled=eaten.isSuccess&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("eatDish")){Icon(Icons.Outlined.Restaurant,null);Spacer(Modifier.width(8.dp));Text(tr("Съесть","Eat"))}
            OutlinedButton(onClick={confirmDelete=true},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("deleteDish"),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text(tr("Удалить блюдо","Delete dish"))}
        }
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},icon={Icon(Icons.Outlined.DeleteOutline,null)},title={Text(tr("Удалить «${dish.name}»?","Delete “${dish.name}”?"))},
        text={Text(tr("Блюдо исчезнет из инвентаря и поиска. Уже записанная еда в дневнике сохранится.","The dish leaves the inventory and search. Food already logged stays in the diary."))},
        confirmButton={TextButton(onClick={confirmDelete=false;close();vm.deleteDish(dish)}){Text(tr("Удалить","Delete"))}},dismissButton={TextButton(onClick={confirmDelete=false}){Text(tr("Отмена","Cancel"))}})
}
