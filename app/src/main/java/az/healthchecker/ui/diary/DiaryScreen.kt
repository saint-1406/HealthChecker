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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DiaryScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,add:()->Unit,edit:(DiaryEntry)->Unit,repeat:(DiaryEntry)->Unit,editMedication:(Medication?)->Unit,openPhoto:(InboxPhoto)->Unit) {
    val day=snapshot.day(date)
    val capture=rememberPhotoCapture(vm)
    val photos by vm.photos.collectAsStateWithLifecycle()
    val medications=snapshot.medicationDay(date)
    LazyColumn(Modifier.fillMaxSize().testTag("diaryList"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{DaySummaryCard(day)}
        // The camera button sits next to "Log food"; the photo card appears only while photos wait, so entries stay near the top.
        item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick=add,modifier=Modifier.weight(1f).heightIn(min=54.dp)){Icon(Icons.Outlined.Add,tr("Добавить еду","Add food"));Spacer(Modifier.width(8.dp));Text(tr("Записать еду","Log food"))}
            PhotoMenuButton(capture)
        }}
        if(photos.isNotEmpty())item{PhotoInboxCard(vm,capture,openPhoto)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(count(day.entries.size,"запись","записи","записей","entry","entries"),style=MaterialTheme.typography.titleMedium);TextButton(onClick={vm.water(date)}){Icon(Icons.Outlined.WaterDrop,null);Spacer(Modifier.width(4.dp));Text(tr("+250 мл воды","+250 ml water"))}}}
        if(day.entries.isEmpty())item{Panel{Icon(Icons.Outlined.Restaurant,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary);SectionTitle(tr("Пока ничего не записано","Nothing logged yet"),tr("Добавьте то, что съели. Мы посчитаем остальное.","Add what you ate. We'll count the rest."))}}
        items(day.entries,key={it.id}) {entry->Panel {
            val name=vm.entryName(entry)
            Row(Modifier.fillMaxWidth().clickable{edit(entry)},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                EntryBadge(name,entry.waterMl>0,vm.food(entry.foodId)?.recipe!=null)
                Column(Modifier.weight(1f)){
                    Text(name,fontWeight=FontWeight.SemiBold)
                    Text(listOf(entryTime(entry),quantityLabel(entry.quantity,entry.grams)).filter{it.isNotEmpty()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    // Every entry shows its own protein, fat, carbohydrate and fibre, and how its energy splits.
                    Text(macrosLine(entry.nutrients),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(entry.waterMl==0.0)MacroBar(entry.nutrients,Modifier.fillMaxWidth(0.7f).padding(top=4.dp),5.dp)
                }
                Text("${number(entry.nutrients.kcal)}\n$KCAL",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            }
            Row{TextButton(onClick={edit(entry)}){Text(tr("Изменить","Edit"))};IconButton(onClick={vm.delete(entry)}){Icon(Icons.Outlined.DeleteOutline,tr("Удалить ","Delete ")+name)}}
        }}
        item{Panel{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=day.completed,onCheckedChange={vm.completeDay(date,it)});Column{Text(tr("День заполнен","Day complete"),fontWeight=FontWeight.Medium);Text(tr("Учитывать в средней статистике","Count it in the averages"),style=MaterialTheme.typography.bodySmall)}}}}
        item{Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Outlined.Medication,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){SectionTitle(tr("Лекарства и витамины","Medicines and vitamins"),if(medications.isEmpty())tr("Препараты с картой: дозировка, расписание, курс","Cards with dose, schedule and course") else tr("Отметьте приём, когда примете","Mark a dose when you take it"))}
        }}
        items(medications,key={"med:"+it.medication.id}){m->MedicationRow(vm,m,date){editMedication(m.medication)}}
        item{OutlinedButton(onClick={editMedication(null)},modifier=Modifier.fillMaxWidth().testTag("addMedication")){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text(tr("Добавить препарат","Add a medicine or vitamin"))}}
        val recent=snapshot.entries.sortedByDescending{it.instant}.distinctBy{it.foodId}.take(5)
        if(recent.isNotEmpty()){item{SectionTitle(tr("Недавние продукты","Recent foods"),tr("Быстро повторите запись","Repeat an entry quickly"))};items(recent,key={"recent:${it.foodId}"}){e->TextButton(onClick={repeat(e)},modifier=Modifier.fillMaxWidth()){Text(vm.entryName(e),modifier=Modifier.weight(1f));Icon(Icons.Outlined.Add,null)}}}
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
