package az.healthchecker.data

import java.io.*
import java.util.UUID

/** Room's committed generation chooses rollback versus completion after a process interruption. */
internal class ExploreMediaReplacement(private val root:File) {
    private val current=File(root,"media")
    private val staged=File(root,"media.staged")
    private val previous=File(root,"media.previous")
    private val journal=File(root,"media.restore")
    private var generation:String?=null
    fun prepare(files:Map<String,File>):String {
        check(!journal.exists());staged.deleteRecursively();check(staged.mkdirs())
        try { files.forEach { (path,source) ->
            az.healthchecker.backup.ArchiveBackupCodec.requireMediaPath(path)
            require(source.isFile);val target=File(staged,path);target.parentFile!!.mkdirs()
            source.inputStream().use{input->FileOutputStream(target).use{out->input.copyTo(out);out.fd.sync()}}
        } } catch(e:Exception){staged.deleteRecursively();throw e}
        return UUID.randomUUID().toString().also{generation=it}
    }
    fun activate() {
        val id=checkNotNull(generation);check(!previous.exists());if(current.exists()&&!current.isDirectory)throw IOException("Media path is not a directory")
        val tmp=File(root,"media.restore.tmp");FileOutputStream(tmp).use{it.write("$id\n${current.exists()}".toByteArray());it.fd.sync()}
        if(!tmp.renameTo(journal))throw IOException("Could not save media journal")
        if(current.exists()&&!current.renameTo(previous))throw IOException("Could not preserve media")
        if(!staged.renameTo(current))throw IOException("Could not activate media")
    }
    fun recover(committed:String?) {
        if(journal.exists()) {
            val lines=journal.readLines();check(lines.size==2&&lines[1] in listOf("true","false"))
            if(lines[0]==committed)check(current.isDirectory)
            else if(previous.exists()) { if(current.exists()&&!current.deleteRecursively())throw IOException("Could not roll back media");if(!previous.renameTo(current))throw IOException("Could not restore media") }
            else if(lines[1]=="false"&&!staged.exists()) { if(current.exists()&&!current.deleteRecursively())throw IOException("Could not roll back new media") }
            if(previous.exists()&&!previous.deleteRecursively())throw IOException("Could not clean old media")
            if(staged.exists()&&!staged.deleteRecursively())throw IOException("Could not clean staged media")
            if(!journal.delete())throw IOException("Could not finish media recovery")
        } else if(staged.exists()&&!staged.deleteRecursively())throw IOException("Could not clear staged media")
        File(root,"media.restore.tmp").delete()
    }
}
