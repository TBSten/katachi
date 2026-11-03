package me.tbsten.katachi.intellij.data.generate

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Path
import java.nio.file.Paths

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

internal fun splitUriList(text: String): List<String> = text.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
