package az.healthchecker.ui.training
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import az.healthchecker.domain.*
import az.healthchecker.ui.KCAL
import az.healthchecker.ui.number
import kotlinx.serialization.json.Json

/** Keeps the items being checked through a rotation; null — the editor is closed. */
val VideoLogSaver:Saver<List<VideoLogItem>?,String> = Saver(save={it?.let{list->Json.encodeToString(list)}},restore={Json.decodeFromString<List<VideoLogItem>>(it)})

/**
 * The step before a video goes to the diary: every exercise of it, checked as done, with its sets and reps or seconds the
 * user can change. Only the checked ones are logged, with these amounts; cancel logs nothing.
 */
@Composable fun VideoLogEditor(items:List<VideoLogItem>,change:(List<VideoLogItem>)->Unit,name:(String)->String,met:(String)->Double?,bodyWeightKg:Double?,
    thumb:@Composable (String)->Unit,close:()->Unit,confirm:(List<VideoLogItem>)->Unit) {
    fun update(i:Int,item:VideoLogItem)=change(items.toMutableList().also{it[i]=item})
    val totals=videoLogTotals(items,met,bodyWeightKg)
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize().testTag("videoLogEditor"),color=MaterialTheme.colorScheme.surface){
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()){
                Column(Modifier.padding(start=16.dp,end=16.dp,top=16.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(tr("Что вы сделали?","What did you do?"),style=MaterialTheme.typography.titleLarge)
                    Text(tr("Снимите отметку с пропущенного и поправьте подходы, повторения и время, если сделали больше или меньше.",
                        "Uncheck what you skipped and change the sets, reps and time if you did more or less."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(listOfNotNull(count(totals.count,"упражнение","упражнения","упражнений","exercise","exercises"),tr("работа ","work ")+formatVideoTime(totals.seconds),totals.kcal?.let{"≈ ${number(it)} $KCAL"}).joinToString(" · "),
                        style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("videoLogTotals"))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        TextButton(onClick={change(items.map{it.copy(done=true)})},modifier=Modifier.testTag("videoLogAll")){Text(tr("Отметить все","Check all"))}
                        TextButton(onClick={change(items.map{it.copy(done=false)})},modifier=Modifier.testTag("videoLogNone")){Text(tr("Снять все","Uncheck all"))}
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f).testTag("videoLogList"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    itemsIndexed(items){i,item->VideoLogRow(i,item,name(item.exerciseId),thumb){update(i,it)}}
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End)){
                    TextButton(onClick=close,modifier=Modifier.heightIn(min=48.dp).testTag("cancelVideoLog")){Text(tr("Отмена","Cancel"))}
                    Button(onClick={confirm(items.filter{it.done})},enabled=totals.count>0,modifier=Modifier.heightIn(min=48.dp).testTag("saveVideoLog")){
                        Text(tr("Записать","Log")+if(totals.count>0)" (${totals.count})" else "")
                    }
                }
            }
        }
    }
}

@Composable private fun VideoLogRow(index:Int,item:VideoLogItem,name:String,thumb:@Composable (String)->Unit,update:(VideoLogItem)->Unit) {
    Surface(shape=RoundedCornerShape(16.dp),color=if(item.done)MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        tonalElevation=if(item.done)1.dp else 0.dp,modifier=Modifier.fillMaxWidth().testTag("videoLogItem_$index")){
        Column(Modifier.padding(horizontal=4.dp,vertical=6.dp)){
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{update(item.copy(done=!item.done))},verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=item.done,onCheckedChange={update(item.copy(done=it))},modifier=Modifier.testTag("videoLogDone_$index"))
                thumb(item.exerciseId)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)){
                    Text(name,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,
                        color=if(item.done)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(item.done)tr("работа ","work ")+formatVideoTime(item.workSeconds) else tr("Пропущено","Skipped"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(item.done)Column(Modifier.padding(start=12.dp,end=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                if(item.hasSets)Stepper(tr("Подходы","Sets"),item.sets,SETS_RANGE,1,"videoLogSets_$index"){update(item.withSets(it))}
                val label=when(item.measure){ExerciseMeasure.REPS->tr("Повторения в подходе","Reps per set");ExerciseMeasure.SECONDS->tr("Секунды в подходе","Seconds per set");ExerciseMeasure.MINUTES->tr("Время, с","Time, s")}
                Stepper(label,item.amount,item.amountRange,item.amountStep,"videoLogAmount_$index"){update(item.withAmount(it))}
            }
        }
    }
}

/** A number with − and + buttons (48 dp each) and a field to type it straight in; a value out of [range] is not taken. */
@Composable private fun Stepper(label:String,value:Int,range:IntRange,step:Int,tag:String,set:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(label,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
        FilledTonalIconButton(onClick={set((value-step).coerceAtLeast(range.first))},enabled=value>range.first,modifier=Modifier.size(48.dp).testTag("${tag}_minus")){
            Icon(Icons.Outlined.Remove,tr("Меньше: ","Less: ")+label)
        }
        var text by remember(value){mutableStateOf(value.toString())}
        val typed=text.toIntOrNull()
        OutlinedTextField(text,{t->val digits=t.filter(Char::isDigit).take(5);text=digits;digits.toIntOrNull()?.takeIf{it in range}?.let(set)},
            singleLine=true,isError=typed==null||typed !in range,textStyle=MaterialTheme.typography.bodyLarge.copy(textAlign=TextAlign.Center),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
            modifier=Modifier.padding(horizontal=6.dp).width(84.dp).semantics{contentDescription=label}.testTag("${tag}_field"))
        FilledTonalIconButton(onClick={set((value+step).coerceAtMost(range.last))},enabled=value<range.last,modifier=Modifier.size(48.dp).testTag("${tag}_plus")){
            Icon(Icons.Outlined.Add,tr("Больше: ","More: ")+label)
        }
    }
}
