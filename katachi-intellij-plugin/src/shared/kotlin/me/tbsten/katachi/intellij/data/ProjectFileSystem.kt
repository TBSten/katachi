package me.tbsten.katachi.intellij.data

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal interface ProjectFileSystem {
    fun readText(path: Path): String?

    fun lastModified(path: Path): Instant?

    fun exists(path: Path): Boolean
}

internal object NioProjectFileSystem : ProjectFileSystem {
    override fun readText(path: Path): String? = try {
        if (Files.isRegularFile(path)) String(Files.readAllBytes(path), StandardCharsets.UTF_8) else null
    } catch (_: IOException) {
        null
    }

    override fun lastModified(path: Path): Instant? = try {
        if (Files.exists(path)) Files.getLastModifiedTime(path).toInstant() else null
    } catch (_: IOException) {
        null
    }

    override fun exists(path: Path): Boolean = Files.exists(path)
}
