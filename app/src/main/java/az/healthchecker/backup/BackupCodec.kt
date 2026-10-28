package az.healthchecker.backup
import az.healthchecker.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.InputStream
import java.io.ByteArrayOutputStream
// Schema 2 added workouts, training marks, training settings and dish recipes; schema 3 added fibre, English names,
// medications with their intakes and steps; schema 4 added the weight log and exercise pictures; schema 5 added the
// user's workout videos. Older files still decode.
@Serializable private data class BackupEnvelope(val format:String="healthchecker-backup",val schemaVersion:Int=BackupCodec.SCHEMA_VERSION,val snapshot:UserSnapshot)
object BackupCodec {
    const val MAX_BYTES=25*1024*1024
    const val SCHEMA_VERSION=5
    private val json=Json{encodeDefaults=true}
    fun encode(snapshot:UserSnapshot):ByteArray {
        validateSnapshot(snapshot)
        return json.encodeToString(BackupEnvelope(snapshot=snapshot)).toByteArray(Charsets.UTF_8).also{require(it.size<=MAX_BYTES){tr("Копия превышает 25 МБ","The backup is larger than 25 MB")}}
    }
    fun decode(bytes:ByteArray):UserSnapshot {
        require(bytes.size<=MAX_BYTES){tr("Копия превышает 25 МБ","The backup is larger than 25 MB")}
        try {
            val e=json.decodeFromString<BackupEnvelope>(bytes.toString(Charsets.UTF_8))
            require(e.format=="healthchecker-backup" && e.schemaVersion in 1..SCHEMA_VERSION){tr("Неподдерживаемый формат копии","Unsupported backup format")}
            validateSnapshot(e.snapshot)
            return e.snapshot
        } catch(e:Exception){throw IllegalArgumentException(tr("Копия повреждена или имеет неподдерживаемый формат","The backup is damaged or in an unsupported format"),e)}
    }
    fun readLimited(input:InputStream):ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_BYTES){tr("Копия превышает 25 МБ","The backup is larger than 25 MB")};out.write(buffer,0,n)}
        return out.toByteArray()
    }
}
