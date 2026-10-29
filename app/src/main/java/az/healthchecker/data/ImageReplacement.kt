package az.healthchecker.data

import az.healthchecker.domain.EXERCISE_ID
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Directory swap journal. The generation committed by Room decides recovery after process death. */
class ImageReplacement(private val root:File,private val move:(File,File)->Boolean={a,b->a.renameTo(b)}) {
    private val current=File(root,"exercise-images")
    private val staged=File(root,"exercise-images.staged")
    private val previous=File(root,"exercise-images.previous")
    private val journal=File(root,"exercise-images.restore")
    private var generation:String?=null

    fun prepare(images:Map<String,ByteArray>):String {
        check(!journal.exists()){ "Recover the previous image replacement first" }
        images.keys.forEach{require(EXERCISE_ID.matches(it))}
        if(staged.exists()&&!staged.deleteRecursively())throw IOException("Could not clear staged pictures")
        if(!staged.mkdirs())throw IOException("Could not prepare pictures")
        try { images.forEach{(id,bytes)->writeSynced(File(staged,"$id.jpg"),bytes)} }
        catch(e:Exception){staged.deleteRecursively();throw e}
        return UUID.randomUUID().toString().also{generation=it}
    }
    fun activate() {
        val id=checkNotNull(generation)
        if(current.exists()&&!current.isDirectory)throw IOException("The picture path is not a directory")
        check(!previous.exists()){ "Recover the previous image replacement first" }
        val hadOriginal=current.exists()
        // A complete journal reaches disk before any directory can move. Rename the journal last.
        val temp=File(root,"exercise-images.restore.tmp")
        writeSynced(temp,"$id\n$hadOriginal".toByteArray(Charsets.UTF_8))
        if(!temp.renameTo(journal))throw IOException("Could not save the picture restore journal")
        if(hadOriginal&&!move(current,previous))throw IOException("Could not preserve previous pictures")
        if(!move(staged,current))throw IOException("Could not activate restored pictures")
    }
    fun recover(committedGeneration:String?) {
        if(journal.exists()) {
            val lines=journal.readLines()
            check(lines.size==2 && lines[1] in listOf("true","false")){ "Invalid picture restore journal" }
            if(lines[0]==committedGeneration) {
                check(current.isDirectory){ "Committed pictures are unavailable" }
            } else if(previous.exists()) {
                if(current.exists()&&!current.deleteRecursively())throw IOException("Could not roll back pictures")
                if(!move(previous,current))throw IOException("Could not restore previous pictures")
            } else if(lines[1]=="false" && !staged.exists()) {
                if(current.exists()&&!current.deleteRecursively())throw IOException("Could not remove uncommitted pictures")
            }
            // Delete the journal only after recovery succeeded; the next launch can retry a failed recovery.
            if(previous.exists()&&!previous.deleteRecursively())throw IOException("Could not clean previous pictures")
            if(staged.exists()&&!staged.deleteRecursively())throw IOException("Could not clean staged pictures")
            if(!journal.delete())throw IOException("Could not finish picture recovery")
        } else if(staged.exists()&&!staged.deleteRecursively())throw IOException("Could not clean staged pictures")
        File(root,"exercise-images.restore.tmp").delete()
    }
    private fun writeSynced(file:File,bytes:ByteArray) { FileOutputStream(file).use{it.write(bytes);it.fd.sync()} }
}
