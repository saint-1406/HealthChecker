package az.healthchecker.explore

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class OfflineMapInstallerTest {
    @Test fun verifiesPartsBeforeActivationAndKeepsGoodMapWhenUpdateIsCorrupt() {
        val directory = Files.createTempDirectory("offline-map-test").toFile()
        try {
            val good = "mapsforge-map-fixture".toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(good).joinToString("") { "%02x".format(it) }
            val manifest = OfflineMapManifest(size = good.size.toLong(), sha256 = hash, parts = listOf("maps/azerbaijan.map.part00"))
            val file = OfflineMapInstaller.install(directory, manifest) { good.inputStream() }
            assertArrayEquals(good, file.readBytes())
            val badManifest = manifest.copy(sha256 = "0".repeat(64))
            assertTrue(runCatching { OfflineMapInstaller.install(directory, badManifest) { good.inputStream() } }.isFailure)
            assertArrayEquals(good, file.readBytes())
            assertFalse(directory.listFiles()!!.any { it.name.endsWith(".tmp") })
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectsAssetTraversalBeforeOpeningAnyPart() {
        val directory = Files.createTempDirectory("offline-map-test").toFile()
        try {
            val manifest = OfflineMapManifest(size = 5, sha256 = "0".repeat(64), parts = listOf("../outside"))
            var opened = false
            assertTrue(runCatching { OfflineMapInstaller.install(directory, manifest) { opened = true; byteArrayOf().inputStream() } }.isFailure)
            assertFalse(opened)
        } finally { directory.deleteRecursively() }
    }
}
