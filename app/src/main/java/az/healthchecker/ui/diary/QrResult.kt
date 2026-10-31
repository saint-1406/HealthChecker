package az.healthchecker.ui.diary

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.*
import az.healthchecker.ui.AppViewModel
import az.healthchecker.ui.Panel
import kotlinx.coroutines.CancellationException

/** Opens a QR link in another app; this app itself has no internet access. */
fun openQrLink(context:Context,url:String):String? = try {
    context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));null
} catch(_:ActivityNotFoundException) { tr("Нет приложения, чтобы открыть ссылку","No app can open this link") }

const val RECEIPT_NOTE_RU="Это ссылка на кассовый чек e-kassa. Список покупок хранится на сервере, а у приложения нет доступа в интернет — откройте чек в браузере."
const val RECEIPT_NOTE_EN="This is an e-kassa receipt link. The purchased items are stored on the server and this app has no internet access — open the receipt in a browser."

/** Diary result for a QR code without a product code: a list matched to the food catalog, or text/link actions. */
@Composable fun QrResultPanel(vm:AppViewModel,raw:String,select:(Food)->Unit,close:()->Unit) {
    val content=remember(raw){runCatching{parseQr(raw)}.getOrNull()}
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var linkError by remember(raw){mutableStateOf<String?>(null)}
    Panel {
        when(content) {
            is QrContent.ItemList -> {
                Text(tr("Список из QR · ${content.items.size}","QR list · ${content.items.size}"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("qrListTitle"))
                Text(tr("Нажмите «Записать», чтобы указать порцию найденного продукта. Совпадения проверьте — поиск идёт по названию.","Tap Log to choose a portion of the matched food. Check the matches: they are found by name."),style=MaterialTheme.typography.bodySmall)
                var matches by remember(raw){mutableStateOf<Map<String,Food?>?>(null)}
                LaunchedEffect(raw){
                    matches=content.items.associateWith{item->try{vm.search(item).firstOrNull()}catch(e:CancellationException){throw e}catch(_:Exception){null}}
                }
                if(matches==null)LinearProgressIndicator(Modifier.fillMaxWidth())
                content.items.forEach{item->
                    val food=matches?.get(item)
                    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().testTag("qrListItem")) {
                        Column(Modifier.weight(1f)) {
                            Text(item,style=MaterialTheme.typography.bodyLarge)
                            if(matches!=null)Text(food?.let{"→ "+it.displayName}?:tr("Нет в каталоге","Not in the catalog"),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        if(food!=null)TextButton(onClick={select(food)},modifier=Modifier.heightIn(min=48.dp)){Text(tr("Записать","Log"))}
                    }
                }
            }
            is QrContent.Text -> {
                Text(tr("В QR-коде нет кода товара","This QR code has no product code"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("qrTextTitle"))
                if(content.receipt)Text(tr(RECEIPT_NOTE_RU,RECEIPT_NOTE_EN),style=MaterialTheme.typography.bodySmall)
                SelectionContainer{Text(content.text,maxLines=8,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("qrText"))}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={clipboard.setText(AnnotatedString(content.text))},modifier=Modifier.heightIn(min=48.dp)){Text(tr("Копировать","Copy"))}
                    content.url?.let{url->Button(onClick={linkError=openQrLink(context,url)},modifier=Modifier.heightIn(min=48.dp)){Text(tr("Открыть ссылку","Open link"))}}
                }
                linkError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
            else -> Text(tr("Не удалось прочитать QR-код","Could not read the QR code"),color=MaterialTheme.colorScheme.error)
        }
        TextButton(onClick=close,modifier=Modifier.heightIn(min=48.dp)){Text(tr("Закрыть","Close"))}
    }
}
