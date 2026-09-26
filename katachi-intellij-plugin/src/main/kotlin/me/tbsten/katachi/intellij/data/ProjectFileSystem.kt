package me.tbsten.katachi.intellij.data

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** The disk as the data layer reads it. Faked in tests; [NioProjectFileSystem] in the plugin. */
internal interface ProjectFileSystem {
    /** The file's text as UTF-8, or `null` when it does not exist (or cannot be read). */
    fun readText(path: Path): String?

    /** The file's modification time, or `null` when it does not exist. */
    fun lastModified(path: Path): Instant?

    fun exists(path: Path): Boolean
}

/** [ProjectFileSystem] over `java.nio`. The VFS is not needed: Gradle writes behind the IDE's back anyway. */
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
