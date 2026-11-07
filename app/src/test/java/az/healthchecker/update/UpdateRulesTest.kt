package az.healthchecker.update
import az.healthchecker.domain.AppLanguage
import az.healthchecker.domain.Lang
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class UpdateRulesTest {
    @After fun russianAgain() { Lang.current = AppLanguage.RU }

    private val hash = "ab".repeat(32)
    private fun release(tag: String = "v1.16.6", apk: String = "HealthChecker-update-v1.16.6.apk", host: String = "github.com", digest: String? = "sha256:$hash", extra: String = "") = """
        {"tag_name":"$tag","html_url":"https://github.com/saint-1406/HealthChecker/releases/tag/$tag","body":"Новое\r\n- пункт","draft":false,"prerelease":false,$extra
         "assets":[
          {"name":"HealthChecker-products-v1.16.6.sqlite.gz","browser_download_url":"https://github.com/x/y.gz","size":5},
          {"name":"$apk","browser_download_url":"https://$host/saint-1406/HealthChecker/releases/download/$tag/$apk","size":190964376${digest?.let { ",\"digest\":\"$it\"" } ?: ""}},
          {"name":"$apk.sha256","browser_download_url":"https://github.com/saint-1406/HealthChecker/releases/download/$tag/$apk.sha256","size":97,"digest":null}
         ]}
    """.trimIndent()

    @Test fun latestReleaseGivesItsApkAndChecksumFile() {
        val u = UpdateRules.parseRelease(release())
        assertEquals("1.16.6", u.version)
        assertEquals("HealthChecker-update-v1.16.6.apk", u.apkName)
        assertTrue(u.apkUrl.endsWith("/v1.16.6/HealthChecker-update-v1.16.6.apk"))
        assertTrue(u.checksumUrl.endsWith(".apk.sha256"))
        assertEquals(190964376L, u.size)
        assertEquals(hash, u.githubDigest)
        assertTrue(u.notes.startsWith("Новое"))
    }

    @Test fun releaseWithoutDigestStillWorks() = assertNull(UpdateRules.parseRelease(release(digest = null)).githubDigest)

    @Test fun releaseWithoutTheExpectedFilesIsNoUpdate() {
        assertReason(UpdateFailure.Reason.NO_RELEASE) { UpdateRules.parseRelease(release(apk = "HealthChecker-update-v1.16.5.apk")) }
        assertReason(UpdateFailure.Reason.NO_RELEASE) { UpdateRules.parseRelease(release(tag = "latest-build")) }
        assertReason(UpdateFailure.Reason.NO_RELEASE) { UpdateRules.parseRelease(release(extra = "\"prerelease\":true,").replace("\"prerelease\":false,", "")) }
        assertReason(UpdateFailure.Reason.SERVER) { UpdateRules.parseRelease("<html>rate limited</html>") }
    }

    @Test fun downloadLinksOutsideGithubAreRefused() {
        assertReason(UpdateFailure.Reason.SERVER) { UpdateRules.parseRelease(release(host = "evil.example.com")) }
        assertTrue(UpdateRules.allowedUrl("https://api.github.com/repos/saint-1406/HealthChecker/releases/latest"))
        assertTrue(UpdateRules.allowedUrl("https://objects.githubusercontent.com/a/b?x=1"))
        assertTrue(UpdateRules.allowedUrl("https://release-assets.githubusercontent.com/a"))
        assertFalse(UpdateRules.allowedUrl("http://github.com/x"))
        assertFalse(UpdateRules.allowedUrl("https://github.com.evil.com/x"))
        assertFalse(UpdateRules.allowedUrl("https://user@github.com/x"))
        assertFalse(UpdateRules.allowedUrl("https://github.com:8443/x"))
        assertFalse(UpdateRules.allowedUrl("file:///sdcard/x.apk"))
        assertFalse(UpdateRules.allowedUrl("not a url"))
    }

    @Test fun versionsCompareByNumbers() {
        assertTrue(UpdateRules.newer("1.16.6", "1.16.5"))
        assertTrue(UpdateRules.newer("1.17", "1.16.9"))
        assertTrue(UpdateRules.newer("1.16.10", "1.16.9"))
        assertTrue(UpdateRules.newer("v2.0.0", "1.99"))
        assertFalse(UpdateRules.newer("1.16.5", "1.16.5"))
        assertFalse(UpdateRules.newer("1.16.5.0", "1.16.5"))
        assertFalse(UpdateRules.newer("1.16.4", "1.16.5"))
        assertTrue(UpdateRules.newer("1.17-beta", "1.16.5"))
        assertTrue(UpdateRules.newer("1.16.5", ""))
    }

    @Test fun checksumFileOfTheBuildScriptIsRead() {
        val name = "HealthChecker-update-v1.16.6.apk"
        assertEquals(hash, UpdateRules.parseChecksum("${hash.uppercase()}  $name\r\n", name))
        assertEquals(hash, UpdateRules.parseChecksum("\uFEFF$hash *$name", name))
        assertEquals(hash, UpdateRules.parseChecksum("\n$hash\n", name))
        assertReason(UpdateFailure.Reason.CHECKSUM_FILE) { UpdateRules.parseChecksum("$hash  HealthChecker-update-v1.16.5.apk", name) }
        assertReason(UpdateFailure.Reason.CHECKSUM_FILE) { UpdateRules.parseChecksum("12345  $name", name) }
        assertReason(UpdateFailure.Reason.CHECKSUM_FILE) { UpdateRules.parseChecksum("", name) }
    }

    @Test fun dailyCheckRunsAtMostOnceADayAndOnlyWhenOn() {
        val day = UpdateViewModel.DAY
        assertTrue(UpdateViewModel.dueForCheck(true, 0, 10 * day))
        assertFalse(UpdateViewModel.dueForCheck(true, 10 * day, 10 * day + day - 1))
        assertTrue(UpdateViewModel.dueForCheck(true, 10 * day, 11 * day))
        assertTrue(UpdateViewModel.dueForCheck(true, 10 * day, 9 * day)) // clock set back
        assertFalse(UpdateViewModel.dueForCheck(false, 0, 10 * day))
    }

    @Test fun bannerHidesOnceDismissedAndWhenTheVersionIsInstalled() {
        assertEquals("1.16.6", UpdateState(current = "1.16.5", known = "1.16.6").banner)
        assertNull(UpdateState(current = "1.16.5", known = "1.16.6", dismissed = "1.16.6").banner)
        assertNull(UpdateState(current = "1.16.6", known = "1.16.6").banner)
        assertEquals("1.16.7", UpdateState(current = "1.16.5", known = "1.16.7", dismissed = "1.16.6").banner)
    }

    @Test fun failuresAreExplainedInBothLanguages() {
        val offline = UpdateFailure(UpdateFailure.Reason.OFFLINE)
        assertTrue(UpdateTexts.failure(offline).contains("Нет подключения"))
        assertTrue(UpdateTexts.failure(UpdateFailure(UpdateFailure.Reason.CHECKSUM)).contains("SHA-256"))
        assertTrue(UpdateTexts.failure(UpdateFailure(UpdateFailure.Reason.NO_SPACE, "40")).contains("40 МБ"))
        Lang.current = AppLanguage.EN
        assertTrue(UpdateTexts.failure(offline).contains("No internet"))
        assertTrue(UpdateTexts.failure(UpdateFailure(UpdateFailure.Reason.WRONG_SIGNATURE)).contains("signing key"))
        assertEquals("182.1 MB", UpdateTexts.megabytes(190964376))
        for (reason in UpdateFailure.Reason.entries) assertTrue(reason.name, UpdateTexts.failure(UpdateFailure(reason, "x")).isNotBlank())
    }
}

fun assertReason(reason: UpdateFailure.Reason, block: () -> Unit) {
    try { block(); fail("Expected $reason") } catch (e: UpdateFailure) { assertEquals(e.message, reason, e.reason) }
}
