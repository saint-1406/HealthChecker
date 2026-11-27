package az.healthchecker.ui.theme
import android.provider.Settings
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** True while the "Arcade" layout is on; every screen and shared component reads it. */
val LocalArcade = staticCompositionLocalOf { false }

/** Shorthand for [LocalArcade] inside composables. */
val arcade:Boolean @Composable @ReadOnlyComposable get() = LocalArcade.current

/**
 * Arcade corners: straight everywhere. Chips, fields, dialogs and sheets read these from the theme, so the whole app
 * loses its rounding at once; only the big hero blocks get cut corners ([ArcadeCut]).
 */
val ArcadeShapes = Shapes(extraSmall=RoundedCornerShape(0.dp),small=RoundedCornerShape(0.dp),medium=RoundedCornerShape(0.dp),large=RoundedCornerShape(0.dp),extraLarge=RoundedCornerShape(0.dp))
/** The hero block's shape: the top-right and bottom-left corners cut off, like the mockup. */
fun ArcadeCut(cut:Dp=14.dp):CornerBasedShape = CutCornerShape(topStart=0.dp,topEnd=cut,bottomEnd=0.dp,bottomStart=cut)

/**
 * Arcade type: bold grotesque headings and monospace text and figures, so numbers line up in columns. The font scale
 * still applies — only families, weights and spacing change.
 */
fun arcadeTypography(base:Typography=Typography()):Typography {
    val sans=FontFamily.SansSerif;val mono=FontFamily.Monospace
    fun TextStyle.head(weight:FontWeight=FontWeight.Bold,tracking:Float=-0.02f)=copy(fontFamily=sans,fontWeight=weight,letterSpacing=(fontSize.value*tracking).sp)
    // Monospace is wide; a slight negative tracking keeps labels compact without losing the column rhythm.
    fun TextStyle.code(weight:FontWeight?=null)=copy(fontFamily=mono,fontWeight=weight?:fontWeight,letterSpacing=(-fontSize.value*0.04f).sp)
    return base.copy(
        displayLarge=base.displayLarge.head(),displayMedium=base.displayMedium.head(),displaySmall=base.displaySmall.head(),
        headlineLarge=base.headlineLarge.head(),headlineMedium=base.headlineMedium.head(),headlineSmall=base.headlineSmall.head(),
        titleLarge=base.titleLarge.head(),titleMedium=base.titleMedium.head(FontWeight.SemiBold,0f).copy(fontFeatureSettings="tnum"),titleSmall=base.titleSmall.head(FontWeight.SemiBold,0f).copy(fontFeatureSettings="tnum"),
        // Running text stays proportional (monospace makes long Russian lines too wide) but with tabular figures, so
        // numbers still line up; small meta lines and labels are monospace.
        bodyLarge=base.bodyLarge.copy(fontFamily=sans,fontFeatureSettings="tnum"),bodyMedium=base.bodyMedium.copy(fontFamily=sans,fontFeatureSettings="tnum"),bodySmall=base.bodySmall.code(),
        labelLarge=base.labelLarge.code(FontWeight.Medium),labelMedium=base.labelMedium.code(FontWeight.Medium),labelSmall=base.labelSmall.code(FontWeight.Medium))
}

/** Any rounded shape of the standard layout, straight in Arcade. Use for cards, tiles and clipped rows. */
@Composable @ReadOnlyComposable fun rounded(size:Dp):Shape = if(arcade)RectangleShape else RoundedCornerShape(size)
@Composable @ReadOnlyComposable fun rounded(percent:Int):Shape = if(arcade)RectangleShape else RoundedCornerShape(percent)
@Composable @ReadOnlyComposable fun rounded(topStart:Dp=0.dp,topEnd:Dp=0.dp,bottomEnd:Dp=0.dp,bottomStart:Dp=0.dp):Shape =
    if(arcade)RectangleShape else RoundedCornerShape(topStart=topStart,topEnd=topEnd,bottomEnd=bottomEnd,bottomStart=bottomStart)

/**
 * The second accent with a meaning of its own — the pulse and the watch: a warm pink that keeps 4.5:1 against the
 * background in light and dark, whatever interface colour is chosen.
 */
val pulseColor:Color @Composable @ReadOnlyComposable get() {
    val bg=MaterialTheme.colorScheme.background.compositeOver(Color.Black)
    return if(bg.luminance()<0.4f)Color(0xFFFF8FAB) else Color(0xFFB0244F)
}

/** The system "remove animations" setting: motion then jumps straight to the end. */
@Composable fun reducedMotion():Boolean {
    val context=LocalContext.current
    return runCatching{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}.getOrDefault(false)
}
