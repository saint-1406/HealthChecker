package az.healthchecker.backup
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.UUID
class SafBackupStore(private val context:Context,private val folder:Uri):BackupStore {
    override suspend fun writeNew(bytes:ByteArray):String {
        val root=DocumentFile.fromTreeUri(context,folder)?:error("Папка недоступна")
        require(root.canWrite()){ "Нет доступа к папке. Выберите её снова." }
        val file=root.createFile("application/json","HealthChecker-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")?:error("Не удалось создать файл")
        val stream=context.contentResolver.openOutputStream(file.uri,"w")?:error("Не удалось открыть файл")
        stream.use{it.write(bytes);it.flush()}
        return file.uri.toString()
    }
    override suspend fun read(id:String):ByteArray = context.contentResolver.openInputStream(Uri.parse(id))?.use{BackupCodec.readLimited(it)}?:error("Не удалось прочитать файл")
    override suspend fun delete(id:String) { DocumentFile.fromSingleUri(context,Uri.parse(id))?.delete() }
}
