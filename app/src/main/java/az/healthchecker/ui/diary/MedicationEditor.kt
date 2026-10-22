package az.healthchecker.ui.diary
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

fun medKindLabel(k:MedKind):String = when(k){MedKind.MEDICINE->tr("Лекарство","Medicine");MedKind.VITAMIN->tr("Витамин","Vitamin");MedKind.SUPPLEMENT->tr("БАД","Supplement")}
fun formLabel(f:MedForm):String = when(f){MedForm.TABLET->tr("Таблетки","Tablets");MedForm.CAPSULE->tr("Капсулы","Capsules");MedForm.DROPS->tr("Капли","Drops");MedForm.LIQUID->tr("Сироп, раствор","Syrup, solution");MedForm.POWDER->tr("Порошок","Powder");MedForm.SPRAY->tr("Спрей","Spray");MedForm.INJECTION->tr("Инъекции","Injections");MedForm.GUMMY->tr("Жевательные","Chewables");MedForm.OTHER->tr("Другое","Other")}
/** The unit one dose is counted in for each form. */
fun doseUnit(f:MedForm):String = when(f){MedForm.TABLET->tr("табл.","tab");MedForm.CAPSULE->tr("капс.","caps");MedForm.DROPS->tr("кап.","drops");MedForm.LIQUID,MedForm.INJECTION->tr("мл","ml");MedForm.POWDER->tr("г","g");MedForm.SPRAY->tr("впрыск.","sprays");MedForm.GUMMY,MedForm.OTHER->tr("шт.","pcs")}
fun doseLabel(f:MedForm,dose:Double):String = "${plain(dose)} ${doseUnit(f)}"
fun strengthLabel(u:StrengthUnit):String = when(u){StrengthUnit.MG->tr("мг","mg");StrengthUnit.MCG->tr("мкг","mcg");StrengthUnit.G->tr("г","g");StrengthUnit.IU->tr("МЕ","IU");StrengthUnit.ML->tr("мл","ml");StrengthUnit.PERCENT->"%"}
fun timingLabel(t:FoodTiming):String = when(t){FoodTiming.ANY->tr("Неважно","Any time");FoodTiming.BEFORE->tr("До еды","Before meals");FoodTiming.WITH->tr("Во время еды","With meals");FoodTiming.AFTER->tr("После еды","After meals");FoodTiming.EMPTY_STOMACH->tr("Натощак","On an empty stomach")}
fun substanceLabel(s:ActiveSubstance):String = s.name+(s.amount?.let{" ${plain(it)} ${strengthLabel(s.unit)}"}?:"")
/** "Витамин · 1 капс. × 2 в день · после еды · до 30 октября". */
fun medicationSummary(m:Medication):String = listOfNotNull(medKindLabel(m.kind),"${doseLabel(m.form,m.dose)} × ${m.timesPerDay} ${tr("в день","a day")}",
    m.timing.takeIf{it!=FoodTiming.ANY}?.let{timingLabel(it).lowercase(Lang.locale)},m.times.takeIf{it.isNotEmpty()}?.joinToString(", "),
    m.endDate?.let{tr("до ","until ")+dayMonth(it)}).joinToString(" · ")

// Substance rows survive rotation as one string: name, amount and unit per row.
private fun packSubstances(list:List<Triple<String,String,StrengthUnit>>)=list.joinToString("\u0002"){"${it.first}\u0001${it.second}\u0001${it.third.name}"}
private fun unpackSubstances(s:String)=s.split("\u0002").filter{it.isNotEmpty()}.map{r->r.split("\u0001").let{Triple(it[0],it.getOrElse(1){""},StrengthUnit.entries.find{u->u.name==it.getOrNull(2)}?:StrengthUnit.MG)}}

