package az.healthchecker.ui
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.healthchecker.ui.theme.pulseColor
import az.healthchecker.domain.*
import java.time.LocalDate

/*
 * Arcade's frame around every main screen: a header with the pixel mark, the section and a square date box, and a tab
 * strip of square cells at the bottom. The same tabs, routes and date as the standard layout.
 */

/** A small picture drawn from rows of characters, one square per character; '.' is empty. */
@Composable fun PixelArt(rows:List<String>,colors:Map<Char,Color>,modifier:Modifier=Modifier) {
    val w=rows.maxOf{it.length};val h=rows.size
    Canvas(modifier) {
        val px=minOf(size.width/w,size.height/h);val ox=(size.width-px*w)/2;val oy=(size.height-px*h)/2
        rows.forEachIndexed{y,row->row.forEachIndexed{x,ch->colors[ch]?.let{drawRect(it,Offset(ox+x*px,oy+y*px),Size(px+0.5f,px+0.5f))}}}
    }
}

/** The VitaCore mark in Arcade: a pixel heart. */
val PIXEL_HEART=listOf(".DD.DD.","DDDDDDD","DDDDDDD",".DDDDD.","..DDD..","...D...")

/**
 * The hero block's athlete: dumbbells up after a workout. Drawn for this app; D is the figure, M its shadow tone,
 * P the weight plates, W the eyes.
 */
val PIXEL_ATHLETE=listOf(
    ".....DDDDDD.....",
    "....DDDDDDDD....",
    "....DWWDDWWD....",
    "....DDDDDDDD....",
    ".....DDMMDD.....",
    "PP....DMMD....PP",
    "PPMMDDDDDDDDMMPP",
    "PP..DDDDDDDD..PP",
    "....DDDMMDDD....",
    ".....DDDDDD.....",
    ".....DD..DD.....",
    "....DDD..DDD....",
    "....DD....DD....",
    "...DDD....DDD...",
    "..MMMMMMMMMMMM..")

/** The day box: "Сегодня, 27 нояб. ▾" opens a menu of nearby days and the calendar. */
@Composable fun ArcadeDateBox(date:String,choose:(String)->Unit,openCalendar:()->Unit) {
    var open by remember{mutableStateOf(false)}
    val today=LocalDate.now();val d=LocalDate.parse(date)
    val label=when(d){today->tr("Сегодня, ","Today, ")+shortDate(date);today.minusDays(1)->tr("Вчера, ","Yesterday, ")+shortDate(date);else->shortDayMonth(date)}
    Box {
        Row(Modifier.border(1.dp,MaterialTheme.colorScheme.outline).clickable{open=true}.heightIn(min=40.dp).padding(start=10.dp,end=4.dp)
            .semantics{contentDescription=tr("Дата: ","Date: ")+dayMonth(date,true);role=Role.DropdownList}.testTag("arcadeDate"),verticalAlignment=Alignment.CenterVertically) {
            Text(label,style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=180.dp))
            Icon(Icons.Outlined.ArrowDropDown,null)
        }
        DropdownMenu(open,{open=false},shape=RectangleShape) {
            val items=buildList{
                add(tr("Сегодня","Today") to today)
                add(tr("Вчера","Yesterday") to today.minusDays(1))
                add(tr("Предыдущий день","Previous day") to d.minusDays(1))
                if(d<today)add(tr("Следующий день","Next day") to d.plusDays(1))
            }
            items.distinctBy{it.second}.forEach{(name,day)->DropdownMenuItem(text={Text(name+" · "+shortDate(day.toString()),style=MaterialTheme.typography.bodyMedium)},onClick={open=false;choose(day.toString())},
                modifier=Modifier.testTag("arcadeDay-"+day))}
            HorizontalDivider()
            DropdownMenuItem(text={Text(tr("Выбрать в календаре…","Pick in the calendar…"),style=MaterialTheme.typography.bodyMedium)},onClick={open=false;openCalendar()},modifier=Modifier.testTag("arcadeCalendar"))
        }
    }
}

/** Header: the pixel mark with VITACORE and the section, the date box on the right (hidden for tabs without a date). */
@Composable fun ArcadeTopBar(section:String?,date:String?,choose:(String)->Unit,openCalendar:()->Unit) {
    Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal=16.dp).padding(top=6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            PixelArt(PIXEL_HEART,mapOf('D' to MaterialTheme.colorScheme.primary),Modifier.size(18.dp,16.dp))
            Text("VITACORE"+(section?.let{" / "+it.uppercase(Lang.locale)}?:""),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,
                modifier=Modifier.weight(1f).testTag("screenTitle"))
            if(date!=null)ArcadeDateBox(date,choose,openCalendar)
        }
        HorizontalDivider(Modifier.padding(top=6.dp),color=MaterialTheme.colorScheme.outlineVariant)
    }
}

data class ArcadeTab(val id:Int,val label:String,val icon:ImageVector)

/**
 * The tab strip: square cells under a hairline, the open one marked by a bar on top and the accent colour. When there
 * is little room per line only the open tab keeps its label.
 */
@Composable fun ArcadeNavBar(tabs:List<ArcadeTab>,current:Int,tight:Boolean,open:(Int)->Unit) {
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding()) {
        HorizontalDivider(color=c.outlineVariant)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            tabs.forEach{t->
                val on=t.id==current
                Column(Modifier.weight(1f).fillMaxHeight().clickable{open(t.id)}.semantics{role=Role.Tab;selected=on;contentDescription=t.label}.testTag("arcadeTab-${t.id}")
                    .heightIn(min=56.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if(on)c.primary else Color.Transparent))
                    Spacer(Modifier.height(8.dp))
                    Icon(t.icon,null,tint=if(on)c.primary else c.onSurfaceVariant,modifier=Modifier.size(22.dp))
                    if(on||!tight)Text(t.label.uppercase(Lang.locale),style=MaterialTheme.typography.labelSmall,color=if(on)c.primary else c.onSurfaceVariant,maxLines=1,softWrap=false,
                        overflow=TextOverflow.Clip,modifier=Modifier.padding(top=4.dp,bottom=8.dp))
                    else Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** An Arcade notice: a straight frame with the accent edge, the text and its action. */
@Composable fun ArcadeSnackbar(text:String,undo:(()->Unit)?,close:()->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(Modifier.padding(16.dp).fillMaxWidth(),shape=RectangleShape,color=c.surfaceContainerHighest,contentColor=c.onSurface,border=BorderStroke(1.dp,c.primary)) {
        Row(Modifier.height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(c.primary))
            Text(text,style=MaterialTheme.typography.bodyMedium,maxLines=3,modifier=Modifier.weight(1f).padding(horizontal=12.dp,vertical=14.dp).testTag("noticeText"))
            if(undo!=null)androidx.compose.material3.TextButton(onClick=undo,shape=RectangleShape,colors=ButtonDefaults.textButtonColors(contentColor=c.primary),modifier=Modifier.testTag("undoNotice")){Text(tr("Отменить","Undo").uppercase(Lang.locale),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)}
            IconButton(onClick=close){Icon(Icons.Outlined.Close,tr("Закрыть","Close"))}
        }
    }
}
