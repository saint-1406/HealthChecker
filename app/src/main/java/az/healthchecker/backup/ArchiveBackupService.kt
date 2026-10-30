package az.healthchecker.backup

import android.content.Context
import az.healthchecker.data.HealthRepository
import java.io.File
import java.util.UUID

/** A verified local ZIP is streamed to SAF and read back before the coordinator rotates old copies. */
class ArchiveBackupService(private val context:Context,private val repository:HealthRepository,private val store:SafBackupStore) {
    suspend fun writeCurrentVerified():Pair<Long,String> {
        val local=File(context.cacheDir,"backup-${UUID.randomUUID()}.zip")
        var id:String?=null
        try {
            val (revision,snapshot)=repository.withCurrentArchive { revision,snapshot,media ->
                local.outputStream().use{ArchiveBackupCodec.write(snapshot,media,it)}
                revision to snapshot
            }
            val written=store.writeArchive(local)
            id=written
            val staging=File(context.cacheDir,"verify-${UUID.randomUUID()}")
            val restored=store.open(written).use{ArchiveBackupCodec.read(it,staging)}
            restored.use{require(it.snapshot==snapshot){"Backup verification failed"}}
            return revision to written
        } catch(e:Exception) {
            id?.let{runCatching{store.delete(it)}};throw e
        } finally {local.delete()}
    }
}
