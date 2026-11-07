package az.healthchecker.update
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

class GithubHttpTest {
    /** One canned answer per URL; [opened] records every address the updater tried. */
    private class Server(val answers: Map<String, Triple<Int, Map<String, String>, ByteArray>>) {
        val opened = mutableListOf<String>()
        val headers = mutableMapOf<String, String>()
        fun open(url: URL): HttpURLConnection {
            opened += url.toString()
            val (status, head, body) = answers[url.toString()] ?: throw UnknownHostException(url.host)
            return object : HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = status
                override fun getHeaderField(name: String?) = head[name]
                override fun getInputStream(): InputStream = ByteArrayInputStream(body)
                override fun setRequestProperty(key: String?, value: String?) { headers[key!!] = value!! }
            }
        }
    }

    private fun ok(text: String) = Triple(200, emptyMap<String, String>(), text.toByteArray())

    @Test fun apiAnswerIsReadWithGithubHeaders() {
        val server = Server(mapOf(UpdateRules.LATEST to ok("{}")))
        assertEquals("{}", GithubHttp(server::open).text(UpdateRules.LATEST, 100))
        assertEquals("application/vnd.github+json", server.headers["Accept"])
        assertTrue(server.headers.getValue("User-Agent").startsWith("HealthChecker"))
    }

    @Test fun downloadFollowsGithubRedirectsToItsFileStorage() {
        val start = "https://github.com/saint-1406/HealthChecker/releases/download/v1/a.apk"
        val storage = "https://release-assets.githubusercontent.com/github-production-release-asset/1?sig=x"
        val server = Server(mapOf(start to Triple(302, mapOf("Location" to storage), ByteArray(0)), storage to ok("APK")))
        val out = ByteArrayOutputStream()
        assertEquals(3L, GithubHttp(server::open).download(start, out) { _, _ -> })
        assertEquals("APK", out.toString())
        assertEquals(listOf(start, storage), server.opened)
    }

    @Test fun redirectsAwayFromGithubOrToPlainHttpAreRefused() {
        val start = "https://github.com/a.apk"
        for (target in listOf("https://evil.example.com/a.apk", "http://objects.githubusercontent.com/a.apk")) {
            val server = Server(mapOf(start to Triple(302, mapOf("Location" to target), ByteArray(0)), target to ok("APK")))
            assertReason(UpdateFailure.Reason.SERVER) { GithubHttp(server::open).download(start, ByteArrayOutputStream()) { _, _ -> } }
            assertEquals(listOf(start), server.opened)
        }
    }

    @Test fun endlessRedirectsStop() {
        val a = "https://github.com/a"; val b = "https://github.com/b"
        val server = Server(mapOf(a to Triple(302, mapOf("Location" to b), ByteArray(0)), b to Triple(302, mapOf("Location" to a), ByteArray(0))))
        assertReason(UpdateFailure.Reason.SERVER) { GithubHttp(server::open).text(a, 100) }
    }

    @Test fun statusCodesBecomeClearReasons() {
        fun answer(status: Int, head: Map<String, String> = emptyMap()) = Server(mapOf(UpdateRules.LATEST to Triple(status, head, ByteArray(0))))
        assertReason(UpdateFailure.Reason.NO_RELEASE) { GithubHttp(answer(404)::open).text(UpdateRules.LATEST, 100) }
        assertReason(UpdateFailure.Reason.RATE_LIMITED) { GithubHttp(answer(403, mapOf("X-RateLimit-Remaining" to "0"))::open).text(UpdateRules.LATEST, 100) }
        assertReason(UpdateFailure.Reason.RATE_LIMITED) { GithubHttp(answer(429)::open).text(UpdateRules.LATEST, 100) }
        assertReason(UpdateFailure.Reason.SERVER) { GithubHttp(answer(500)::open).text(UpdateRules.LATEST, 100) }
    }

    @Test fun noNetworkIsReportedAsOffline() {
        assertReason(UpdateFailure.Reason.OFFLINE) { GithubHttp(Server(emptyMap())::open).text(UpdateRules.LATEST, 100) }
    }

    @Test fun nonGithubAddressesAreNeverOpened() {
        val server = Server(mapOf("https://example.com/x" to ok("x")))
        assertReason(UpdateFailure.Reason.SERVER) { GithubHttp(server::open).text("https://example.com/x", 100) }
        assertEquals(emptyList<String>(), server.opened)
    }

    @Test fun oversizedTextAnswersAreRefused() {
        val server = Server(mapOf(UpdateRules.LATEST to ok("x".repeat(200))))
        assertReason(UpdateFailure.Reason.SERVER) { GithubHttp(server::open).text(UpdateRules.LATEST, 100) }
    }
}
