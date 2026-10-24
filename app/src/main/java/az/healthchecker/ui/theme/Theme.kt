package az.healthchecker.ui.theme
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import az.healthchecker.domain.*
import java.time.LocalDate
// Every role a Material component may read is set, so no default purple leaks into chips, the navigation
// indicator or dialogs; outline keeps text-field borders at ~3:1 contrast, outlineVariant is for dividers.
private val Light=lightColorScheme(primary=Color(0xFF28715C),onPrimary=Color.White,primaryContainer=Color(0xFFDDEEE2),onPrimaryContainer=Color(0xFF153D31),
    secondary=Color(0xFF8A6447),onSecondary=Color.White,secondaryContainer=Color(0xFFD5EADC),onSecondaryContainer=Color(0xFF153D31),
    tertiary=Color(0xFF8A6447),onTertiary=Color.White,tertiaryContainer=Color(0xFFF3E2D3),onTertiaryContainer=Color(0xFF3A2414),
    background=Color(0xFFF6F7F2),onBackground=Color(0xFF202E28),surface=Color(0xFFFFFFFF),onSurface=Color(0xFF202E28),surfaceVariant=Color(0xFFEBEEE7),onSurfaceVariant=Color(0xFF5B675F),
    surfaceTint=Color(0xFF28715C),surfaceContainerLowest=Color(0xFFFFFFFF),surfaceContainerLow=Color(0xFFF9FAF6),surfaceContainer=Color(0xFFF1F3EE),surfaceContainerHigh=Color(0xFFEBEEE7),surfaceContainerHighest=Color(0xFFE4E8E1),
    outline=Color(0xFF8A968E),outlineVariant=Color(0xFFD1D8D0))
private val Dark=darkColorScheme(primary=Color(0xFF9BD1B4),onPrimary=Color(0xFF153D31),primaryContainer=Color(0xFF254C3E),onPrimaryContainer=Color(0xFFDDEEE2),
    secondary=Color(0xFFE2C1A6),onSecondary=Color(0xFF3A2414),secondaryContainer=Color(0xFF2E4A3D),onSecondaryContainer=Color(0xFFDDEEE2),
    tertiary=Color(0xFFE2C1A6),onTertiary=Color(0xFF3A2414),tertiaryContainer=Color(0xFF55402F),onTertiaryContainer=Color(0xFFF3E2D3),
    background=Color(0xFF111B17),onBackground=Color(0xFFE3EBE4),surface=Color(0xFF1C2821),onSurface=Color(0xFFE3EBE4),surfaceVariant=Color(0xFF29352E),onSurfaceVariant=Color(0xFFB2BFB5),
    surfaceTint=Color(0xFF9BD1B4),surfaceContainerLowest=Color(0xFF0C1411),surfaceContainerLow=Color(0xFF17221D),surfaceContainer=Color(0xFF1C2821),surfaceContainerHigh=Color(0xFF233029),surfaceContainerHighest=Color(0xFF2C3A32),
    outline=Color(0xFF7F9186),outlineVariant=Color(0xFF45584B))
private fun SchemeColors.material(dark:Boolean):ColorScheme {
    fun c(v:Int)=Color(v)
    val base=if(dark)Dark else Light
    return base.copy(primary=c(primary),onPrimary=c(onPrimary),primaryContainer=c(primaryContainer),onPrimaryContainer=c(onPrimaryContainer),inversePrimary=c(primaryContainer),
        secondary=c(secondary),onSecondary=c(onSecondary),secondaryContainer=c(secondaryContainer),onSecondaryContainer=c(onSecondaryContainer),
        tertiary=c(tertiary),onTertiary=c(onTertiary),tertiaryContainer=c(tertiaryContainer),onTertiaryContainer=c(onTertiaryContainer),
        background=c(background),onBackground=c(onBackground),surface=c(surface),onSurface=c(onSurface),surfaceVariant=c(surfaceVariant),onSurfaceVariant=c(onSurfaceVariant),
        surfaceTint=c(primary),surfaceContainerLowest=c(surfaceLowest),surfaceContainerLow=c(surfaceLow),surfaceContainer=c(surfaceContainer),surfaceContainerHigh=c(surfaceHigh),surfaceContainerHighest=c(surfaceHighest),
        surfaceBright=c(surfaceHigh),surfaceDim=c(surfaceLow),outline=c(outline),outlineVariant=c(outlineVariant))
}
fun isDark(mode:String,system:Boolean):Boolean = mode=="dark"||mode=="system"&&system
/** The colour scheme of [palette] ("mint" is the original hand-tuned one) in light or dark. */
fun colorSchemeFor(palette:String,customHue:Float,dark:Boolean):ColorScheme = paletteScheme(palette,customHue,dark)?.material(dark)?:if(dark)Dark else Light

/**
 * The app's theme: the palette shown today (a holiday's while its theme is on), light or dark. Over the user's own
 * background picture the cards become slightly see-through, so the picture shows between and behind them.
 */
@Composable fun HealthTheme(appearance:Appearance,today:LocalDate,content:@Composable ()->Unit) {
    val dark=isDark(appearance.mode,isSystemInDarkTheme())
    val scheme=colorSchemeFor(appearance.effectivePalette(today),appearance.customHue,dark)
    // Status and navigation bar icons follow the app's own light or dark look, not the phone's.
    val view=LocalView.current
    if(!view.isInEditMode)SideEffect{
        var context=view.context;while(context is ContextWrapper&&context !is Activity)context=context.baseContext
        (context as? Activity)?.window?.let{WindowCompat.getInsetsController(it,view).apply{isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}}
    }
    MaterialTheme(colorScheme=if(appearance.hasBackground)scheme.copy(surface=scheme.surface.copy(alpha=appearance.cardAlpha)) else scheme,content=content)
}
