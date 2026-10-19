package az.healthchecker.data
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
private val Context.settingsStore by preferencesDataStore("settings")
data class Settings(val theme:String="system",val folder:String="",val lastBackup:Long=0,val backups:List<String> = emptyList())
class SettingsRepository(context:Context) {
    private val store=context.applicationContext.settingsStore
    private val theme=stringPreferencesKey("theme");private val folder=stringPreferencesKey("folder");private val last=longPreferencesKey("lastBackup");private val legacyBackups=stringSetPreferencesKey("backups")
    // Ordered oldest → newest: rotation keeps the newest previous copy, which an unordered set cannot guarantee.
    private val backups=stringPreferencesKey("backupList")
    val settings:Flow<Settings> = store.data.map{Settings(it[theme]?:"system",it[folder]?:"",it[last]?:0,it[backups]?.split('\n')?.filter(String::isNotEmpty)?:(it[legacyBackups]?:emptySet()).toList())}
    suspend fun theme(value:String){store.edit{it[theme]=value}}
    suspend fun folder(value:String){store.edit{if(it[folder]!=value){it[folder]=value;it.remove(backups);it.remove(legacyBackups);it.remove(last)}}}
    suspend fun saved(ids:List<String>){store.edit{it[backups]=ids.joinToString("\n");it.remove(legacyBackups);it[last]=System.currentTimeMillis()}}
}
