package az.healthchecker.explore

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable data class OfflineMapManifest(
    val version: Int = 1,
    val fileName: String = "azerbaijan.map",
    val size: Long,
    val sha256: String,
    val parts: List<String>,
    val source: String = "",
    val date: String = ""
)

object OfflineMapInstaller {
    /** Blocking worker-only installer; the activated filename includes the verified content hash. */
    @Synchronized fun install(directory: File, manifest: OfflineMapManifest, openPart: (String) -> InputStream): File {
        require(manifest.version == 1 && manifest.fileName == "azerbaijan.map")
        require(manifest.size in 1..1_000_000_000L && manifest.sha256.matches(Regex("[a-f0-9]{64}")))
        require(manifest.parts.size in 1..64 && manifest.parts.distinct().size == manifest.parts.size)
        require(manifest.parts.all { it.matches(Regex("maps/azerbaijan\\.map\\.part[0-9]{2}")) })
        check(directory.isDirectory || directory.mkdirs())
        val target = File(directory, "azerbaijan-${manifest.sha256}.map")
        // Verify even an existing copy: a prior interrupted/externally corrupted file is never rendered.
        if (target.isFile && target.length() == manifest.size && digest(target) == manifest.sha256) return target
        val temporary = File(directory, target.name + ".tmp")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var total = 0L
            temporary.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                for (part in manifest.parts) openPart(part).use { input ->
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= manifest.size) { "Bundled map size mismatch" }
                        output.write(buffer, 0, n); hash.update(buffer, 0, n)
                    }
                }
                output.fd.sync()
            }
            require(total == manifest.size && hex(hash.digest()) == manifest.sha256) { "Bundled map checksum mismatch" }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return target
        } finally { temporary.delete() }
    }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
        return hex(hash.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
