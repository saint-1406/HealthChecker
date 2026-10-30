package az.healthchecker.backup

import az.healthchecker.domain.*
import az.healthchecker.data.TrackBinary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.*
import java.security.MessageDigest
import java.util.zip.*

@Serializable private data class ArchiveEntry(val size:Long,val sha256:String)
@Serializable private data class ArchiveManifest(val format:String="healthchecker-archive",val version:Int=1,val entries:Map<String,ArchiveEntry>)
data class StagedArchive(val snapshot:UserSnapshot,val media:Map<String,File>,val directory:File):Closeable {
    override fun close(){directory.deleteRecursively()}
}
/** ZIP transport never creates a byte array for the gallery or for tracks. All paths are a strict allowlist. */
object ArchiveBackupCodec {
    const val MAX_TOTAL_BYTES=2L*1024*1024*1024
    const val MAX_ENTRIES=25000
    private const val MAX_MANIFEST_BYTES=4L*1024*1024
    private val json=Json{encodeDefaults=true}
    fun requireMediaPath(path:String) {
        val parts=path.split('/');require(parts.size==2)
        when(parts[0]) { "photos"->requirePhotoName(parts[1]);"tracks"->{require(parts[1].endsWith(".bin"));requireExploreId(parts[1].removeSuffix(".bin"))};else->throw IllegalArgumentException("Invalid archive media path") }
    }
    private fun expected(snapshot:UserSnapshot):Set<String> = buildSet {
        snapshot.explore?.let { s ->
            s.findings.mapNotNull{it.photoFile}.forEach{add("photos/$it")}
            s.walks.filter{it.pointCount>0}.forEach{add("tracks/${it.id}.bin")}
        }
    }
    private fun mediaSize(snapshot:UserSnapshot,path:String,file:File):Long = if(path.startsWith("tracks/"))
        snapshot.explore!!.walks.first{path=="tracks/${it.id}.bin"}.pointCount.toLong()*TrackBinary.BYTES else file.length()
    fun write(snapshot:UserSnapshot,media:Map<String,File>,output:OutputStream) {
        validateSnapshot(snapshot);require(media.keys==expected(snapshot));require(media.size+2<=MAX_ENTRIES)
        val manifest=linkedMapOf<String,ArchiveEntry>();var total=0L
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            fun entry(path:String,input:InputStream,size:Long) {
                require(size>=0);total+=size;require(total<=MAX_TOTAL_BYTES)
                val digest=MessageDigest.getInstance("SHA-256");zip.putNextEntry(ZipEntry(path))
                val buffer=ByteArray(32768);var remaining=size
                input.use { source -> while(remaining>0) { val n=source.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());require(n>0){"Missing committed media bytes"};zip.write(buffer,0,n);digest.update(buffer,0,n);remaining-=n } }
                zip.closeEntry();manifest[path]=ArchiveEntry(size,digest.digest().hex())
            }
            val bytes=BackupCodec.encode(snapshot);entry("snapshot.json",bytes.inputStream(),bytes.size.toLong())
            media.toSortedMap().forEach{(path,file)->requireMediaPath(path);require(file.isFile);val size=mediaSize(snapshot,path,file);require(size<=limit(path));entry(path,file.inputStream(),size)}
            val manifestBytes=json.encodeToString(ArchiveManifest(entries=manifest)).toByteArray();require(manifestBytes.size<=MAX_MANIFEST_BYTES)
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifestBytes);zip.closeEntry()
        }
    }
    fun read(input:InputStream,staging:File):StagedArchive {
        require(!staging.exists()){"Staging directory already exists"};require(staging.mkdirs())
        try {
            val source=PushbackInputStream(BufferedInputStream(input),4);val signature=ByteArray(4);val n=source.read(signature);if(n>0)source.unread(signature,0,n)
            if(n<2||signature[0]!=80.toByte()||signature[1]!=75.toByte()) {
                val snapshot=BackupCodec.decode(BackupCodec.readLimited(source));require(expected(snapshot).isEmpty()){"Media requires a ZIP archive"}
                return StagedArchive(snapshot,emptyMap(),staging)
            }
            val actual=linkedMapOf<String,ArchiveEntry>();var total=0L
            ZipInputStream(source).use { zip ->
                while(true) {
                    val entry=zip.nextEntry?:break;val name=entry.name
                    require(!entry.isDirectory&&name !in actual&&actual.size<MAX_ENTRIES){"Duplicate or invalid ZIP entry"}
                    if(name!="snapshot.json"&&name!="manifest.json")requireMediaPath(name)
                    require(entry.size<=limit(name)){"Oversized ZIP entry"}
                    val target=File(staging,name);require(target.canonicalPath.startsWith(staging.canonicalPath+File.separator));target.parentFile!!.mkdirs()
                    val digest=MessageDigest.getInstance("SHA-256");var size=0L;val buffer=ByteArray(32768)
                    FileOutputStream(target).use { out -> while(true) {
                        val count=zip.read(buffer);if(count<0)break
                        size+=count;total+=count;require(size<=limit(name)&&total<=MAX_TOTAL_BYTES){"Archive exceeds storage limits"}
                        out.write(buffer,0,count);digest.update(buffer,0,count)
                    };out.fd.sync() }
                    actual[name]=ArchiveEntry(size,digest.digest().hex());zip.closeEntry()
                }
            }
            require("snapshot.json" in actual&&"manifest.json" in actual){"Incomplete archive"}
            val manifest=json.decodeFromString<ArchiveManifest>(File(staging,"manifest.json").readText())
            require(manifest.format=="healthchecker-archive"&&manifest.version==1&&manifest.entries==actual.filterKeys{it!="manifest.json"}){"Archive hash or size mismatch"}
            val snapshot=BackupCodec.decode(File(staging,"snapshot.json").readBytes())
            require(actual.keys==expected(snapshot)+setOf("snapshot.json","manifest.json")){"Missing or unreferenced media"}
            val media=expected(snapshot).associateWith{File(staging,it)}
            validateMedia(snapshot,media)
            return StagedArchive(snapshot,media,staging)
        } catch(e:Exception){staging.deleteRecursively();if(e is IllegalArgumentException)throw e;throw IllegalArgumentException("Invalid or incomplete backup archive",e)}
    }
    fun validateMedia(snapshot:UserSnapshot,media:Map<String,File>) {
        validateSnapshot(snapshot);require(media.keys==expected(snapshot)){"Missing referenced media"}
        media.forEach { (path,file) ->
            requireMediaPath(path);require(file.isFile&&file.length()<=limit(path))
            if(path.startsWith("photos/")) {
                RandomAccessFile(file,"r").use{require(file.length()>=4&&it.read()==255&&it.read()==216){"Invalid JPEG"};it.seek(file.length()-2);require(it.read()==255&&it.read()==217){"Truncated JPEG"}}
            } else {
                val w=snapshot.explore!!.walks.first{path=="tracks/${it.id}.bin"}
                require(file.length()==w.pointCount.toLong()*TrackBinary.BYTES){"Invalid committed track length"}
                var last:TrackPoint?=null
                DataInputStream(BufferedInputStream(file.inputStream())).use { stream ->
                    val buffer=ByteArray(TrackBinary.BYTES)
                    repeat(w.pointCount){index->stream.readFully(buffer);val p=TrackBinary.decode(buffer);require(index!=0||p.breakBefore);require(last==null||p.breakBefore||p.timeMillis>last!!.timeMillis);last=p}
                }
                require(last==w.lastPoint){"Track checkpoint mismatch"}
            }
        }
    }
    private fun limit(path:String):Long=when {
        path=="snapshot.json"->BackupCodec.MAX_BYTES.toLong()
        path=="manifest.json"->MAX_MANIFEST_BYTES
        path.startsWith("photos/")->MAX_FINDING_PHOTO_BYTES.toLong()
        else->MAX_TRACK_POINTS.toLong()*TrackBinary.BYTES
    }
    private fun ByteArray.hex()=joinToString(""){"%02x".format(it)}
}
