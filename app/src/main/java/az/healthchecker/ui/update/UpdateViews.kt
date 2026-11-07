package az.healthchecker.ui.update
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import az.healthchecker.domain.Lang
import az.healthchecker.domain.tr
import az.healthchecker.ui.Panel
import az.healthchecker.ui.SectionTitle
import az.healthchecker.update.UpdateState
import az.healthchecker.update.UpdateTexts
import az.healthchecker.update.UpdateViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Always composed while the app is open: opens the screens the updater asks for and resumes it on coming back. */
@Composable fun UpdateEffects(vm: UpdateViewModel, startupCheck: Boolean) {
    val context = LocalContext.current
    val launch by vm.launch.collectAsStateWithLifecycle()
    LaunchedEffect(launch) {
        val intent = launch ?: return@LaunchedEffect
        val failure = runCatching { context.startActivity(intent) }.exceptionOrNull()
        vm.launched(failure as? Exception ?: failure?.let { Exception(it) })
    }
    LifecycleResumeEffect(vm) { vm.resumed(); onPauseOrDispose { } }
    // A while after start, so opening the diary is never slowed down by the network.
    LaunchedEffect(startupCheck) { if (startupCheck) { kotlinx.coroutines.delay(15_000); vm.startupCheck() } }
}

/** A small line over the main screens when a newer version is known; tapping it opens the profile's update section. */
@Composable fun UpdateBanner(vm: UpdateViewModel, open: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val version = state.banner ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("updateBanner")) {
        Row(Modifier.clickable(onClick = open).heightIn(min = 48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, null)
            Spacer(Modifier.width(12.dp))
            Text(UpdateTexts.available(version) + tr(" — открыть", " — open"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::dismissBanner) { Icon(Icons.Outlined.Close, tr("Скрыть", "Hide")) }
        }
    }
}

/** The "Updates" section of the profile, worded after ATLAS: this version, the newest one, what's new, the buttons. */
@Composable fun UpdatePanel(vm: UpdateViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    var notes by rememberSaveable { mutableStateOf(false) }
    val found = state.found?.takeIf { it.newer }?.update
    Panel(modifier.testTag("updatePanel")) {
        SectionTitle(tr("Обновления", "Updates"), tr("Новые версии с GitHub: github.com/saint-1406/HealthChecker", "New versions from GitHub: github.com/saint-1406/HealthChecker"))
        if (state.current.isNotBlank()) Text(UpdateTexts.current(state.current), style = MaterialTheme.typography.bodyMedium)
        state.newerVersion?.let { v ->
            Text(UpdateTexts.available(v) + (found?.let { " · " + UpdateTexts.megabytes(it.size) } ?: ""), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            if (found != null && found.size > 50L * 1024 * 1024) Text(tr("Файл большой — лучше скачивать по Wi‑Fi.", "It is a large file — Wi‑Fi is best."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.phase == UpdateState.Phase.DOWNLOADING) LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
        state.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("updateError")) }
        if (state.found == null && state.lastCheck > 0) Text(tr("Последняя проверка: ", "Last checked: ") + Instant.ofEpochMilli(state.lastCheck).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(if (Lang.en) "MMM d, HH:mm" else "d MMM, HH:mm", Lang.locale)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (found != null && found.notes.isNotBlank()) {
            TextButton(onClick = { notes = !notes }) { Text(if (notes) tr("Скрыть, что нового", "Hide what’s new") else tr("Что нового", "What’s new")) }
            if (notes) Text(found.notes.take(4000), style = MaterialTheme.typography.bodySmall)
        }
        if (state.newerVersion != null) {
            if (state.phase == UpdateState.Phase.DOWNLOADING) OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text(tr("Отменить загрузку", "Cancel the download")) }
            else Button(onClick = { vm.install() }, enabled = !state.busy && state.phase == UpdateState.Phase.IDLE, modifier = Modifier.fillMaxWidth().testTag("updateInstall")) { Text(tr("Обновить", "Update")) }
        }
        OutlinedButton(onClick = { vm.check() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("updateCheck")) {
            Text(if (state.checking) tr("Проверяю…", "Checking…") else tr("Проверить обновления", "Check for updates"))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = state.autoCheck, role = Role.Switch, onValueChange = vm::setAutoCheck).testTag("updateAutoCheck"), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Проверять раз в день при запуске", "Check once a day on start"), modifier = Modifier.weight(1f))
            Switch(checked = state.autoCheck, onCheckedChange = null)
        }
        Text(tr("Приложение выходит в интернет только здесь: при проверке и загрузке обновления с GitHub. Файл сверяется с опубликованной SHA-256 и ключом подписи, затем Android сам предлагает установку поверх текущей версии — дневник и находки остаются.",
            "The app goes online only here: to check for and download an update from GitHub. The file is checked against the published SHA-256 and the signing key, then Android offers to install it over this version — your diary and findings stay."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
