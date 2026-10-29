package az.healthchecker.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun SaveStatus(vm:AppViewModel,openProfile:()->Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val revision by vm.revision.collectAsStateWithLifecycle()
    val copying by vm.backupBusy.collectAsStateWithLifecycle()
    val error by vm.backupError.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().clickable(onClick=openProfile).heightIn(min=48.dp).padding(vertical=6.dp).testTag("saveStatus"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(if(busy)Icons.Outlined.Save else Icons.Outlined.CheckCircle,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
            Text(if(busy)tr("Сохраняем на телефоне…","Saving on this phone…") else tr("На телефоне · сохранено","On this phone · saved"),style=MaterialTheme.typography.bodySmall)
        }
        val text=when {
            settings.folder.isEmpty()->tr("Резервная копия не настроена","Backup is not set up")
            error!=null->tr("Резервная копия требует внимания","Backup needs attention")
            copying->tr("Резервная копия обновляется…","Updating backup…")
            settings.lastBackup==0L||revision.current!=revision.backedUp->tr("Есть изменения без резервной копии","Some changes are not backed up")
            else->tr("Резервная копия · ","Backup · ")+Instant.ofEpochMilli(settings.lastBackup).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if(Lang.en)"MMM d, HH:mm" else "d MMM, HH:mm",Lang.locale))
        }
        Text(text,style=MaterialTheme.typography.bodySmall,color=if(error!=null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun BackupSetupCard(vm:AppViewModel,snapshot:UserSnapshot,chooseFolder:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    if(settings.folder.isEmpty()&&!settings.backupPromptDismissed&&snapshot.entries.isNotEmpty())Panel(Modifier.testTag("backupSetup")) {
        SectionTitle(tr("Сохраните дневник после переустановки","Keep your diary after reinstalling"),tr("Записи уже на телефоне. Выберите папку для отдельной резервной копии.","Entries are already on this phone. Choose a folder for a separate backup."))
        Button(onClick=chooseFolder,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(tr("Выбрать папку для копии","Choose backup folder"))}
        TextButton(onClick={vm.dismissBackupSetup()},modifier=Modifier.fillMaxWidth()){Text(tr("Позже","Later"))}
    }
}
