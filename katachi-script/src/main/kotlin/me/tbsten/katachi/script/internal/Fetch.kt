package me.tbsten.katachi.script.internal

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

internal const val KATACHI_RELEASES_API: String = "https://api.github.com/repos/TBSten/katachi/releases/latest"

internal const val KATACHI_RELEASES_PAGE: String = "https://github.com/TBSten/katachi/releases"

/** Reads a URL. `file://` is read from disk, so a built docs site can stand in for the real one. */
internal fun interface HttpFetcher {
    /** The body, or null when it could not be fetched in time. */
    fun fetch(url: String, connectTimeout: Duration, totalTimeout: Duration, retries: Int): ByteArray?
}

internal object JdkHttpFetcher : HttpFetcher {
    override fun fetch(url: String, connectTimeout: Duration, totalTimeout: Duration, retries: Int): ByteArray? {
        if (url.startsWith("file://")) {
            val path = Path(URI(url).path)
            return if (Files.isRegularFile(path)) Files.readAllBytes(path) else null
        }
        val client = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(totalTimeout)
            .header("User-Agent", "katachi-cli")
            .GET()
            .build()
        repeat(retries + 1) { attempt ->
            val body = try {
                val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                response.body().takeIf { response.statusCode() in 200..299 }
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            if (body != null) return body
            if (attempt < retries) Thread.sleep(2000)
        }
        return null
    }
}

/** The newest katachi release on GitHub without its `v`, or null when it cannot be had quickly. */
internal fun ScriptContext.fetchLatestVersion(): String? {
    val body = http.fetch(KATACHI_RELEASES_API, Duration.ofSeconds(5), Duration.ofSeconds(15), retries = 1) ?: return null
    val match = Regex("\"tag_name\"\\s*:\\s*\"v?([^\"]*)\"").find(body.toString(Charsets.UTF_8)) ?: return null
    return match.groupValues[1].ifEmpty { null }
}
