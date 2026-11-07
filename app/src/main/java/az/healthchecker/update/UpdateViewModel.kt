package az.healthchecker.update
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * The update section of the profile and the small banner. [launch] carries the next screen to open (Android's
 * "install unknown apps" settings or its installer); the activity opens it and calls [launched].
 */
data class UpdateState(
    val current: String = "",
    val checking: Boolean = false,
    val found: UpdateCheck? = null,
    /** A newer version seen by an earlier check (kept across restarts for the banner); "" when none. */
    val known: String = "",
    val phase: Phase = Phase.IDLE,
    val percent: Int = 0,
    val note: String? = null,
    val error: String? = null,
    val lastCheck: Long = 0,
    val autoCheck: Boolean = true,
    val dismissed: String = "",
) {
    enum class Phase { IDLE, DOWNLOADING, WAITING_PERMISSION, INSTALLER }
    val busy get() = checking || phase == Phase.DOWNLOADING
    val newerVersion: String? get() = found?.takeIf { it.newer }?.update?.version ?: known.takeIf { it.isNotEmpty() && UpdateRules.newer(it, current) }
    val banner: String? get() = newerVersion?.takeIf { it != dismissed && phase == Phase.IDLE }
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val prefs = application.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val engine = UpdateEngine(GithubHttp(), PackageApkInspector(application), UpdateInstaller.folder(application)) { UpdateInstaller.freeBytes(application) }
    private val current = runCatching { application.packageManager.getPackageInfo(application.packageName, 0).versionName }.getOrNull().orEmpty()
    private val _state = MutableStateFlow(UpdateState(current, known = prefs.getString(KNOWN, "").orEmpty(), lastCheck = prefs.getLong(LAST_CHECK, 0),
        autoCheck = prefs.getBoolean(AUTO_CHECK, true), dismissed = prefs.getString(DISMISSED, "").orEmpty()))
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _launch = MutableStateFlow<Intent?>(null)
    val launch: StateFlow<Intent?> = _launch.asStateFlow()
    private var job: Job? = null
    private var verified: File? = null
    private var startupChecked = false

    init { viewModelScope.launch(Dispatchers.IO) { runCatching { engine.cleanUp(current) } } }

    /** The quiet check after start: once a day at most, only when the user keeps it on; silent unless there is something new. */
    fun startupCheck() {
        if (startupChecked) return
        startupChecked = true
        val s = _state.value
        if (dueForCheck(s.autoCheck, s.lastCheck, System.currentTimeMillis())) check(quiet = true)
    }

    /** [thenInstall]: the user tapped "Update" before any check of this run, so a newer version is downloaded right away. */
    fun check(quiet: Boolean = false, thenInstall: Boolean = false) {
        if (_state.value.busy) return
        _state.update { it.copy(checking = true, error = null, note = null) }
        job = viewModelScope.launch(Dispatchers.IO) {
            val found = try {
                engine.check(current)
            } catch (e: CancellationException) {
                _state.update { it.copy(checking = false) }; throw e
            } catch (e: Exception) {
                _state.update { it.copy(checking = false, error = if (quiet) null else UpdateTexts.failure(failure(e))) }
                return@launch
            }
            val now = System.currentTimeMillis()
            val known = if (found.newer) found.update.version else ""
            prefs.edit().putLong(LAST_CHECK, now).putString(KNOWN, known).apply()
            _state.update { it.copy(checking = false, found = found, known = known, lastCheck = now, note = if (found.newer) null else UpdateTexts.latest(current)) }
            if (found.newer && thenInstall) downloadAndHandOver(found, askPermission = true)
        }
    }

    /** "Update": download, check, then Android's installer. [askPermission]: the settings page may open (never on a retry). */
    fun install(askPermission: Boolean = true) {
        val s = _state.value
        if (s.busy) return
        val found = s.found ?: return check(thenInstall = true)
        if (!found.newer) return
        job = viewModelScope.launch(Dispatchers.IO) { downloadAndHandOver(found, askPermission) }
    }

    private suspend fun downloadAndHandOver(found: UpdateCheck, askPermission: Boolean) {
        _state.update { it.copy(phase = UpdateState.Phase.DOWNLOADING, percent = 0, error = null, note = UpdateTexts.downloading(0)) }
        try {
            val scope = kotlin.coroutines.coroutineContext
            val file = verified?.takeIf { it.isFile && it.name == found.update.apkName } ?: engine.download(found.update) { done, total ->
                scope.ensureActive()
                val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                if (percent != _state.value.percent) _state.update { it.copy(percent = percent, note = UpdateTexts.downloading(percent)) }
            }
            verified = file
            handOver(file, found.update.version, askPermission)
        } catch (e: CancellationException) {
            _state.update { it.copy(phase = UpdateState.Phase.IDLE, note = null) }; throw e
        } catch (e: Exception) {
            _state.update { it.copy(phase = UpdateState.Phase.IDLE, note = null, error = UpdateTexts.failure(failure(e))) }
        }
    }

    private fun handOver(file: File, version: String, askPermission: Boolean) {
        if (!UpdateInstaller.canInstall(context)) {
            if (askPermission) {
                _state.update { it.copy(phase = UpdateState.Phase.WAITING_PERMISSION, note = UpdateTexts.permission) }
                _launch.value = UpdateInstaller.permissionIntent(context)
            } else _state.update { it.copy(phase = UpdateState.Phase.IDLE, note = UpdateTexts.permission) }
            return
        }
        _state.update { it.copy(phase = UpdateState.Phase.INSTALLER, note = UpdateTexts.verified(version)) }
        _launch.value = UpdateInstaller.installIntent(context, file)
    }

    fun cancel() { job?.cancel() }

    /** The activity opened [launch]; [failure] when no app could open it. */
    fun launched(failure: Exception? = null) {
        val intent = _launch.value
        _launch.value = null
        if (failure == null) return
        // No "install unknown apps" page on this phone: the installer asks by itself.
        val file = verified
        if (intent?.action != Intent.ACTION_VIEW && file != null) {
            _state.update { it.copy(phase = UpdateState.Phase.INSTALLER) }
            _launch.value = runCatching { UpdateInstaller.installIntent(context, file) }.getOrNull()
            return
        }
        _state.update { it.copy(phase = UpdateState.Phase.IDLE, note = null, error = UpdateTexts.failure(UpdateFailure(UpdateFailure.Reason.INSTALLER, failure.message ?: failure.javaClass.simpleName))) }
    }

    /** Back in the app: from the settings the installer is tried once more by itself; from a cancelled installer the button works again. */
    fun resumed() {
        when (_state.value.phase) {
            UpdateState.Phase.WAITING_PERMISSION -> {
                val file = verified; val version = _state.value.found?.update?.version
                if (file != null && version != null) handOver(file, version, askPermission = false) else _state.update { it.copy(phase = UpdateState.Phase.IDLE) }
            }
            UpdateState.Phase.INSTALLER -> _state.update { it.copy(phase = UpdateState.Phase.IDLE, note = null) }
            else -> Unit
        }
    }

    fun setAutoCheck(on: Boolean) { prefs.edit().putBoolean(AUTO_CHECK, on).apply(); _state.update { it.copy(autoCheck = on) } }

    fun dismissBanner() { val v = _state.value.banner ?: return; prefs.edit().putString(DISMISSED, v).apply(); _state.update { it.copy(dismissed = v) } }

    private fun failure(e: Exception) = e as? UpdateFailure ?: (e as? java.io.IOException)?.let(GithubHttp::networkFailure) ?: UpdateFailure(UpdateFailure.Reason.INTERRUPTED, e.message ?: e.javaClass.simpleName)

    companion object {
        private const val LAST_CHECK = "lastCheck"; private const val KNOWN = "knownVersion"; private const val AUTO_CHECK = "autoCheck"; private const val DISMISSED = "dismissedVersion"
        const val DAY = 24L * 60 * 60 * 1000
        /** At most once a day; a clock set back counts as "due" so the check is not stuck for days. */
        fun dueForCheck(autoCheck: Boolean, lastCheck: Long, now: Long) = autoCheck && (now - lastCheck >= DAY || now < lastCheck)
    }
}
