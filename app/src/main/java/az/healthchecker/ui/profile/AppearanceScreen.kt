package az.healthchecker.ui.profile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import az.healthchecker.ui.*
import az.healthchecker.ui.theme.colorSchemeFor
import az.healthchecker.ui.theme.isDark
import java.time.LocalDate

fun paletteName(id:String):String = when(id){
    "custom"->tr("Свой цвет","Own colour");"halloween"->tr("Хэллоуин","Halloween");"newyear"->tr("Новый год","New Year")
    else->PALETTES.find{it.id==id}?.let{tr(it.ru,it.en)}?:id
}

/**
 * Appearance: light or dark, the interface colour (presets or any hue), holiday themes that switch on by themselves,
 * holiday decorations and an own background picture with how strongly it is veiled and how solid the cards are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun AppearanceScreen(vm:AppViewModel,back:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val a=settings.appearance
    val today=remember{LocalDate.now()}
    val season=seasonOn(today);val active=a.activeSeason(today);val shown=a.effectivePalette(today)
    val dark=isDark(a.mode,isSystemInDarkTheme())
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let(vm::setBackground)}
    fun choose(id:String)=vm.appearance{it.choose(id,today)}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start=8.dp,end=16.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=back){Icon(Icons.AutoMirrored.Outlined.ArrowBack,tr("Назад","Back"))}
            Column{Text(tr("Оформление","Appearance"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(tr("Сейчас: ","Now: ")+paletteName(shown),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("appearanceNow"))}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).testTag("appearanceList"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Preview()
            Panel {
                Text(tr("Режим","Mode"),style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf(Triple("system",tr("Как в системе","As the system"),Icons.Outlined.PhoneAndroid),Triple("light",tr("Светлый","Light"),Icons.Outlined.LightMode),Triple("dark",tr("Тёмный","Dark"),Icons.Outlined.DarkMode)).forEach{(value,label,icon)->
                        FilterChip(selected=a.mode==value,onClick={vm.appearance{it.copy(mode=value)}},label={Text(label)},leadingIcon={Icon(icon,null)},modifier=Modifier.testTag("mode-$value"))}
                }
            }
            Panel {
                Text(tr("Цвет интерфейса","Interface colour"),style=MaterialTheme.typography.titleMedium)
                Text(tr("Кнопки, переключатели, графики и фон экранов подбираются под выбранный цвет с проверкой контраста текста.","Buttons, switches, charts and screen backgrounds follow the chosen colour, with text contrast checked."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    (PALETTES.map{it.id}+"custom").forEach{id->Swatch(id,a.customHue,dark,isSelected=active==null&&a.palette==id){choose(id)}}
                }
                if(a.palette=="custom"&&active==null){
                    var hue by remember(a.customHue){mutableFloatStateOf(a.customHue)}
                    Text(tr("Оттенок","Hue"),style=MaterialTheme.typography.titleSmall)
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Brush.horizontalGradient((0..12).map{Color(hsl(it*30f,0.6f,0.45f))})))
                    Slider(hue,{hue=it},valueRange=0f..359f,onValueChangeFinished={vm.appearance{it.copy(customHue=hue)}},modifier=Modifier.testTag("customHue"))
                }
            }
            Panel {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.Celebration,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){Text(tr("Праздничные темы","Holiday themes"),style=MaterialTheme.typography.titleMedium);Text(tr("Сами включаются за неделю до праздника и держатся неделю после","Switch on a week before the holiday and stay a week after"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(a.seasonal,{on->vm.appearance{it.copy(seasonal=on,seasonSkipped="")}},modifier=Modifier.testTag("seasonalSwitch"))
                }
                Text(tr("Хэллоуин — с 25 октября по 7 ноября · Новый год — с 26 декабря по 8 января","Halloween — 25 October to 7 November · New Year — 26 December to 8 January"),style=MaterialTheme.typography.bodySmall)
                Text(when{
                    active!=null->tr("Сейчас включена тема «","The “")+paletteName(active.palette)+tr("» — до ","” theme is on until ")+dayMonth(seasonEnd(active,today).toString())+tr(". Выберите другой цвет выше, чтобы выключить её до следующего праздника.",". Choose another colour above to switch it off until the next holiday.")
                    season!=null&&a.seasonal->tr("Тема «","The “")+paletteName(season.palette)+tr("» выключена до следующего праздника.","” theme is off until the next holiday.")
                    else->tr("Попробовать праздничную тему можно в любой момент:","Try a holiday theme any time:")
                },style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("seasonStatus"))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    HolidayCard("halloween",Icons.Outlined.NightsStay,dark,shown=="halloween",Modifier.weight(1f)){choose("halloween")}
                    HolidayCard("newyear",Icons.Outlined.AcUnit,dark,shown=="newyear",Modifier.weight(1f)){choose("newyear")}
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(tr("Украшения и анимация","Decorations and animation"),style=MaterialTheme.typography.titleSmall);Text(tr("Снег и гирлянда на Новый год, луна, летучие мыши и тыквы на Хэллоуин","Snow and lights for New Year; moon, bats and pumpkins for Halloween"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(a.decorations,{on->vm.appearance{it.copy(decorations=on)}},modifier=Modifier.testTag("decorationsSwitch"))
                }
            }
            Panel {
                Text(tr("Фон","Background"),style=MaterialTheme.typography.titleMedium)
                if(a.hasBackground){
                    rememberPicture(vm.backgroundFile(),a.background,240.dp)?.let{Image(it,tr("Ваш фон","Your background"),Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp)),contentScale=ContentScale.Crop)}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!busy){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text(tr("Заменить","Replace"))}
                        TextButton(onClick=vm::removeBackground,enabled=!busy,modifier=Modifier.testTag("removeBackground")){Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(6.dp));Text(tr("Убрать фон","Remove"))}
                    }
                    var dim by remember(a.dim){mutableFloatStateOf(a.dim)}
                    Text(tr("Приглушить картинку: ","Soften the picture: ")+"${(dim*100).toInt()}%",style=MaterialTheme.typography.titleSmall)
                    Slider(dim,{dim=it},valueRange=0.1f..0.9f,onValueChangeFinished={vm.appearance{it.copy(dim=dim)}},modifier=Modifier.testTag("backgroundDim"))
                    var cards by remember(a.cardAlpha){mutableFloatStateOf(a.cardAlpha)}
                    Text(tr("Плотность карточек: ","Card opacity: ")+"${(cards*100).toInt()}%",style=MaterialTheme.typography.titleSmall)
                    Slider(cards,{cards=it},valueRange=0.6f..1f,onValueChangeFinished={vm.appearance{it.copy(cardAlpha=cards)}},modifier=Modifier.testTag("cardAlpha"))
                    Text(tr("Чем сильнее приглушена картинка и плотнее карточки, тем легче читать текст.","The softer the picture and the more solid the cards, the easier the text is to read."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(tr("Поставьте за всеми экранами свою картинку из галереи: фото, пейзаж, что угодно. Она хранится только в приложении.","Put your own picture from the gallery behind every screen. It is kept only in the app."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("pickBackground")){Icon(Icons.Outlined.Wallpaper,null);Spacer(Modifier.width(8.dp));Text(tr("Выбрать картинку","Choose a picture"))}
                }
            }
            TextButton(onClick={if(a.hasBackground)vm.removeBackground();vm.appearance{Appearance(mode=it.mode)}},modifier=Modifier.fillMaxWidth().testTag("resetAppearance")){Icon(Icons.Outlined.RestartAlt,null);Spacer(Modifier.width(6.dp));Text(tr("Вернуть стандартное оформление","Restore the default look"))}
            Text(tr("Оформление хранится на этом телефоне и в резервную копию не входит.","The look is kept on this phone and is not part of the backup."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A little sample of the current look, so a change is seen at once. */
