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
import az.healthchecker.ui.kit.Button

/** What VitaCore does with Health Connect data; Health Connect opens it from its permission screens. */
class HealthPrivacyActivity:ComponentActivity() {
    override fun attachBaseContext(newBase:Context) { super.attachBaseContext(LanguageStore.wrap(newBase)) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(tr("VitaCore и Health Connect","VitaCore and Health Connect"),style=MaterialTheme.typography.headlineSmall)
                Text(privacyText(),style=MaterialTheme.typography.bodyLarge)
                Button(onClick={finish()}){Text(tr("Понятно","Got it"))}
            }
        } } }
    }
}

fun privacyText():String = tr(
    "VitaCore только читает шаги, сон, тренировки, пульс и вес, которые часы, весы или другие приложения (например, Mi Fitness) записали в Health Connect, и показывает их на телефоне. Приложение ничего не записывает в Health Connect, не читает данные в фоне и не отправляет их в интернет. Шаги, сон, тренировки и пульс остаются в Health Connect и не попадают в резервную копию. Утреннее взвешивание дня копируется в журнал веса приложения — как если бы вы ввели его сами — и хранится вместе с ним. Доступ можно отозвать в любой момент в настройках Health Connect.",
    "VitaCore only reads the steps, sleep, workouts, pulse and weight that a watch, a scale or another app (such as Mi Fitness) wrote to Health Connect, and shows them on the phone. The app writes nothing to Health Connect, does not read in the background and does not send the data anywhere. Steps, sleep, workouts and pulse stay in Health Connect and are not put in backups. The first weigh-in of a day is copied to the app's weight log — as if you had typed it — and kept with it. You can take the access back at any time in Health Connect's settings.")
