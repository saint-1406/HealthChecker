package az.healthchecker.ui.dashboard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import kotlinx.coroutines.delay

/**
 * Protein: today against the goal, the last 7 or 30 days, the foods that gave the most, and a list of foods by protein
 * per 100 g that can be filtered by an amount and sorted both ways. Tapping a food opens it for the diary.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProteinScreen(vm:AppViewModel,snapshot:UserSnapshot,date:String,back:()->Unit,eat:(Food)->Unit) {
    var period by rememberSaveable{mutableIntStateOf(7)}
    var query by rememberSaveable{mutableStateOf("")}
    var gramsText by rememberSaveable{mutableStateOf("")}
    var matchName by rememberSaveable{mutableStateOf(ProteinMatch.AT_LEAST.name)}
    var sortName by rememberSaveable{mutableStateOf(ProteinSort.HIGH_FIRST.name)}
    var whole by rememberSaveable{mutableStateOf(false)}
    val match=ProteinMatch.valueOf(matchName);val sort=ProteinSort.valueOf(sortName)
    val grams=gramsText.replace(',','.').trim().toDoubleOrNull()?.takeIf{it>=0&&it<=100}
    var results by remember{mutableStateOf<List<Food>?>(null)}
    LaunchedEffect(query,grams,match,sort,whole,snapshot.customFoods){delay(150);results=vm.findProteinFoods(query,grams,match,sort,whole)}
    val stats=remember(snapshot,date,period){proteinStats(snapshot,date,period,vm::entryName)}
    val today=stats.days.last()
    val weight=snapshot.bodyWeight(date)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start=8.dp,end=16.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            Column{Text(tr("Белок","Protein"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(dayMonth(date,true),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("proteinList"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{Panel {
                Text(tr("СЕГОДНЯ","TODAY"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Row(verticalAlignment=Alignment.Bottom){
                    Text("${number(today.grams.value)}${if(!today.grams.complete)"+" else ""} $G",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("proteinToday"))
                    Text(today.goal?.let{"  ${tr("из","of")} ${number(it)} $G"}?:"  "+tr("цель не задана","no goal set"),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=6.dp))
                }
                today.goal?.let{goal->
                    LinearProgressIndicator(progress={(today.grams.value/goal).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth())
                    Text(if(today.grams.value>=goal)tr("Цель по белку выполнена","The protein goal is met") else tr("Осталось ","Left: ")+"${number(goal-today.grams.value)} $G",style=MaterialTheme.typography.bodyMedium)
                }
                Text(listOfNotNull(today.energyShare?.let{tr("Белок даёт ","Protein gives ")+"${number(it*100)}% "+tr("калорий дня","of the day's calories")},
                    weight?.takeIf{today.grams.value>0}?.let{"${if(today.grams.complete)"" else "≥ "}${number(today.grams.value/it,1)} $G "+tr("на кг веса","per kg of body weight")}).joinToString(" · ").ifEmpty{if(today.grams.complete&&today.grams.value==0.0)tr("Сегодня белка ещё нет в дневнике","No protein in the diary today yet") else tr("Недостаточно данных для расчёта доли калорий","Not enough data to calculate the calorie share")},
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
            item{Panel {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(tr("По дням","By day"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                    listOf(7,30).forEach{d->FilterChip(selected=period==d,onClick={period=d},label={Text("$d "+tr("дн.","d"))},modifier=Modifier.padding(start=6.dp).testTag("proteinPeriod$d"))}
                }
                ProteinChart(stats.days)
                Row{Text(shortDate(stats.days.first().date),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f));Text(shortDate(stats.days.last().date),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text(stats.average?.let{tr("В среднем ","Average ")+"${number(it)} $G "+tr("в день","a day")}?:tr("Пока нет дней с полными данными","No days with complete data yet"),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("proteinAverage"))
                if(stats.goalDays>0)Text(tr("Цель выполнена: ","Goal met: ")+"${stats.goalMet} "+tr("из","of")+" ${stats.goalDays} "+tr("дн. с достаточными данными","days with sufficient data"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                stats.perKg?.let{Text("${number(it,1)} $G "+tr("на кг веса в среднем. Для взрослых обычно советуют от 0,8 г/кг, при силовых тренировках — 1,2–2 г/кг.","per kg of body weight on average. Adults are usually advised 0.8 g/kg or more, 1.2–2 g/kg with strength training."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }}
            if(stats.sources.isNotEmpty())item{Panel {
                Text(tr("Главные источники за ","Top sources over ")+"$period "+tr("дн.","days"),style=MaterialTheme.typography.titleMedium)
                val total=stats.sources.sumOf{it.grams}.coerceAtLeast(1.0)
                stats.sources.forEach{s->
                    Column(Modifier.fillMaxWidth()){
                        Row{Text(s.name,modifier=Modifier.weight(1f),maxLines=1);Text("${number(s.grams)} $G",fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)}
                        LinearProgressIndicator(progress={(s.grams/total).toFloat()},modifier=Modifier.fillMaxWidth().padding(top=2.dp),drawStopIndicator={})
                    }
                }
            }}
            item{SectionTitle(tr("Продукты с белком","Foods with protein"),tr("На 100 г; нажмите, чтобы записать в дневник","Per 100 g; tap to log it"))}
            item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query,{query=it.take(60)},label={Text(tr("Название","Name"))},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("proteinQuery"))
                OutlinedTextField(gramsText,{gramsText=it.filter{c->c.isDigit()||c==','||c=='.'}.take(5)},label={Text(tr("Белка на 100 г, г","Protein per 100 g, g"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    isError=gramsText.isNotBlank()&&grams==null,modifier=Modifier.fillMaxWidth().testTag("proteinGrams"))
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf(ProteinMatch.AT_LEAST to tr("Не меньше","At least"),ProteinMatch.ABOUT to tr("Около","About"),ProteinMatch.AT_MOST to tr("Не больше","At most")).forEach{(m,label)->
                        FilterChip(selected=match==m,onClick={matchName=m.name},enabled=grams!=null,label={Text(label)})}
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FilterChip(selected=sort==ProteinSort.HIGH_FIRST,onClick={sortName=ProteinSort.HIGH_FIRST.name},label={Text(tr("Больше → меньше","High → low"))},leadingIcon={Icon(Icons.Outlined.ArrowDownward,null)},modifier=Modifier.testTag("sortHigh"))
                    FilterChip(selected=sort==ProteinSort.LOW_FIRST,onClick={sortName=ProteinSort.LOW_FIRST.name},label={Text(tr("Меньше → больше","Low → high"))},leadingIcon={Icon(Icons.Outlined.ArrowUpward,null)},modifier=Modifier.testTag("sortLow"))
                    FilterChip(selected=sort==ProteinSort.PER_KCAL,onClick={sortName=ProteinSort.PER_KCAL.name},label={Text(tr("Больше на 100 ккал","Most per 100 kcal"))})
                }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FilterChip(selected=!whole,onClick={whole=false},label={Text(tr("Частые продукты","Everyday foods"))})
                    FilterChip(selected=whole,onClick={whole=true},label={Text(tr("Весь каталог","Whole catalog"))},modifier=Modifier.testTag("wholeCatalog"))
                }
                val found=results
                Text(when{found==null->tr("Ищем…","Searching…");found.size>LIST_LIMIT->tr("Найдено ","Found ")+"${found.size}, "+tr("показаны первые ","showing the first ")+LIST_LIMIT;else->tr("Найдено: ","Found: ")+found.size},
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("proteinFound"))
                if(found!=null&&found.isEmpty()&&!whole)TextButton(onClick={whole=true}){Text(tr("Искать во всём каталоге","Search the whole catalog"))}
            }}
            items(results.orEmpty().take(LIST_LIMIT),key={it.id}){f->ProteinFoodRow(f){eat(f)}}
        }
    }
}
private const val LIST_LIMIT=200

@Composable private fun ProteinFoodRow(food:Food,open:()->Unit) {
    Surface(onClick=open,shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("proteinFood")) {
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Column(Modifier.weight(1f)){
                Text(food.displayName,fontWeight=FontWeight.SemiBold,maxLines=2)
                Text(listOfNotNull(food.per100g.kcal?.let{"${number(it)} $KCAL"},food.proteinPer100Kcal()?.let{"${number(it,1)} $G "+tr("на 100 ккал","per 100 kcal")}).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                food.portions.firstOrNull()?.let{p->food.per100g.protein?.let{pr->Text("${p.displayName} (${number(p.grams)} $G): ${number(pr*p.grams/100,1)} $G "+tr("белка","protein"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            }
            Text("${number(food.per100g.protein,1)} $G",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
        }
    }
}

/** Bars of protein by day with the goal as a dashed line; days without entries show a stub. */
@Composable private fun ProteinChart(days:List<ProteinDay>) {
    val bar=MaterialTheme.colorScheme.primary;val met=MaterialTheme.colorScheme.tertiary;val empty=MaterialTheme.colorScheme.surfaceVariant;val line=MaterialTheme.colorScheme.outline
    val peak=(days.maxOf{maxOf(it.grams.value,it.goal?:0.0)}).coerceAtLeast(1.0)
    val summary=days.joinToString("; "){d->"${shortDate(d.date)}: "+if(d.logged)"${number(d.grams.value)} $G" else tr("нет данных","no data")}
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics{contentDescription=summary}) {
        val step=size.width/days.size;val w=(step*0.62f).coerceAtMost(28.dp.toPx())
        fun y(v:Double)=size.height-(v/peak*(size.height-6.dp.toPx())).toFloat()
        days.forEachIndexed{i,d->
            val x=i*step+(step-w)/2
            if(d.logged&&d.grams.value>0){val top=y(d.grams.value);drawRoundRect(if(d.goal!=null&&d.grams.value>=d.goal)met else bar,Offset(x,top),Size(w,size.height-top),CornerRadius(w/3))}
            else drawRoundRect(empty,Offset(x,size.height-4.dp.toPx()),Size(w,4.dp.toPx()),CornerRadius(w/3))
        }
        // The goal can change over the period, so it is drawn per day.
        days.forEachIndexed{i,d->d.goal?.let{g->drawLine(line,Offset(i*step,y(g)),Offset((i+1)*step,y(g)),1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8f,6f)))}}
    }
}
