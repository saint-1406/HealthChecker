package az.healthchecker.update
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** The only way the updater reaches the network. */
interface UpdateHttp {
    /** A small text answer (the release JSON, a .sha256 file) of at most [maxBytes]. */
    fun text(url: String, maxBytes: Int): String
    /** Streams a file into [out]; [onBytes] gets each chunk and may throw to stop. Returns the bytes copied. */
    fun download(url: String, out: OutputStream, onBytes: (ByteArray, Int) -> Unit): Long
}

/**
 * HTTPS to GitHub only. Redirects are followed by hand so that every hop is checked against [UpdateRules.HOSTS]: a
 * download link may move to GitHub's file storage but never to any other host, and never to plain HTTP.
 */
class GithubHttp(private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) : UpdateHttp {
    override fun text(url: String, maxBytes: Int): String = request(url, api = url.startsWith("https://api.github.com/")) { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer); if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > maxBytes) throw UpdateFailure(UpdateFailure.Reason.SERVER, "too long")
        }
        out.toString(Charsets.UTF_8.name())
    }

    override fun download(url: String, out: OutputStream, onBytes: (ByteArray, Int) -> Unit): Long = request(url, api = false) { input ->
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        while (true) {
            val n = try { input.read(buffer) } catch (e: IOException) { throw UpdateFailure(UpdateFailure.Reason.INTERRUPTED, e.message ?: e.javaClass.simpleName) }
            if (n < 0) break
            out.write(buffer, 0, n)
            onBytes(buffer, n)
            done += n
        }
        done
    }

    private fun <T> request(start: String, api: Boolean, read: (InputStream) -> T): T {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            if (!UpdateRules.allowedUrl(url)) throw UpdateFailure(UpdateFailure.Reason.SERVER, "host")
            val connection = try { open(URL(url)) } catch (e: IOException) { throw networkFailure(e) }
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "HealthChecker-Android-Updater")
                if (api) {
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                val status = connection.responseCode
                when {
                    status in 300..399 -> {
                        val location = connection.getHeaderField("Location") ?: throw UpdateFailure(UpdateFailure.Reason.SERVER, "HTTP $status")
                        url = URL(URL(url), location).toString()
                    }
                    status in 200..299 -> return connection.inputStream.use(read)
                    status == 404 -> throw UpdateFailure(UpdateFailure.Reason.NO_RELEASE, "HTTP 404")
                    status == 429 || (status == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0") -> throw UpdateFailure(UpdateFailure.Reason.RATE_LIMITED)
                    else -> throw UpdateFailure(UpdateFailure.Reason.SERVER, "HTTP $status")
                }
            } catch (e: UpdateFailure) {
                throw e
            } catch (e: IOException) {
                throw networkFailure(e)
            } finally {
                connection.disconnect()
            }
        }
        throw UpdateFailure(UpdateFailure.Reason.SERVER, "redirects")
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        /** No route, no name lookup, no answer: the phone is offline or the network blocks GitHub. */
        fun networkFailure(e: IOException): UpdateFailure = when (e) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketTimeoutException -> UpdateFailure(UpdateFailure.Reason.OFFLINE, e.javaClass.simpleName)
            is SSLException -> UpdateFailure(UpdateFailure.Reason.SERVER, "TLS")
            else -> UpdateFailure(UpdateFailure.Reason.INTERRUPTED, e.message ?: e.javaClass.simpleName)
        }
    }
}
