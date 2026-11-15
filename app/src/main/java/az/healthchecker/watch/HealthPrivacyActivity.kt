package az.healthchecker.watch
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import az.healthchecker.data.LanguageStore
import az.healthchecker.domain.tr

/** What HealthChecker does with Health Connect data; Health Connect opens it from its permission screens. */
class HealthPrivacyActivity:ComponentActivity() {
    override fun attachBaseContext(newBase:Context) { super.attachBaseContext(LanguageStore.wrap(newBase)) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(tr("HealthChecker и Health Connect","HealthChecker and Health Connect"),style=MaterialTheme.typography.headlineSmall)
                Text(privacyText(),style=MaterialTheme.typography.bodyLarge)
                Button(onClick={finish()}){Text(tr("Понятно","Got it"))}
            }
        } } }
    }
}

fun privacyText():String = tr(
    "HealthChecker только читает шаги, сон, тренировки и пульс, которые часы или другие приложения (например, Mi Fitness) записали в Health Connect, и показывает их на телефоне. Приложение ничего не записывает в Health Connect, не читает данные в фоне, не отправляет их в интернет и не кладёт в резервную копию — они остаются в Health Connect. Доступ можно отозвать в любой момент в настройках Health Connect.",
    "HealthChecker only reads the steps, sleep, workouts and pulse that a watch or another app (such as Mi Fitness) wrote to Health Connect, and shows them on the phone. The app writes nothing to Health Connect, does not read in the background, does not send the data anywhere and does not put it in backups — it stays in Health Connect. You can take the access back at any time in Health Connect's settings.")
