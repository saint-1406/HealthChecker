package az.healthchecker.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.IOException

class ImageReplacementTest {
    private fun root()=Files.createTempDirectory("images-test").toFile()
    private fun old(root:java.io.File){root.resolve("exercise-images").mkdirs();root.resolve("exercise-images/squat.jpg").writeBytes(byteArrayOf(1,2))}
    @Test fun failedActivationKeepsOriginalFiles() {
        val root=root();old(root);var moves=0
        val replacement=ImageReplacement(root){a,b->moves++;if(moves==2)false else a.renameTo(b)}
        replacement.prepare(mapOf("lunge" to byteArrayOf(3,4)))
        assertThrows(IOException::class.java){replacement.activate()}
        replacement.recover(null)
        assertArrayEquals(byteArrayOf(1,2),root.resolve("exercise-images/squat.jpg").readBytes())
        assertFalse(root.resolve("exercise-images/lunge.jpg").exists())
    }
    @Test fun processDeathBeforeDatabaseCommitRestoresOldPictures() {
        val root=root();old(root)
        val replacement=ImageReplacement(root);replacement.prepare(mapOf("lunge" to byteArrayOf(3,4)));replacement.activate()
        ImageReplacement(root).recover(null)
        assertArrayEquals(byteArrayOf(1,2),root.resolve("exercise-images/squat.jpg").readBytes())
        assertFalse(root.resolve("exercise-images/lunge.jpg").exists())
    }
    @Test fun processDeathAfterCommitKeepsNewPicturesAndCleansJournal() {
        val root=root();old(root)
        val replacement=ImageReplacement(root);val generation=replacement.prepare(mapOf("lunge" to byteArrayOf(3,4)));replacement.activate()
        ImageReplacement(root).recover(generation)
        assertArrayEquals(byteArrayOf(3,4),root.resolve("exercise-images/lunge.jpg").readBytes())
        assertFalse(root.resolve("exercise-images/squat.jpg").exists())
        assertEquals(listOf("exercise-images"),root.list()!!.toList())
    }
    @Test fun recoveryWithoutPreviousDirectoryRemovesUncommittedPictures() {
        val root=root();val replacement=ImageReplacement(root)
        replacement.prepare(mapOf("lunge" to byteArrayOf(3,4)));replacement.activate()
        ImageReplacement(root).recover(null)
        assertFalse(root.resolve("exercise-images").exists())
    }
}
