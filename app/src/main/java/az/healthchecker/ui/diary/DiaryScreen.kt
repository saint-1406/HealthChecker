package az.healthchecker.ui.diary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import az.healthchecker.ui.kit.FilledTonalButton
import az.healthchecker.ui.kit.OutlinedButton
import az.healthchecker.ui.kit.Button
import az.healthchecker.ui.theme.rounded

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DiaryScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,add:()->Unit,edit:(DiaryEntry)->Unit,repeat:(DiaryEntry)->Unit,editMedication:(Medication?)->Unit,openPhoto:(InboxPhoto)->Unit,selectFood:(Food)->Unit,openMeals:()->Unit,openDraft:()->Unit,openProfile:()->Unit,chooseFolder:()->Unit) {
    val day=snapshot.day(date)
    val capture=rememberPhotoCapture(vm)
    val photos by vm.photos.collectAsStateWithLifecycle()
    val medications=snapshot.medicationDay(date)
    val draft by vm.mealDraft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val favoritesTab by vm.diaryFavorites.collectAsStateWithLifecycle()
    val arcadeOn=az.healthchecker.ui.theme.LocalArcade.current
    Column(Modifier.fillMaxSize()) {
    DayFavoritesTabs(favoritesTab,snapshot.favoriteFoods.size,tr("День","Day")){vm.diaryFavorites.value=it}
    if(favoritesTab)LazyColumn(Modifier.fillMaxSize().testTag("favoriteFoodsList"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){favoriteFoods(vm,snapshot,date,busy,selectFood)}
    else LazyColumn(Modifier.fillMaxSize().testTag("diaryList"),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item{DaySummaryCard(day)}
        // The camera button sits next to "Log food"; the photo card appears only while photos wait, so entries stay near the top.
        item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick=add,modifier=Modifier.weight(1f).height(46.dp).testTag("logFoodButton")){Icon(Icons.Outlined.Add,tr("Добавить еду","Add food"));Spacer(Modifier.width(8.dp));Text(tr("Записать еду","Log food"))}
            PhotoMenuButton(capture)
        }}
        draft?.let{d->item{MealDraftCard(d,openDraft)}}
        item{FoodShortcuts(vm,snapshot,date,repeat,selectFood,openMeals,openDraft)}
        item{WaterActions(vm,date)}
        item{SaveStatus(vm,openProfile)}
        item{BackupSetupCard(vm,snapshot,chooseFolder)}
        if(photos.isNotEmpty())item{PhotoInboxCard(vm,capture,openPhoto)}
        item{Text(count(day.entries.size,"запись","записи","записей","entry","entries"),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=6.dp))}
        if(day.entries.isEmpty())item{Panel{Icon(Icons.Outlined.Restaurant,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary);SectionTitle(tr("Пока ничего не записано","Nothing logged yet"),tr("Добавьте то, что съели. Мы посчитаем остальное.","Add what you ate. We'll count the rest."))}}
        // One slim row per entry: badge, name, "time · amount" and macros on one line, a thin energy split, then kcal,
        // the star and delete. Tapping the row edits it.
        // Arcade: compact groups by part of the day — name and portion on the left, calories aligned on the right.
        if(arcadeOn)mealGroups(day.entries).forEach{(slot,list)->
            item(key="slot:"+slot.name){ArcadeMealHeader(slot,list)}
            items(list,key={it.id}){entry->ArcadeEntryRow(vm,snapshot,entry,busy,edit)}
        }
        else items(day.entries,key={it.id}) {entry->Surface(shape=rounded(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
            val name=vm.entryName(entry)
            Row(Modifier.fillMaxWidth().padding(start=10.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable{edit(entry)}.semantics(mergeDescendants=true){contentDescription=tr("Изменить запись: ","Edit entry: ")+name},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    EntryBadge(name,entry.waterMl>0,vm.food(entry.foodId)?.recipe!=null,34.dp)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        Text(name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        // Every entry shows its own protein, fat, carbohydrate and fibre, and how its energy splits.
                        Text(listOf(quantityLabel(entry.quantity,entry.grams),if(entry.waterMl==0.0)macrosLine(entry.nutrients) else "").filter{it.isNotEmpty()}.joinToString(" · "),
                            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(entry.waterMl==0.0)MacroBar(entry.nutrients,Modifier.fillMaxWidth(0.6f).padding(top=2.dp),3.dp)
                    }
                    Column(horizontalAlignment=Alignment.End) {
                        Text("${number(entry.nutrients.kcal)} $KCAL",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary,maxLines=1)
                        Text(entryTime(entry),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(entry.waterMl==0.0&&vm.food(entry.foodId)!=null)FavoriteStar(entry.foodId in snapshot.favoriteFoods,name,!busy){vm.toggleFavorite(entry.foodId)}
                IconButton(onClick={vm.delete(entry)}){Icon(Icons.Outlined.DeleteOutline,tr("Удалить ","Delete ")+name,Modifier.size(20.dp))}
            }
        }}
        item{Surface(shape=rounded(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(end=12.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=day.completed,onCheckedChange={vm.completeDay(date,it)});Column{Text(tr("День заполнен","Day complete"),style=MaterialTheme.typography.titleSmall);Text(tr("Учитывать в средней статистике","Count it in the averages"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
        if(medications.isNotEmpty())item{Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Outlined.Medication,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){SectionTitle(tr("Лекарства и витамины","Medicines and vitamins"),if(medications.isEmpty())tr("Препараты с картой: дозировка, расписание, курс","Cards with dose, schedule and course") else tr("Отметьте приём, когда примете","Mark a dose when you take it"))}
        }}
        items(medications,key={"med:"+it.medication.id}){m->MedicationRow(vm,m,date){editMedication(m.medication)}}
        item{OutlinedButton(onClick={editMedication(null)},modifier=Modifier.fillMaxWidth().testTag("addMedication")){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(tr("Добавить препарат","Add a medicine or vitamin"))}}
    }
    }
}

fun mealSlotName(s:MealSlot):String = when(s){MealSlot.BREAKFAST->tr("Завтрак","Breakfast");MealSlot.LUNCH->tr("Обед","Lunch");MealSlot.DINNER->tr("Ужин","Dinner");MealSlot.LATE->tr("Поздний перекус","Late snack");MealSlot.WATER->tr("Вода","Water")}

/** A group's header in Arcade: the part of the day in caps and its total on the right, over a hairline. */
@Composable private fun ArcadeMealHeader(slot:MealSlot,list:List<DiaryEntry>) {
    Column(Modifier.padding(top=10.dp).testTag("mealGroup-"+slot.name)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            ArcadeLabel(mealSlotName(slot),Modifier.weight(1f))
            Text(if(slot==MealSlot.WATER)"${number(list.sumOf{it.waterMl})} $ML" else "${number(list.sumOf{it.nutrients.kcal?:0.0})} $KCAL",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(Modifier.padding(top=6.dp),color=MaterialTheme.colorScheme.outline)
    }
}

/** A dense Arcade entry: name over "time · portion", the calories right-aligned, then the star and delete. */
@Composable private fun ArcadeEntryRow(vm:AppViewModel,snapshot:UserSnapshot,entry:DiaryEntry,busy:Boolean,edit:(DiaryEntry)->Unit) {
    val name=vm.entryName(entry)
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable{edit(entry)}.padding(vertical=6.dp).semantics(mergeDescendants=true){contentDescription=tr("Изменить запись: ","Edit entry: ")+name},verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                    Text(name,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(entryTime(entry)+" · "+quantityLabel(entry.quantity,entry.grams),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Text(if(entry.waterMl>0)"${number(entry.waterMl)} $ML" else "${number(entry.nutrients.kcal)} $KCAL",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Bold,maxLines=1,modifier=Modifier.padding(start=8.dp))
            }
            if(entry.waterMl==0.0&&vm.food(entry.foodId)!=null)FavoriteStar(entry.foodId in snapshot.favoriteFoods,name,!busy){vm.toggleFavorite(entry.foodId)}
            IconButton(onClick={vm.delete(entry)}){Icon(Icons.Outlined.DeleteOutline,tr("Удалить ","Delete ")+name,Modifier.size(20.dp))}
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}

/** One card in course today: what to take and how, the doses already marked, and "Taken". */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MedicationRow(vm:AppViewModel,m:MedicationDay,date:String,open:()->Unit) {
    val med=m.medication
    Panel(Modifier.testTag("medication")) {
        Row(Modifier.fillMaxWidth().clickable(onClick=open),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(med.name,fontWeight=FontWeight.SemiBold)
                Text(medicationSummary(med),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(med.substances.isNotEmpty())Text(med.substances.joinToString(", ",transform=::substanceLabel),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if(m.done)Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,tint=if(m.done)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
        Text("${tr("Принято","Taken")}: ${m.intakes.size} ${tr("из","of")} ${med.timesPerDay}",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
        if(m.intakes.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){m.intakes.forEach{t->InputChip(selected=false,onClick={vm.deleteIntake(t)},label={Text(Instant.parse(t.instant).atZone(ZoneId.of(t.zoneId)).format(DateTimeFormatter.ofPattern("HH:mm")))},trailingIcon={Icon(Icons.Outlined.Close,tr("Убрать отметку","Remove the mark"),Modifier.size(16.dp))})}}
        FilledTonalButton(onClick={vm.takeMedication(med,date)},modifier=Modifier.fillMaxWidth().testTag("takeMedication")){Icon(Icons.Outlined.Check,null);Spacer(Modifier.width(8.dp));Text(tr("Принял","Taken")+" · "+doseLabel(med.form,med.dose))}
    }
}
