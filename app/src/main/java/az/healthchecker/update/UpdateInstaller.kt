package az.healthchecker.update
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/** Lends the downloaded update (cache/updates/) to Android's installer, and nothing else; its own class so it can sit next to the photo provider. */
class UpdateFileProvider : FileProvider()

object UpdateInstaller {
    const val APK_TYPE = "application/vnd.android.package-archive"
    fun authority(context: Context) = context.packageName + ".updates"
    fun folder(context: Context) = File(context.cacheDir, "updates")
    fun freeBytes(context: Context): Long = runCatching { folder(context).apply { mkdirs() }.let { StatFs(it.path).availableBytes } }.getOrDefault(Long.MAX_VALUE)

    /** Android 8+: the app may hand a file to the installer only once the user allowed "install unknown apps" for it. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))

    fun installIntent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(context, authority(context), file), APK_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** Reads package name, versionCode and signing certificates through PackageManager. */
class PackageApkInspector(private val context: Context) : ApkInspector {
    private val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    override fun archive(file: File): ApkFacts? = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)?.let(::facts)

    override fun installed(): ApkFacts = facts(context.packageManager.getPackageInfo(context.packageName, flags))

    @Suppress("DEPRECATION")
    private fun facts(info: PackageInfo): ApkFacts {
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.let { s -> if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory } else info.signatures
        // A rotated key keeps the old one in its history, so one certificate in common is enough.
        val signers = signatures?.takeIf { it.isNotEmpty() }?.map { UpdateEngine.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet()
        return ApkFacts(info.packageName, code, signers)
    }
}
