package az.healthchecker.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
val RU:Locale=Locale.forLanguageTag("ru")
// Editable form value: keeps full precision so re-saving an unchanged field does not round it.
fun plain(value:Double):String = if(value%1.0==0.0&&value<1e15)value.toLong().toString() else value.toString().replace('.',',')
fun plural(n:Int,one:String,few:String,many:String):String = "$n "+when{n%100 in 11..14->many;n%10==1->one;n%10 in 2..4->few;else->many}
fun number(value:Double?,decimals:Int=0):String = value?.let{String.format(RU,"%.${decimals}f",it)}?:"—"
@Composable fun SectionTitle(title:String,subtitle:String?=null) {
    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
    subtitle?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
