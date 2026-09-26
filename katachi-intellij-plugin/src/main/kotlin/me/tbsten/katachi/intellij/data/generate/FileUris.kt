package me.tbsten.katachi.intellij.data.generate

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A `file:///…` URI from katachi's output as an absolute path; `%20` and percent-encoded Japanese
 * decode here (E-26). `null` when it is not a usable `file:` URI.
 */
internal fun pathOfFileUri(uri: String): Path? = try {
    val parsed = URI(uri.trim())
    if (parsed.scheme != "file") null else Paths.get(parsed)
} catch (_: URISyntaxException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: FileSystemNotFoundException) {
    null
}

/**
 * The URIs of a `, ` separated list. A URI encodes its spaces as `%20`, so the separator never
 * occurs inside one.
 */
internal fun splitUriList(text: String): List<String> = text.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
