package az.healthchecker.domain
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow

/** Holiday looks that switch on by themselves around the holiday. */
enum class Season(val palette:String) { HALLOWEEN("halloween"), NEW_YEAR("newyear") }

/**
 * Halloween (31 October) and New Year (1 January) run from six days before the holiday — when fewer than seven days are
 * left — to seven days after it: 25 October – 7 November and 26 December – 8 January.
 */
fun seasonOn(date:LocalDate):Season? {
    fun around(holiday:LocalDate)=!date.isBefore(holiday.minusDays(6))&&!date.isAfter(holiday.plusDays(7))
    if(around(LocalDate.of(date.year,10,31)))return Season.HALLOWEEN
    if(around(LocalDate.of(if(date.monthValue==12)date.year+1 else date.year,1,1)))return Season.NEW_YEAR
    return null
}
/** "halloween-2026", "newyear-2027": one holiday period, so turning its theme off lasts until the next holiday. */
fun seasonKey(season:Season,date:LocalDate):String = season.palette+"-"+(if(season==Season.NEW_YEAR&&date.monthValue==12)date.year+1 else date.year)
/** The last day a season's theme stays on, for "until 7 November". */
fun seasonEnd(season:Season,date:LocalDate):LocalDate = when(season){
    Season.HALLOWEEN->LocalDate.of(date.year,11,7)
    Season.NEW_YEAR->LocalDate.of(if(date.monthValue==12)date.year+1 else date.year,1,8)
}

/**
 * The user's look: light/dark [mode], interface [palette] (a preset id, "custom" with [customHue], or a holiday palette),
 * holiday themes switching on by themselves ([seasonal]) unless turned off for the current holiday ([seasonSkipped]),
 * an own background picture with [dim] (how much the app's background colour covers it) and [cardAlpha], and holiday
 * [decorations] (snow, bats).
 */
data class Appearance(val mode:String="system",val palette:String="mint",val customHue:Float=200f,val seasonal:Boolean=true,val seasonSkipped:String="",
    val background:Long=0,val dim:Float=0.55f,val cardAlpha:Float=0.9f,val decorations:Boolean=true) {
    val hasBackground:Boolean get() = background>0
}

/** The holiday whose theme is on today, if any. */
fun Appearance.activeSeason(today:LocalDate):Season? = seasonOn(today)?.takeIf{seasonal&&seasonSkipped!=seasonKey(it,today)}
/** The palette shown today: the holiday's while it is on, otherwise the chosen one. */
fun Appearance.effectivePalette(today:LocalDate):String = activeSeason(today)?.palette?:palette
/** Whose decorations to draw: those of the holiday palette on screen, also when it was picked by hand outside the holiday. */
fun Appearance.decorationSeason(today:LocalDate):Season? = Season.entries.find{it.palette==effectivePalette(today)}
/**
 * Choosing a palette by hand always wins: while a holiday theme is on, picking another palette turns the holiday theme off
 * until the next holiday; picking the holiday's own palette brings it back.
 */
fun Appearance.choose(palette:String,today:LocalDate):Appearance {
    val season=seasonOn(today)
    return when {
        season!=null&&palette==season.palette->copy(seasonSkipped="",seasonal=true)
        season!=null&&seasonal->copy(palette=palette,seasonSkipped=seasonKey(season,today))
        else->copy(palette=palette)
    }
}

/** Interface colours to choose from; mint is the original look. */
data class PalettePreset(val id:String,val ru:String,val en:String,val hue:Float,val saturation:Float)
val PALETTES=listOf(
    PalettePreset("mint","Мята","Mint",160f,0.48f),PalettePreset("ocean","Океан","Ocean",203f,0.62f),PalettePreset("lavender","Лаванда","Lavender",258f,0.42f),
    PalettePreset("berry","Ягода","Berry",330f,0.5f),PalettePreset("coral","Коралл","Coral",8f,0.62f),PalettePreset("amber","Янтарь","Amber",36f,0.75f),
    PalettePreset("forest","Лес","Forest",125f,0.4f),PalettePreset("graphite","Графит","Graphite",210f,0.12f))

/** Every colour role of a Material scheme as ARGB, so the palette maths is testable without Android. */
data class SchemeColors(val primary:Int,val onPrimary:Int,val primaryContainer:Int,val onPrimaryContainer:Int,
    val secondary:Int,val onSecondary:Int,val secondaryContainer:Int,val onSecondaryContainer:Int,
    val tertiary:Int,val onTertiary:Int,val tertiaryContainer:Int,val onTertiaryContainer:Int,
    val background:Int,val onBackground:Int,val surface:Int,val onSurface:Int,val surfaceVariant:Int,val onSurfaceVariant:Int,
    val surfaceLowest:Int,val surfaceLow:Int,val surfaceContainer:Int,val surfaceHigh:Int,val surfaceHighest:Int,
    val outline:Int,val outlineVariant:Int)

