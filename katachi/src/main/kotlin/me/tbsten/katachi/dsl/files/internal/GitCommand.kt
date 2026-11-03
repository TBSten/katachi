package me.tbsten.katachi.dsl.files.internal

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.dsl.files.FsPath

internal const val GIT_TIMEOUT_SECONDS: Long = 60L

// stderr is discarded: an undrained pipe would block git. Callers needing stderr (gitLsFiles) drive the process
// themselves.
internal fun runGit(root: FsPath, vararg args: String): String? {
    val process = try {
        ProcessBuilder(listOf("git", *args))
            .directory(File(root.value))
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (_: IOException) {
        return null
    }
    val output = process.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
    if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return null
    }
    return if (process.exitValue() == 0) output else null
}
