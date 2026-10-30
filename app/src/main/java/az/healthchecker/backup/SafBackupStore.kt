package az.healthchecker.backup
import az.healthchecker.domain.tr
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.UUID
class SafBackupStore(private val context:Context,private val folder:Uri):BackupStore {
    override suspend fun writeNew(bytes:ByteArray):String {
        val root=DocumentFile.fromTreeUri(context,folder)?:error(tr("Папка недоступна","The folder is unavailable"))
        require(root.canWrite()){ tr("Нет доступа к папке. Выберите её снова.","No access to the folder. Choose it again.") }
        val file=root.createFile("application/json","HealthChecker-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")?:error(tr("Не удалось создать файл","Could not create the file"))
        val stream=context.contentResolver.openOutputStream(file.uri,"w")?:error(tr("Не удалось открыть файл","Could not open the file"))
        stream.use{it.write(bytes);it.flush()}
        return file.uri.toString()
    }
    fun open(id:String):java.io.InputStream = context.contentResolver.openInputStream(Uri.parse(id))?:error("Could not open backup")
    suspend fun writeArchive(source:java.io.File):String {
        val root=DocumentFile.fromTreeUri(context,folder)?:error("Backup folder unavailable")
        require(root.canWrite()){"No write access to backup folder"}
        val target=root.createFile("application/zip","HealthChecker-${System.currentTimeMillis()}-${UUID.randomUUID()}.zip")?:error("Could not create backup")
        try {
            val output=context.contentResolver.openOutputStream(target.uri,"w")?:error("Could not open backup")
            output.use{out->source.inputStream().use{it.copyTo(out)};out.flush()}
            return target.uri.toString()
        } catch(e:Exception){target.delete();throw e}
    }
    override suspend fun read(id:String):ByteArray = context.contentResolver.openInputStream(Uri.parse(id))?.use{BackupCodec.readLimited(it)}?:error(tr("Не удалось прочитать файл","Could not read the file"))
    override suspend fun delete(id:String) { DocumentFile.fromSingleUri(context,Uri.parse(id))?.delete() }
}
