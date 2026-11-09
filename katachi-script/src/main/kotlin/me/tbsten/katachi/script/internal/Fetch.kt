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

/** Where the guide, the templates and the docs are served from unless `KATACHI_DOCS` says otherwise. */
internal const val KATACHI_DOCS_DEFAULT: String = "https://tbsten.github.io/katachi"

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

/**
 * The docs origin: `KATACHI_DOCS` > what `init` recorded in the working directory > the real site.
 * `init` records it so that a later `docs` does not quietly go back to the real site.
 */
internal fun ScriptContext.docsBase(): String {
    env["KATACHI_DOCS"]?.takeIf { it.isNotEmpty() }?.let { return it }
    val recorded = findWorkdir()?.resolve("tmp/cache/docs")
    if (recorded != null && recorded.exists() && recorded.fileSize() > 0) return recorded.readText()
    return KATACHI_DOCS_DEFAULT
}

/** Downloads [url] to [dest], or stops with what to do by hand. The kit's `download`. */
internal fun ScriptContext.download(url: String, dest: Path) {
    val body = http.fetch(url, Duration.ofSeconds(10), Duration.ofSeconds(120), retries = 2)
        ?: die(Messages.Fetch.downloadFailedCheckYour(url, fileUri(dest)))
    dest.parent?.createDirectories()
    val tmp = dest.resolveSibling("${dest.name}.part.${ProcessHandle.current().pid()}")
    tmp.writeBytes(body)
    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING)
}

/** Fetches [url] into [dest] unless it is already there (or [refresh]), and notes it in the MANIFEST. */
internal fun ScriptContext.fetchOnce(url: String, dest: Path, refresh: Boolean, manifest: Path?) {
    if (!refresh && dest.exists() && dest.fileSize() > 0) return
    download(url, dest)
    recordInManifest(manifest, url, dest)
}

/** One line per file the command put in the working directory, so a person can tell later what came from where. */
internal fun ScriptContext.recordInManifest(manifest: Path?, source: String, dest: Path) {
    if (manifest == null) return
    manifest.parent?.createDirectories()
    val now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").format(clock.instant().atOffset(ZoneOffset.UTC))
    if (!manifest.exists()) manifest.writeText("")
    manifest.appendText("$now\t$source\t${dest.fileSize()} bytes\t$dest\n")
}

/** The newest katachi release on GitHub without its `v`, or null when it cannot be had quickly. */
internal fun ScriptContext.fetchLatestVersion(): String? {
    val body = http.fetch(KATACHI_RELEASES_API, Duration.ofSeconds(5), Duration.ofSeconds(15), retries = 1) ?: return null
    val match = Regex("\"tag_name\"\\s*:\\s*\"v?([^\"]*)\"").find(body.toString(Charsets.UTF_8)) ?: return null
    return match.groupValues[1].ifEmpty { null }
}
