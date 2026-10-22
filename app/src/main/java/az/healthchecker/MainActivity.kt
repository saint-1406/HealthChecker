package az.healthchecker
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import az.healthchecker.data.LanguageStore
import az.healthchecker.ui.HealthApp
class MainActivity : ComponentActivity() {
    // Dates, the date picker and system strings follow the language chosen in the app, not the phone's.
    override fun attachBaseContext(newBase:Context) { super.attachBaseContext(LanguageStore.wrap(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);enableEdgeToEdge();setContent { HealthApp() } }
}
