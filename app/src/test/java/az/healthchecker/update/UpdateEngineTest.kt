package az.healthchecker.update
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

class UpdateEngineTest {
    @get:Rule val temp = TemporaryFolder()

    private val apkBytes = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val apkHash = UpdateEngine.hex(MessageDigest.getInstance("SHA-256").digest(apkBytes))
    private val name = "HealthChecker-update-v1.16.6.apk"
    private val update = AvailableUpdate("1.16.6", name, "https://github.com/a/$name", apkBytes.size.toLong(), "https://github.com/a/$name.sha256", apkHash, "notes", "")

    private class FakeHttp(val texts: Map<String, String>, val files: Map<String, ByteArray>) : UpdateHttp {
        var downloads = 0
        override fun text(url: String, maxBytes: Int) = texts[url] ?: throw UpdateFailure(UpdateFailure.Reason.NO_RELEASE)
        override fun download(url: String, out: OutputStream, onBytes: (ByteArray, Int) -> Unit): Long {
            downloads++
            val bytes = files.getValue(url)
            bytes.toList().chunked(64 * 1024).forEach { c -> val a = c.toByteArray(); out.write(a); onBytes(a, a.size) }
            return bytes.size.toLong()
        }
    }

    private class FakeInspector(var archive: ApkFacts? = ApkFacts("az.healthchecker.app", 22, setOf("key")), val installed: ApkFacts = ApkFacts("az.healthchecker.app", 21, setOf("key"))) : ApkInspector {
        override fun archive(file: File) = archive
        override fun installed() = installed
    }

    private fun engine(sums: String = "${apkHash.uppercase()}  $name", bytes: ByteArray = apkBytes, inspector: ApkInspector = FakeInspector(), free: Long = Long.MAX_VALUE, http: FakeHttp? = null) =
        (http ?: FakeHttp(mapOf(update.checksumUrl to sums), mapOf(update.apkUrl to bytes))).let { UpdateEngine(it, inspector, File(temp.root, "updates")) { free } }

    private fun files() = File(temp.root, "updates").listFiles().orEmpty().map { it.name }

    @Test fun checkedDownloadIsKeptForTheInstaller() {
        val progress = mutableListOf<Long>()
        val file = engine().download(update) { done, _ -> progress += done }
        assertEquals(name, file.name)
        assertArrayEquals(apkBytes, file.readBytes())
        assertEquals(apkBytes.size.toLong(), progress.last())
        assertEquals(listOf(name), files())
    }

    @Test fun aFileThatWasAlreadyDownloadedAndCheckedIsNotDownloadedAgain() {
        val http = FakeHttp(mapOf(update.checksumUrl to "$apkHash  $name"), mapOf(update.apkUrl to apkBytes))
        engine(http = http).download(update) { _, _ -> }
        engine(http = http).download(update) { _, _ -> }
        assertEquals(1, http.downloads)
    }

    @Test fun checksumMismatchDeletesTheFile() {
        val broken = apkBytes.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
        assertReason(UpdateFailure.Reason.CHECKSUM) { engine(bytes = broken).download(update) { _, _ -> } }
        assertEquals(emptyList<String>(), files())
    }

    @Test fun checksumFileDisagreeingWithGithubIsRefusedBeforeDownloading() {
        val http = FakeHttp(mapOf(update.checksumUrl to "${"0".repeat(64)}  $name"), mapOf(update.apkUrl to apkBytes))
        assertReason(UpdateFailure.Reason.CHECKSUM) { engine(http = http).download(update) { _, _ -> } }
        assertEquals(0, http.downloads)
    }

    @Test fun wrongSizeIsRefused() {
        assertReason(UpdateFailure.Reason.SIZE) { engine(bytes = apkBytes.copyOf(apkBytes.size - 10)).download(update) { _, _ -> } }
        assertReason(UpdateFailure.Reason.SIZE) { engine(bytes = apkBytes + ByteArray(10)).download(update) { _, _ -> } }
        assertEquals(emptyList<String>(), files())
    }

    @Test fun anotherAppAnOlderVersionOrAnotherKeyNeverReachTheInstaller() {
        assertReason(UpdateFailure.Reason.NOT_APK) { engine(inspector = FakeInspector(archive = null)).download(update) { _, _ -> } }
        assertReason(UpdateFailure.Reason.WRONG_PACKAGE) { engine(inspector = FakeInspector(ApkFacts("com.other", 22, setOf("key")))).download(update) { _, _ -> } }
        assertReason(UpdateFailure.Reason.NOT_NEWER) { engine(inspector = FakeInspector(ApkFacts("az.healthchecker.app", 21, setOf("key")))).download(update) { _, _ -> } }
        assertReason(UpdateFailure.Reason.WRONG_SIGNATURE) { engine(inspector = FakeInspector(ApkFacts("az.healthchecker.app", 22, setOf("other")))).download(update) { _, _ -> } }
        assertEquals(emptyList<String>(), files())
    }

    @Test fun keysThePhoneCannotReadAreLeftToTheInstallerAndRotatedKeysPass() {
        engine(inspector = FakeInspector(ApkFacts("az.healthchecker.app", 22, null))).download(update) { _, _ -> }
        engine(inspector = FakeInspector(ApkFacts("az.healthchecker.app", 23, setOf("new", "key")))).download(update.copy()) { _, _ -> }
    }

    @Test fun notEnoughSpaceStopsBeforeDownloading() {
        val http = FakeHttp(mapOf(update.checksumUrl to "$apkHash  $name"), mapOf(update.apkUrl to apkBytes))
        try { engine(http = http, free = 1000).download(update) { _, _ -> }; fail() } catch (e: UpdateFailure) {
            assertEquals(UpdateFailure.Reason.NO_SPACE, e.reason)
            assertEquals("33", e.detail) // 300 000 bytes + 32 MB spare − 1000 free, rounded up to whole MB
        }
        assertEquals(0, http.downloads)
    }

    @Test fun cancellingDuringTheDownloadLeavesNothingBehind() {
        try { engine().download(update) { done, _ -> if (done > 100_000) throw kotlinx.coroutines.CancellationException("cancel") }; fail() } catch (_: kotlinx.coroutines.CancellationException) {}
        assertEquals(emptyList<String>(), files())
    }

    @Test fun checkTellsWhetherTheLatestReleaseIsNewer() {
        val json = """{"tag_name":"v1.16.6","assets":[{"name":"$name","browser_download_url":"https://github.com/a/$name","size":10},{"name":"$name.sha256","browser_download_url":"https://github.com/a/$name.sha256","size":1}]}"""
        val http = FakeHttp(mapOf(UpdateRules.LATEST to json), emptyMap())
        assertTrue(engine(http = http).check("1.16.5").newer)
        assertFalse(engine(http = http).check("1.16.6").newer)
    }

    @Test fun installedUpdatesAndBrokenDownloadsAreCleanedUp() {
        val dir = File(temp.root, "updates").apply { mkdirs() }
        listOf("HealthChecker-update-v1.16.5.apk", "HealthChecker-update-v1.16.6.apk", "HealthChecker-update-v1.16.7.apk.part").forEach { File(dir, it).writeText("x") }
        engine().cleanUp("1.16.5")
        assertEquals(listOf("HealthChecker-update-v1.16.6.apk"), files())
    }
}