/** ARGB from hue in degrees and saturation and lightness 0–1. */
fun hsl(h:Float,s:Float,l:Float):Int {
    val hue=((h%360)+360)%360;val sat=s.coerceIn(0f,1f);val light=l.coerceIn(0f,1f)
    val c=(1-abs(2*light-1))*sat;val x=c*(1-abs((hue/60)%2-1));val m=light-c/2
    val (r,g,b)=when{hue<60->Triple(c,x,0f);hue<120->Triple(x,c,0f);hue<180->Triple(0f,c,x);hue<240->Triple(0f,x,c);hue<300->Triple(x,0f,c);else->Triple(c,0f,x)}
    fun ch(v:Float)=((v+m)*255).toInt().coerceIn(0,255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}
/** WCAG relative luminance and contrast ratio. */
fun luminance(argb:Int):Double {
    fun ch(v:Int):Double{val c=v/255.0;return if(c<=0.03928)c/12.92 else ((c+0.055)/1.055).pow(2.4)}
    return 0.2126*ch(argb shr 16 and 0xFF)+0.7152*ch(argb shr 8 and 0xFF)+0.0722*ch(argb and 0xFF)
}
fun contrast(a:Int,b:Int):Double { val x=luminance(a);val y=luminance(b);return (maxOf(x,y)+0.05)/(minOf(x,y)+0.05) }

/** The colour with lightness moved away from [against] until it reaches [ratio] (yellows need it most). */
private fun readable(h:Float,s:Float,l:Float,against:Int,ratio:Double):Int {
    val darker=luminance(against)>0.18;var light=l
    while(contrast(hsl(h,s,light),against)<ratio&&light in 0.01f..0.99f)light+=if(darker)-0.01f else 0.01f
    return hsl(h,s,light)
}

/**
 * A full light or dark scheme: accents from [hue]/[sat], tertiary from [tertiaryHue], and the neutral background and
 * surfaces tinted with [neutralHue]/[neutralSat]. Text roles are pushed until they reach WCAG contrast.
 */
fun generateScheme(hue:Float,sat:Float,dark:Boolean,neutralHue:Float=hue,neutralSat:Float=0.16f,tertiaryHue:Float=hue+40):SchemeColors {
    val white=0xFFFFFFFF.toInt();val n=neutralHue;val ns=neutralSat
    return if(!dark){
        val background=hsl(n,ns*1.1f,0.965f);val surface=hsl(n,ns,0.995f)
        val primary=readable(hue,sat,0.38f,white,4.8)
        val pc=hsl(hue,sat*0.55f,0.9f);val sc=hsl(hue,sat*0.3f,0.9f);val tc=hsl(tertiaryHue,sat*0.5f,0.9f)
        SchemeColors(primary,white,pc,readable(hue,sat*0.6f,0.18f,pc,7.0),
            readable(hue,sat*0.35f,0.36f,white,4.8),white,sc,readable(hue,sat*0.4f,0.18f,sc,7.0),
            readable(tertiaryHue,sat*0.6f,0.38f,white,4.8),white,tc,readable(tertiaryHue,sat*0.6f,0.18f,tc,7.0),
            background,readable(n,ns,0.14f,background,10.0),surface,readable(n,ns,0.14f,surface,10.0),hsl(n,ns*0.9f,0.925f),readable(n,ns*0.6f,0.38f,hsl(n,ns*0.9f,0.925f),4.8),
            hsl(n,ns,1f),hsl(n,ns,0.98f),hsl(n,ns,0.95f),hsl(n,ns*0.9f,0.925f),hsl(n,ns*0.85f,0.9f),
            readable(n,ns*0.45f,0.56f,background,3.0),hsl(n,ns*0.8f,0.84f))
    } else {
        val background=hsl(n,ns*1.4f,0.075f);val surface=hsl(n,ns*1.1f,0.12f)
        val primary=readable(hue,sat*0.75f,0.72f,surface,7.0)
        val pc=hsl(hue,sat*0.45f,0.25f);val sc=hsl(hue,sat*0.3f,0.23f);val tc=hsl(tertiaryHue,sat*0.4f,0.26f)
        SchemeColors(primary,readable(hue,sat*0.7f,0.14f,primary,7.0),pc,readable(hue,sat*0.5f,0.9f,pc,7.0),
            readable(hue,sat*0.3f,0.78f,surface,7.0),hsl(hue,sat*0.4f,0.14f),sc,readable(hue,sat*0.3f,0.9f,sc,7.0),
            readable(tertiaryHue,sat*0.55f,0.76f,surface,7.0),hsl(tertiaryHue,sat*0.6f,0.14f),tc,readable(tertiaryHue,sat*0.5f,0.9f,tc,7.0),
            background,readable(n,ns,0.9f,background,10.0),surface,readable(n,ns,0.9f,surface,10.0),hsl(n,ns,0.19f),readable(n,ns*0.6f,0.74f,hsl(n,ns,0.19f),4.8),
            hsl(n,ns*1.2f,0.055f),hsl(n,ns,0.1f),surface,hsl(n,ns,0.15f),hsl(n,ns,0.185f),
            readable(n,ns*0.5f,0.53f,background,3.0),hsl(n,ns*0.8f,0.3f))
    }
}

/** Colours of a palette id; null for "mint", which keeps the app's hand-tuned original scheme. */
fun paletteScheme(id:String,customHue:Float,dark:Boolean):SchemeColors? = when(id) {
    "mint"->null
    "custom"->generateScheme(customHue,0.55f,dark)
    // Pumpkin orange and witch purple on a warm cream by day, on purple-black by night.
    "halloween"->if(dark)generateScheme(26f,0.95f,true,neutralHue=272f,neutralSat=0.26f,tertiaryHue=276f) else generateScheme(24f,0.9f,false,neutralHue=32f,neutralSat=0.55f,tertiaryHue=276f)
    // Holly red and pine green on snow white by day, on a winter night blue by night.
    "newyear"->if(dark)generateScheme(356f,0.8f,true,neutralHue=218f,neutralSat=0.38f,tertiaryHue=146f) else generateScheme(356f,0.72f,false,neutralHue=208f,neutralSat=0.32f,tertiaryHue=146f)
    else->PALETTES.find{it.id==id}?.let{generateScheme(it.hue,it.saturation,dark)}
}
