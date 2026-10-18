package az.healthchecker.backup
import az.healthchecker.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.InputStream
import java.io.ByteArrayOutputStream
@Serializable private data class BackupEnvelope(val format:String="healthchecker-backup",val schemaVersion:Int=1,val snapshot:UserSnapshot)
object BackupCodec {
    const val MAX_BYTES=25*1024*1024
    private val json=Json{encodeDefaults=true}
    fun encode(snapshot:UserSnapshot):ByteArray {
        validateSnapshot(snapshot)
        return json.encodeToString(BackupEnvelope(snapshot=snapshot)).toByteArray(Charsets.UTF_8).also{require(it.size<=MAX_BYTES){"Копия превышает 25 МБ"}}
    }
    fun decode(bytes:ByteArray):UserSnapshot {
        require(bytes.size<=MAX_BYTES){"Копия превышает 25 МБ"}
        try {
            val e=json.decodeFromString<BackupEnvelope>(bytes.toString(Charsets.UTF_8))
            require(e.format=="healthchecker-backup" && e.schemaVersion==1){"Неподдерживаемый формат копии"}
            validateSnapshot(e.snapshot)
            return e.snapshot
        } catch(e:Exception){throw IllegalArgumentException("Копия повреждена или имеет неподдерживаемый формат",e)}
    }
    fun readLimited(input:InputStream):ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_BYTES){"Копия превышает 25 МБ"};out.write(buffer,0,n)}
        return out.toByteArray()
    }
}
