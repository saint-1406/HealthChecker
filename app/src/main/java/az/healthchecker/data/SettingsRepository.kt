package az.healthchecker.data
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.*
import az.healthchecker.domain.Appearance
import kotlinx.coroutines.flow.*
private val Context.settingsStore by preferencesDataStore("settings")
data class Settings(val appearance:Appearance=Appearance(),val folder:String="",val lastBackup:Long=0,val backups:List<String> = emptyList()) {
    val theme:String get() = appearance.mode
}
class SettingsRepository(context:Context) {
    private val store=context.applicationContext.settingsStore
    private val theme=stringPreferencesKey("theme");private val folder=stringPreferencesKey("folder");private val last=longPreferencesKey("lastBackup");private val legacyBackups=stringSetPreferencesKey("backups")
    // Ordered oldest → newest: rotation keeps the newest previous copy, which an unordered set cannot guarantee.
    private val backups=stringPreferencesKey("backupList")
    private val palette=stringPreferencesKey("palette");private val customHue=floatPreferencesKey("customHue");private val seasonal=booleanPreferencesKey("seasonal")
    private val seasonSkipped=stringPreferencesKey("seasonSkipped");private val background=longPreferencesKey("background");private val dim=floatPreferencesKey("backgroundDim")
    private val cardAlpha=floatPreferencesKey("cardAlpha");private val decorations=booleanPreferencesKey("decorations")
    val settings:Flow<Settings> = store.data.map{
        val d=Appearance()
        Settings(Appearance(it[theme]?:d.mode,it[palette]?:d.palette,it[customHue]?:d.customHue,it[seasonal]?:d.seasonal,it[seasonSkipped]?:d.seasonSkipped,
            it[background]?:d.background,it[dim]?:d.dim,it[cardAlpha]?:d.cardAlpha,it[decorations]?:d.decorations),
            it[folder]?:"",it[last]?:0,it[backups]?.split('\n')?.filter(String::isNotEmpty)?:(it[legacyBackups]?:emptySet()).toList())
    }
    suspend fun theme(value:String){store.edit{it[theme]=value}}
    suspend fun appearance(a:Appearance){store.edit{
        it[theme]=a.mode;it[palette]=a.palette;it[customHue]=a.customHue;it[seasonal]=a.seasonal;it[seasonSkipped]=a.seasonSkipped
        it[background]=a.background;it[dim]=a.dim;it[cardAlpha]=a.cardAlpha;it[decorations]=a.decorations
    }}
    suspend fun folder(value:String){store.edit{if(it[folder]!=value){it[folder]=value;it.remove(backups);it.remove(legacyBackups);it.remove(last)}}}
    suspend fun saved(ids:List<String>){store.edit{it[backups]=ids.joinToString("\n");it.remove(legacyBackups);it[last]=System.currentTimeMillis()}}
}
