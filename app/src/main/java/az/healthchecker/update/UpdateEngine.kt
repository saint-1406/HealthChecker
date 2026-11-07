package az.healthchecker.update
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** What Android says about an APK: its package, versionCode and the SHA-256 of its signing certificates (null: unknown). */
data class ApkFacts(val packageName: String, val versionCode: Long, val signers: Set<String>?)

interface ApkInspector {
    /** The downloaded file; null when it is not an Android app. */
    fun archive(file: File): ApkFacts?
    /** This installed app. */
    fun installed(): ApkFacts
}

/** The result of a check: the newest release and whether it is newer than [current]. */
data class UpdateCheck(val current: String, val update: AvailableUpdate, val newer: Boolean)

/**
 * Checking, downloading and verifying, with no Android UI in it. [dir] is the app's cache/updates folder (shared with the
 * installer through UpdateFileProvider); [freeBytes] tells how much room is left there.
 */
class UpdateEngine(private val http: UpdateHttp, private val inspector: ApkInspector, private val dir: File, private val freeBytes: () -> Long) {

    fun check(currentVersion: String): UpdateCheck {
        val update = UpdateRules.parseRelease(http.text(UpdateRules.LATEST, 2 * 1024 * 1024))
        return UpdateCheck(currentVersion, update, UpdateRules.newer(update.version, currentVersion))
    }

    /** A file that was downloaded before and passes every check again, e.g. after the user came back from the settings. */
    fun ready(update: AvailableUpdate, expectedSha256: String): File? {
        val file = File(dir, update.apkName)
        if (!file.isFile || file.length() != update.size) return null
        return if (runCatching { sha256(file) == expectedSha256 && run { verifyPackage(file); true } }.getOrDefault(false)) file else null
    }

    /** The published SHA-256 of the APK, from its .sha256 file and, when GitHub lists one, its own digest. */
    fun expectedSha256(update: AvailableUpdate): String {
        val published = UpdateRules.parseChecksum(http.text(update.checksumUrl, 4096), update.apkName)
        if (update.githubDigest != null && update.githubDigest != published) throw UpdateFailure(UpdateFailure.Reason.CHECKSUM, "GitHub digest")
        return published
    }

    /**
     * Downloads the APK into [dir] and checks it. A file that fails any check is deleted before the failure is thrown,
     * so nothing broken or foreign ever reaches the installer. [progress] gets (done, total) and may throw to cancel.
     */
    fun download(update: AvailableUpdate, progress: (Long, Long) -> Unit): File {
        val expected = expectedSha256(update)
        ready(update, expected)?.let { return it }
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // only one update is kept at a time
        val needed = update.size + UpdateRules.SPARE_BYTES
        val free = freeBytes()
        if (free < needed) throw UpdateFailure(UpdateFailure.Reason.NO_SPACE, ((needed - free + MB - 1) / MB).toString())
        val file = File(dir, update.apkName)
        val part = File(dir, update.apkName + ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            val copied = FileOutputStream(part).use { out ->
                http.download(update.apkUrl, out) { chunk, n ->
                    digest.update(chunk, 0, n)
                    done += n
                    if (done > update.size) throw UpdateFailure(UpdateFailure.Reason.SIZE, "$done / ${update.size}")
                    progress(done, update.size)
                }
            }
            if (copied != update.size || part.length() != update.size) throw UpdateFailure(UpdateFailure.Reason.SIZE, "${part.length()} / ${update.size}")
            if (hex(digest.digest()) != expected) throw UpdateFailure(UpdateFailure.Reason.CHECKSUM)
            verifyPackage(part)
            if (!part.renameTo(file)) throw UpdateFailure(UpdateFailure.Reason.INTERRUPTED, "rename")
            return file
        } catch (e: Throwable) {
            part.delete(); file.delete()
            throw e
        }
    }

    /** The same app, a higher versionCode and a signing key in common with this install, or Android would refuse it. */
    fun verifyPackage(file: File) {
        val theirs = runCatching { inspector.archive(file) }.getOrNull() ?: throw UpdateFailure(UpdateFailure.Reason.NOT_APK)
        val mine = inspector.installed()
        if (theirs.packageName != mine.packageName) throw UpdateFailure(UpdateFailure.Reason.WRONG_PACKAGE, theirs.packageName)
        if (theirs.versionCode <= mine.versionCode) throw UpdateFailure(UpdateFailure.Reason.NOT_NEWER, theirs.versionCode.toString())
        // Where the phone cannot read the key from a file, Android's installer checks it itself.
        if (theirs.signers != null && mine.signers != null && theirs.signers.intersect(mine.signers).isEmpty()) throw UpdateFailure(UpdateFailure.Reason.WRONG_SIGNATURE)
    }

    /** Leftovers of an update that is installed by now (or of an interrupted download) are removed. */
    fun cleanUp(currentVersion: String) {
        dir.listFiles()?.forEach { f ->
            val version = f.name.removePrefix("HealthChecker-update-v").removeSuffix(".part").removeSuffix(".apk")
            if (f.name.endsWith(".part") || !UpdateRules.newer(version, currentVersion)) f.delete()
        }
    }

    companion object {
        const val MB = 1024L * 1024
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return hex(digest.digest())
        }
        fun hex(bytes: ByteArray) = bytes.joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    }
}
