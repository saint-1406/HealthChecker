package az.healthchecker.ui.inventory
import androidx.compose.foundation.clickable
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
import java.time.format.DateTimeFormatter

/** Minimal outline pictures a dish can carry in the inventory; the key is stored in the recipe. */
val DISH_ICONS:List<Triple<String,ImageVector,String>> = listOf(
    Triple("plate",Icons.Outlined.DinnerDining,"Блюдо"),Triple("soup",Icons.Outlined.SoupKitchen,"Суп"),Triple("bowl",Icons.Outlined.RiceBowl,"Гарнир, каша"),
    Triple("meat",Icons.Outlined.KebabDining,"Мясо"),Triple("salad",Icons.Outlined.Eco,"Салат"),Triple("bakery",Icons.Outlined.BakeryDining,"Выпечка"),
    Triple("breakfast",Icons.Outlined.EggAlt,"Завтрак"),Triple("dessert",Icons.Outlined.Cake,"Десерт"),Triple("drink",Icons.Outlined.LocalCafe,"Напиток"))
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
            Column(Modifier.weight(1f)){Text("Инвентарь",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text(if(dishes.isEmpty())"Здесь хранятся приготовленные вами блюда" else plural(dishes.size,"блюдо","блюда","блюд"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),maxItemsInEachRow=3){
            dishes.forEach{d->InventoryCell(dishIcon(d.recipe?.icon),d.name,"${number(d.per100g.kcal)} ккал/100 г",Modifier.weight(1f)){openId=d.id}}
            InventoryCell(Icons.Outlined.Add,"Новое блюдо","из ингредиентов",Modifier.weight(1f).testTag("newDishCell"),accent=true,onClick=create)
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
                Column(Modifier.weight(1f)){Text(dish.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("Своё блюдо · ${plural(recipe.ingredients.size,"ингредиент","ингредиента","ингредиентов")}",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick=edit){Icon(Icons.Outlined.Edit,"Изменить блюдо")}
            }
            Panel {
                Text("НА 100 г",style=MaterialTheme.typography.labelMedium)
                Text("${number(dish.per100g.kcal)} ккал",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                Text("Б ${number(dish.per100g.protein,1)} г · Ж ${number(dish.per100g.fat,1)} г · У ${number(dish.per100g.carbs,1)} г")
                total?.let{Text("Всё блюдо: ${number(it)} г ≈ ${number(dish.per100g.kcal!!*it/100)} ккал${if(recipe.cookedGrams==null)" (по весу сырых ингредиентов)" else ""}",style=MaterialTheme.typography.bodySmall)}
            }
            Text("Ингредиенты",style=MaterialTheme.typography.titleSmall)
            recipe.ingredients.forEach{i->Text("• ${i.name} — ${quantityLabel(i.quantity,i.grams)}",style=MaterialTheme.typography.bodySmall)}
            HorizontalDivider()
            Text("Сколько съели",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(amount,{amount=it.take(12)},label={Text("Граммы")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("eatGrams"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                dish.portions.drop(1).forEach{p->SuggestionChip(onClick={amount=plain(p.grams)},label={Text("${p.name}: ${number(p.grams)} г")})}
                listOf(150.0,250.0,350.0).forEach{g->SuggestionChip(onClick={amount=plain(g)},label={Text("${number(g)} г")})}
            }
            Text(eaten.getOrNull()?.let{"≈ ${number(it.nutrients.kcal)} ккал · Б ${number(it.nutrients.protein,1)} г"}?:"Введите вес съеденной порции",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("eatPreview"))
            Text("Запись в дневник за ${if(date==LocalDate.now().toString())"сегодня" else LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM",RU))}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={vm.eat(dish,Quantity(grams.getOrThrow(),Measure.GRAMS),date,close)},enabled=eaten.isSuccess&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("eatDish")){Icon(Icons.Outlined.Restaurant,null);Spacer(Modifier.width(8.dp));Text("Съесть")}
            OutlinedButton(onClick={confirmDelete=true},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("deleteDish"),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text("Удалить блюдо")}
        }
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},icon={Icon(Icons.Outlined.DeleteOutline,null)},title={Text("Удалить «${dish.name}»?")},
        text={Text("Блюдо исчезнет из инвентаря и поиска. Уже записанная еда в дневнике сохранится.")},
        confirmButton={TextButton(onClick={confirmDelete=false;close();vm.deleteDish(dish)}){Text("Удалить")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Отмена")}})
}