@Composable private fun Preview() {
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(tr("ЭНЕРГИЯ ЗА ДЕНЬ","ENERGY FOR THE DAY"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text("1 450 "+KCAL,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)}
            Icon(Icons.Outlined.Restaurant,null,tint=MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(progress={0.68f},modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Button(onClick={}){Text(tr("Записать","Log"))}
            FilledTonalButton(onClick={}){Text("+250 "+ML)}
            FilterChip(selected=true,onClick={},label={Text(tr("Выбрано","Selected"))})
        }
    }
}

@Composable private fun Swatch(id:String,customHue:Float,dark:Boolean,isSelected:Boolean,onClick:()->Unit) {
    val scheme=colorSchemeFor(id,customHue,dark)
    Column(Modifier.width(72.dp).clickable(onClick=onClick).semantics{role=Role.RadioButton;selected=isSelected;contentDescription=paletteName(id)}.testTag("palette-$id"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(if(id=="custom")Brush.sweepGradient((0..6).map{Color(hsl(it*60f,0.6f,0.5f))}) else Brush.linearGradient(listOf(scheme.primary,scheme.primary,scheme.tertiary)))
            .border(BorderStroke(if(isSelected)3.dp else 1.dp,if(isSelected)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),CircleShape),contentAlignment=Alignment.Center){
            if(isSelected)Icon(Icons.Outlined.Check,null,tint=if(id=="custom")Color.White else scheme.onPrimary)
        }
        Text(paletteName(id),style=MaterialTheme.typography.labelSmall,maxLines=1)
    }
}

@Composable private fun HolidayCard(id:String,icon:androidx.compose.ui.graphics.vector.ImageVector,dark:Boolean,selected:Boolean,modifier:Modifier,onClick:()->Unit) {
    val scheme=colorSchemeFor(id,0f,dark)
    Surface(onClick=onClick,shape=RoundedCornerShape(18.dp),color=scheme.background,border=BorderStroke(if(selected)3.dp else 1.dp,if(selected)scheme.primary else scheme.outlineVariant),modifier=modifier.testTag("holiday-$id")) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Box(Modifier.size(34.dp).clip(CircleShape).background(scheme.primary),contentAlignment=Alignment.Center){Icon(icon,null,tint=scheme.onPrimary,modifier=Modifier.size(20.dp))}
            Text(paletteName(id),color=scheme.onBackground,fontWeight=FontWeight.SemiBold)
        }
    }
}
