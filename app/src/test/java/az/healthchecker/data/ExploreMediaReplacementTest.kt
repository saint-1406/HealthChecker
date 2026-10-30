package az.healthchecker.data

import java.nio.file.Files
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ExploreMediaReplacementTest {
    @Test fun interruptedUncommittedSwapRestoresOriginalDirectory() {
        val root=Files.createTempDirectory("media-journal").toFile()
        try {
            val photo="${UUID.randomUUID()}.jpg";val old=File(root,"media/photos/$photo").apply{parentFile!!.mkdirs();writeText("original")}
            val source=File(root,"new.jpg").apply{writeText("new")}
            val replacement=ExploreMediaReplacement(root);replacement.prepare(mapOf("photos/$photo" to source));replacement.activate()
            assertEquals("new",old.readText())
            ExploreMediaReplacement(root).recover(null)
            assertEquals("original",old.readText());assertFalse(File(root,"media.restore").exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun committedSwapSurvivesRecreatedRecoveryObject() {
        val root=Files.createTempDirectory("media-journal").toFile()
        try {
            val photo="${UUID.randomUUID()}.jpg";File(root,"media/photos/$photo").apply{parentFile!!.mkdirs();writeText("old")}
            val source=File(root,"new.jpg").apply{writeText("committed")}
            val replacement=ExploreMediaReplacement(root);val generation=replacement.prepare(mapOf("photos/$photo" to source));replacement.activate()
            ExploreMediaReplacement(root).recover(generation)
            assertEquals("committed",File(root,"media/photos/$photo").readText());assertFalse(File(root,"media.previous").exists())
        } finally {root.deleteRecursively()}
    }
}
