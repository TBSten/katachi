package me.tbsten.katachi.dsl.files.internal

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.dsl.files.FsPath

/** How long to wait for any git command katachi runs before giving up. */
internal const val GIT_TIMEOUT_SECONDS: Long = 60L

/**
 * Runs `git <args>` in [root] and returns what it printed to standard output.
 *
 * `null` when git cannot be started (not installed), does not finish within
 * [GIT_TIMEOUT_SECONDS], or exits non-zero -- the three ways a question to git goes unanswered.
 * Standard error is discarded rather than read: a failing git prints there, and a pipe nobody
 * drains would block the process. A caller that needs stderr for its own message (`gitLsFiles`)
 * drives the process itself.
 */
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
