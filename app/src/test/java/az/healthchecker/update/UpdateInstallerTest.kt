package az.healthchecker.update
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class UpdateInstallerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun installerGetsTheDownloadedFileThroughTheUpdatesProvider() {
        // FileProvider matches paths by "/", so on a Windows build host Robolectric's "\" paths never match; phones are POSIX.
        org.junit.Assume.assumeTrue(java.io.File.separatorChar == '/')
        val file = UpdateInstaller.folder(context).apply { mkdirs() }.resolve("HealthChecker-update-v1.16.6.apk").apply { writeText("apk") }
        val intent = UpdateInstaller.installIntent(context, file)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(UpdateInstaller.APK_TYPE, intent.type)
        assertEquals("content", intent.data!!.scheme)
        assertEquals(context.packageName + ".updates", intent.data!!.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("apk", context.contentResolver.openInputStream(intent.data!!)!!.use { it.readBytes().decodeToString() })
    }

    @Test fun filesOutsideTheUpdatesFolderAreNotShared() {
        val other = context.filesDir.resolve("diary.db").apply { writeText("x") }
        assertThrows(IllegalArgumentException::class.java) { UpdateInstaller.installIntent(context, other) }
    }

    @Test fun permissionPageIsTheOneForThisApp() {
        val intent = UpdateInstaller.permissionIntent(context)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:" + context.packageName, intent.dataString)
    }

    @Test fun manifestRequestsOnlyTheUpdaterNetworkPermissionsAndNoBackgroundLocation() {
        val requested = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue(android.Manifest.permission.INTERNET in requested)
        assertTrue(android.Manifest.permission.REQUEST_INSTALL_PACKAGES in requested)
        assertFalse(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION in requested)
        assertFalse(android.Manifest.permission.ACCESS_NETWORK_STATE in requested)
    }
}
