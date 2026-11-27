package az.healthchecker.ui.kit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import az.healthchecker.ui.theme.LocalArcade

/*
 * The app's buttons. Each file imports these by name, so they win over the Material ones of `material3.*`: in the
 * standard layout they are exactly Material's; in Arcade they get straight edges, no shadow and a thin outline where
 * Material would use a tonal fill — the same handlers, the same sizes, so both layouts share every screen.
 */

private val arcadeShape:Shape get() = RectangleShape

@Composable fun Button(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape?=null,colors:ButtonColors=ButtonDefaults.buttonColors(),
    elevation:ButtonElevation?=null,border:BorderStroke?=null,contentPadding:PaddingValues=ButtonDefaults.ContentPadding,
    interactionSource:MutableInteractionSource?=null,content:@Composable RowScope.()->Unit) {
    val a=LocalArcade.current
    androidx.compose.material3.Button(onClick,modifier,enabled,shape?:if(a)arcadeShape else ButtonDefaults.shape,colors,
        elevation?:if(a)ButtonDefaults.buttonElevation(0.dp,0.dp,0.dp,0.dp,0.dp) else ButtonDefaults.buttonElevation(),border,contentPadding,interactionSource,content)
}

@Composable fun OutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape?=null,colors:ButtonColors=ButtonDefaults.outlinedButtonColors(),
    elevation:ButtonElevation?=null,border:BorderStroke?=null,contentPadding:PaddingValues=ButtonDefaults.ContentPadding,
    interactionSource:MutableInteractionSource?=null,content:@Composable RowScope.()->Unit) {
    val a=LocalArcade.current
    androidx.compose.material3.OutlinedButton(onClick,modifier,enabled,shape?:if(a)arcadeShape else ButtonDefaults.outlinedShape,colors,elevation,
        border?:if(a)BorderStroke(1.dp,if(enabled)MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant) else ButtonDefaults.outlinedButtonBorder(enabled),contentPadding,interactionSource,content)
}

/** Tonal in the standard layout; in Arcade an outlined square, so the filled main action stays the only solid one. */
@Composable fun FilledTonalButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape?=null,colors:ButtonColors?=null,
    elevation:ButtonElevation?=null,border:BorderStroke?=null,contentPadding:PaddingValues=ButtonDefaults.ContentPadding,
    interactionSource:MutableInteractionSource?=null,content:@Composable RowScope.()->Unit) {
    val a=LocalArcade.current
    if(a)androidx.compose.material3.OutlinedButton(onClick,modifier,enabled,shape?:arcadeShape,colors?:ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.onSurface),elevation,
        border?:BorderStroke(1.dp,if(enabled)MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),contentPadding,interactionSource,content)
    else androidx.compose.material3.FilledTonalButton(onClick,modifier,enabled,shape?:ButtonDefaults.filledTonalShape,colors?:ButtonDefaults.filledTonalButtonColors(),
        elevation?:ButtonDefaults.filledTonalButtonElevation(),border,contentPadding,interactionSource,content)
}

@Composable fun TextButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape?=null,colors:ButtonColors=ButtonDefaults.textButtonColors(),
    elevation:ButtonElevation?=null,border:BorderStroke?=null,contentPadding:PaddingValues=ButtonDefaults.TextButtonContentPadding,
    interactionSource:MutableInteractionSource?=null,content:@Composable RowScope.()->Unit) {
    androidx.compose.material3.TextButton(onClick,modifier,enabled,shape?:if(LocalArcade.current)arcadeShape else ButtonDefaults.textShape,colors,elevation,border,contentPadding,interactionSource,content)
}

@Composable fun FilledTonalIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape?=null,
    colors:IconButtonColors=IconButtonDefaults.filledTonalIconButtonColors(),interactionSource:MutableInteractionSource?=null,content:@Composable ()->Unit) {
    androidx.compose.material3.FilledTonalIconButton(onClick,modifier,enabled,shape?:if(LocalArcade.current)arcadeShape else IconButtonDefaults.filledShape,colors,interactionSource,content)
}

@Composable fun FilledIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape?=null,
    colors:IconButtonColors=IconButtonDefaults.filledIconButtonColors(),interactionSource:MutableInteractionSource?=null,content:@Composable ()->Unit) {
    androidx.compose.material3.FilledIconButton(onClick,modifier,enabled,shape?:if(LocalArcade.current)arcadeShape else IconButtonDefaults.filledShape,colors,interactionSource,content)
}