/** The full card of a medicine, vitamin or supplement; [existing] is null for a new one. */
@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable fun MedicationEditor(vm:AppViewModel,existing:Medication?,date:String,back:()->Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val key=existing?.id?:"new"
    var name by rememberSaveable(key){mutableStateOf(existing?.name?:"")}
    var kind by rememberSaveable(key){mutableStateOf((existing?.kind?:MedKind.MEDICINE).name)}
    var form by rememberSaveable(key){mutableStateOf((existing?.form?:MedForm.TABLET).name)}
    var substances by rememberSaveable(key){mutableStateOf(packSubstances(existing?.substances?.map{Triple(it.name,it.amount?.let(::plain)?:"",it.unit)}?:emptyList()))}
    var dose by rememberSaveable(key){mutableStateOf(existing?.dose?.let(::plain)?:"1")}
    var perDay by rememberSaveable(key){mutableStateOf((existing?.timesPerDay?:1).toString())}
    var times by rememberSaveable(key){mutableStateOf(existing?.times?.joinToString(", ")?:"")}
    var timing by rememberSaveable(key){mutableStateOf((existing?.timing?:FoodTiming.ANY).name)}
    var start by rememberSaveable(key){mutableStateOf(existing?.startDate?:date)}
    var end by rememberSaveable(key){mutableStateOf(existing?.endDate?:"")}
    var purpose by rememberSaveable(key){mutableStateOf(existing?.purpose?:"")}
    var doctor by rememberSaveable(key){mutableStateOf(existing?.prescribedBy?:"")}
    var notes by rememberSaveable(key){mutableStateOf(existing?.notes?:"")}
    var picking by remember{mutableStateOf<String?>(null)}
    var confirmDelete by remember{mutableStateOf(false)}
    val medForm=MedForm.valueOf(form)
    val rows=unpackSubstances(substances)
    val result=runCatching{
        Medication(existing?.id?:"med:"+UUID.randomUUID(),name.trim(),MedKind.valueOf(kind),medForm,
            rows.filter{it.first.isNotBlank()}.map{(n,a,u)->ActiveSubstance(n.trim(),a.takeIf{it.isNotBlank()}?.let(::parseQuantity),u)},
            parseQuantity(dose),perDay.trim().toIntOrNull()?:throw IllegalArgumentException(tr("Сколько раз в день — целое число","Doses per day must be a whole number")),
            times.split(',',' ',';').map{it.trim()}.filter{it.isNotEmpty()}.map{t->if(t.length==4&&t[1]==':')"0$t" else t},
            FoodTiming.valueOf(timing),start.ifBlank{null},end.ifBlank{null},purpose.trim(),doctor.trim(),notes.trim(),existing?.archived?:false).also(::validateMedication)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
        SectionTitle(if(existing==null)tr("Новый препарат","New medicine or vitamin") else tr("Карта препарата","Medicine card"),tr("Перепишите данные с упаковки или из назначения врача.","Copy the details from the package or the prescription."))
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){MedKind.entries.forEach{k->FilterChip(selected=kind==k.name,onClick={kind=k.name},label={Text(medKindLabel(k))})}}
        OutlinedTextField(name,{name=it.take(200)},label={Text(tr("Название препарата","Name"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("medName"))
        Text(tr("Форма выпуска","Form"),style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){MedForm.entries.forEach{f->FilterChip(selected=form==f.name,onClick={form=f.name},label={Text(formLabel(f))})}}
        Text(tr("Действующие вещества","Active substances"),style=MaterialTheme.typography.titleSmall)
        Text(tr("Сколько вещества в одной единице формы — например, витамин D3 2000 МЕ в капсуле.","How much is in one unit of the form — e.g. vitamin D3 2000 IU per capsule."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEachIndexed{i,(n,a,u)->
            fun update(row:Triple<String,String,StrengthUnit>){substances=packSubstances(rows.toMutableList().also{it[i]=row})}
            Panel {
                Row(verticalAlignment=Alignment.CenterVertically){
                    OutlinedTextField(n,{update(Triple(it.take(200),a,u))},label={Text(tr("Вещество","Substance"))},singleLine=true,modifier=Modifier.weight(1f))
                    IconButton(onClick={substances=packSubstances(rows.filterIndexed{j,_->j!=i})}){Icon(Icons.Outlined.DeleteOutline,tr("Убрать вещество","Remove the substance"))}
                }
                OutlinedTextField(a,{update(Triple(n,it.take(12),u))},label={Text(tr("Количество (необязательно)","Amount (optional)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){StrengthUnit.entries.forEach{s->FilterChip(selected=u==s,onClick={update(Triple(n,a,s))},label={Text(strengthLabel(s))})}}
            }
        }
        if(rows.size<10)TextButton(onClick={substances=packSubstances(rows+Triple("","",StrengthUnit.MG))}){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text(tr("Добавить вещество","Add a substance"))}
        Text(tr("Как принимать","How to take"),style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(dose,{dose=it.take(8)},label={Text(tr("За раз","Per dose")+", ${doseUnit(medForm)}")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f).testTag("medDose"))
            OutlinedTextField(perDay,{perDay=it.filter(Char::isDigit).take(2)},label={Text(tr("Раз в день","Times a day"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f).testTag("medPerDay"))
        }
        OutlinedTextField(times,{times=it.take(120)},label={Text(tr("Время приёма (необязательно), напр. 08:00, 20:00","Dose times (optional), e.g. 08:00, 20:00"))},singleLine=true,modifier=Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){FoodTiming.entries.forEach{t->FilterChip(selected=timing==t.name,onClick={timing=t.name},label={Text(timingLabel(t))})}}
        Text(tr("Курс","Course"),style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            OutlinedButton(onClick={picking="start"},modifier=Modifier.weight(1f)){Text(tr("С ","From ")+dayMonth(start,true))}
            OutlinedButton(onClick={picking="end"},modifier=Modifier.weight(1f)){Text(if(end.isBlank())tr("Без срока","No end date") else tr("По ","Until ")+dayMonth(end,true))}
            if(end.isNotBlank())IconButton(onClick={end=""}){Icon(Icons.Outlined.Close,tr("Без срока","No end date"))}
        }
        OutlinedTextField(purpose,{purpose=it.take(500)},label={Text(tr("Для чего (необязательно)","What for (optional)"))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(doctor,{doctor=it.take(200)},label={Text(tr("Кто назначил (необязательно)","Prescribed by (optional)"))},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(notes,{notes=it.take(2000)},label={Text(tr("Заметки: противопоказания, совместимость, хранение","Notes: warnings, interactions, storage"))},modifier=Modifier.fillMaxWidth().heightIn(min=96.dp))
        result.exceptionOrNull()?.let{if(name.isNotBlank())Text(it.message?:tr("Проверьте поля","Check the fields"),color=MaterialTheme.colorScheme.error)}
        Button(onClick={vm.saveMedication(result.getOrThrow(),back)},enabled=result.isSuccess&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("saveMedication")){Text(if(existing==null)tr("Сохранить препарат","Save") else tr("Сохранить изменения","Save changes"))}
        if(existing!=null)OutlinedButton(onClick={confirmDelete=true},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text(tr("Удалить препарат","Delete"))}
        Text(tr("Приложение не проверяет дозировки и взаимодействия. Следуйте назначению врача и инструкции.","The app does not check doses or interactions. Follow your doctor's advice and the leaflet."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    picking?.let{field->
        val initial=(if(field=="start")start else end.ifBlank{start}).let{LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}
        val state=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(onDismissRequest={picking=null},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{val d=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString();if(field=="start")start=d else end=d};picking=null}){Text(tr("Выбрать","Select"))}},dismissButton={TextButton(onClick={picking=null}){Text(tr("Отмена","Cancel"))}}){DatePicker(state)}
    }
    if(confirmDelete&&existing!=null)AlertDialog(onDismissRequest={confirmDelete=false},title={Text(tr("Удалить «${existing.name}»?","Delete “${existing.name}”?"))},
        text={Text(tr("Карта и отметки её приёма исчезнут из дневника. Удаление можно отменить сразу после него.","The card and its dose marks leave the diary. You can undo right after deleting."))},
        confirmButton={TextButton(onClick={confirmDelete=false;vm.deleteMedication(existing);back()}){Text(tr("Удалить","Delete"))}},dismissButton={TextButton(onClick={confirmDelete=false}){Text(tr("Отмена","Cancel"))}})
}
