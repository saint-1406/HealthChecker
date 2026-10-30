package az.healthchecker.backup
import az.healthchecker.domain.UserSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
data class BackupConfiguration(val folder:String,val lastBackup:Long,val files:List<String>)
interface BackupEnvironment {
    suspend fun configuration():BackupConfiguration
    suspend fun selectFolder(folder:String)
    suspend fun snapshot():Pair<Long,UserSnapshot>
    suspend fun savedRevision():Long
    suspend fun recordSaved(folder:String,files:List<String>,revision:Long)
    fun store(folder:String):BackupStore
    suspend fun writeVerified(folder:String,snapshot:UserSnapshot):String = BackupService(store(folder)).writeVerified(snapshot)
    suspend fun writeVersioned(folder:String,revision:Long,snapshot:UserSnapshot):Pair<Long,String> = revision to writeVerified(folder,snapshot)
}
class BackupCoordinator(private val environment:BackupEnvironment) {
    private val mutex=Mutex()
    // Waits for a running backup so its result is never recorded against the newly selected folder.
    suspend fun selectFolder(folder:String)=mutex.withLock { environment.selectFolder(folder) }
    suspend fun backup(force:Boolean=false)=mutex.withLock {
        val config=environment.configuration()
        if(config.folder.isEmpty())return@withLock
        val (version,snapshot)=environment.snapshot()
        if(!force && version==environment.savedRevision() && config.lastBackup>0)return@withLock
        val store=environment.store(config.folder)
        val (savedVersion,id)=environment.writeVersioned(config.folder,version,snapshot)
        val keep=config.files.takeLast(1)+id
        environment.recordSaved(config.folder,keep,savedVersion)
        (config.files-keep.toSet()).forEach{runCatching{store.delete(it)}}
    }
}
