package az.healthchecker.backup
import az.healthchecker.domain.tr
import az.healthchecker.domain.UserSnapshot
interface BackupStore {
    suspend fun writeNew(bytes:ByteArray):String
    suspend fun read(id:String):ByteArray
    suspend fun delete(id:String)
}
class BackupService(private val store:BackupStore) {
    suspend fun writeVerified(snapshot:UserSnapshot):String {
        val bytes=BackupCodec.encode(snapshot)
        val id=store.writeNew(bytes)
        require(BackupCodec.decode(store.read(id))==snapshot){tr("Не удалось проверить резервную копию","Could not verify the backup")}
        return id
    }
}
