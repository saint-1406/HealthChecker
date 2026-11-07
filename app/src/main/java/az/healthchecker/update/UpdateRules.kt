package az.healthchecker.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale

/**
 * Updating HealthChecker from inside the app, like ATLAS does: the newest GitHub release is asked for, its full APK is
 * downloaded into the app's cache and checked (size, the published .sha256 file, GitHub's own digest, the same package,
 * a higher versionCode and the same signing key). Only a file that passes all of that goes to Android's installer,
 * which puts the new version over the old one: the diary, findings and walks stay.
 *
 * The network is used only here, only for the hosts below and only when the user checks or the once-a-day check runs.
 */
object UpdateRules {
    const val REPOSITORY = "saint-1406/HealthChecker"
    const val LATEST = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    /** GitHub's API, its release pages and the storage its download links redirect to. Nothing else is ever opened. */
    val HOSTS = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
    /** Room kept free besides the file itself, so the phone is not filled to the brim. */
    const val SPARE_BYTES = 32L * 1024 * 1024
    const val MAX_APK_BYTES = 1024L * 1024 * 1024

    fun apkName(version: String) = "HealthChecker-update-v$version.apk"

    fun allowedUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && uri.host?.lowercase(Locale.ROOT) in HOSTS && uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    /** "1.16.6" newer than "1.16.5"; a part that is not a number counts as 0 ("1.17-beta" → 1.17). */
    fun newer(candidate: String, current: String): Boolean {
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
    private fun parts(version: String) = version.trim().removePrefix("v").removePrefix("V").split('.').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** The answer of [LATEST]: the version, the APK and its .sha256 file. */
    fun parseRelease(text: String): AvailableUpdate {
        val release = runCatching { json.decodeFromString(GithubRelease.serializer(), text) }.getOrElse { throw UpdateFailure(UpdateFailure.Reason.SERVER, "JSON") }
        if (release.draft || release.prerelease) throw UpdateFailure(UpdateFailure.Reason.NO_RELEASE)
        val version = release.tag.trim().removePrefix("v").removePrefix("V")
        if (!Regex("[0-9]+(\\.[0-9]+){0,3}").matches(version)) throw UpdateFailure(UpdateFailure.Reason.NO_RELEASE, release.tag)
        val name = apkName(version)
        val apk = release.assets.firstOrNull { it.name == name } ?: throw UpdateFailure(UpdateFailure.Reason.NO_RELEASE, name)
        val sums = release.assets.firstOrNull { it.name == "$name.sha256" } ?: throw UpdateFailure(UpdateFailure.Reason.NO_RELEASE, "$name.sha256")
        if (!allowedUrl(apk.url) || !allowedUrl(sums.url)) throw UpdateFailure(UpdateFailure.Reason.SERVER, "URL")
        if (apk.size <= 0 || apk.size > MAX_APK_BYTES) throw UpdateFailure(UpdateFailure.Reason.SERVER, "size ${apk.size}")
        val digest = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(Locale.ROOT)?.takeIf(::isSha256)
        return AvailableUpdate(version, name, apk.url, apk.size, sums.url, digest, release.body.orEmpty().trim(), release.page)
    }

    /** "1B2C…  HealthChecker-update-v1.16.5.apk" as written by scripts/build-apk.ps1 (also with a BOM, CRLF or "*name"). */
    fun parseChecksum(text: String, expectedName: String): String {
        val line = text.removePrefix("\uFEFF").lineSequence().map(String::trim).firstOrNull(String::isNotEmpty) ?: throw UpdateFailure(UpdateFailure.Reason.CHECKSUM_FILE)
        val fields = line.split(Regex("\\s+"))
        val hash = fields[0].lowercase(Locale.ROOT)
        if (!isSha256(hash)) throw UpdateFailure(UpdateFailure.Reason.CHECKSUM_FILE)
        val named = fields.getOrNull(1)?.removePrefix("*")
        if (named != null && named != expectedName) throw UpdateFailure(UpdateFailure.Reason.CHECKSUM_FILE, named)
        return hash
    }

    fun isSha256(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}

/** The newest release, as far as the check could tell; [githubDigest] is GitHub's own SHA-256 of the APK, when it lists one. */
data class AvailableUpdate(val version: String, val apkName: String, val apkUrl: String, val size: Long, val checksumUrl: String,
    val githubDigest: String?, val notes: String, val page: String)

/** Why checking, downloading or installing stopped; the UI turns [reason] into a sentence (UpdateTexts). */
class UpdateFailure(val reason: Reason, val detail: String = "") : Exception("$reason $detail".trim()) {
    enum class Reason { OFFLINE, RATE_LIMITED, NO_RELEASE, SERVER, NO_SPACE, INTERRUPTED, CHECKSUM_FILE, CHECKSUM, SIZE, NOT_APK, WRONG_PACKAGE, NOT_NEWER, WRONG_SIGNATURE, INSTALLER }
}

@Serializable internal data class GithubAsset(val name: String = "", @SerialName("browser_download_url") val url: String = "", val size: Long = 0, val digest: String? = null)
@Serializable internal data class GithubRelease(@SerialName("tag_name") val tag: String = "", val body: String? = null, @SerialName("html_url") val page: String = "",
    val draft: Boolean = false, val prerelease: Boolean = false, val assets: List<GithubAsset> = emptyList())
