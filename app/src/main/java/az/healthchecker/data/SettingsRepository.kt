package az.healthchecker.data
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.*
import az.healthchecker.domain.Appearance
import az.healthchecker.domain.VideoFile
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
private val Context.settingsStore by preferencesDataStore("settings")
/** [videoFiles]: video id → the user's own copy on this phone; links to documents work only on this install, so they stay out of backups. */
data class Settings(val appearance:Appearance=Appearance(),val folder:String="",val lastBackup:Long=0,val backups:List<String> = emptyList(),val videoFiles:Map<String,VideoFile> = emptyMap()) {
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
    private val videoFiles=stringPreferencesKey("videoFiles")
    val settings:Flow<Settings> = store.data.map{
        val d=Appearance()
        Settings(Appearance(it[theme]?:d.mode,it[palette]?:d.palette,it[customHue]?:d.customHue,it[seasonal]?:d.seasonal,it[seasonSkipped]?:d.seasonSkipped,
            it[background]?:d.background,it[dim]?:d.dim,it[cardAlpha]?:d.cardAlpha,it[decorations]?:d.decorations),
            it[folder]?:"",it[last]?:0,it[backups]?.split('\n')?.filter(String::isNotEmpty)?:(it[legacyBackups]?:emptySet()).toList(),
            it[videoFiles]?.let{s->runCatching{Json.decodeFromString<Map<String,VideoFile>>(s)}.getOrNull()}?:emptyMap())
    }
    suspend fun theme(value:String){store.edit{it[theme]=value}}
    suspend fun appearance(a:Appearance){store.edit{
        it[theme]=a.mode;it[palette]=a.palette;it[customHue]=a.customHue;it[seasonal]=a.seasonal;it[seasonSkipped]=a.seasonSkipped
        it[background]=a.background;it[dim]=a.dim;it[cardAlpha]=a.cardAlpha;it[decorations]=a.decorations
    }}
    suspend fun folder(value:String){store.edit{if(it[folder]!=value){it[folder]=value;it.remove(backups);it.remove(legacyBackups);it.remove(last)}}}
    /** Links (or with null unlinks) the user's own file for a video; returns the link it replaced. */
    suspend fun videoFile(id:String,file:VideoFile?):VideoFile? {
        var old:VideoFile?=null
        store.edit{
            val map=it[videoFiles]?.let{s->runCatching{Json.decodeFromString<Map<String,VideoFile>>(s)}.getOrNull()}.orEmpty()
            old=map[id];it[videoFiles]=Json.encodeToString(if(file==null)map-id else map+(id to file))
        }
        return old
    }
    suspend fun saved(ids:List<String>){store.edit{it[backups]=ids.joinToString("\n");it.remove(legacyBackups);it[last]=System.currentTimeMillis()}}
}
