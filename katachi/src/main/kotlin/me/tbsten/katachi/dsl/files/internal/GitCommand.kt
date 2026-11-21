package me.tbsten.katachi.dsl.files.internal

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.catching

internal const val GIT_TIMEOUT_SECONDS: Long = 60L

internal const val GIT: String = "git"

// A git hook (pre-commit, pre-push, `git bisect run`) runs with GIT_DIR set. In a linked worktree
// that is `<main>/.git/worktrees/<name>`, and with no GIT_WORK_TREE git takes the current directory
// as the top of the work tree: a Gradle root below the top then loses every .gitignore above it.
// Name the work tree from the gitdir's own `gitdir` file, the way git does for the worktree itself.
internal fun ProcessBuilder.withGitWorkTree(): ProcessBuilder {
    val env = environment()
    if (env["GIT_WORK_TREE"] != null) return this
    val gitDir = env["GIT_DIR"]?.let(::File) ?: return this
    val pointer = File(gitDir, "gitdir").takeIf { it.isFile } ?: return this
    val dotGit = runCatching { File(pointer.readText().trim()) }.getOrNull() ?: return this
    dotGit.parentFile?.takeIf { dotGit.name == ".git" }?.let { env["GIT_WORK_TREE"] = it.path }
    return this
}

// stderr is discarded: an undrained pipe would block git. Callers needing stderr use runGitCapturing.
internal fun runGit(root: FsPath, vararg args: String): String? {
    val process = try {
        ProcessBuilder(listOf(GIT, *args))
            .withGitWorkTree()
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

internal sealed interface GitRun {
    class Exited(
        val exitCode: Int,
        val output: String,
        val errorOutput: String,
    ) : GitRun

    class CannotStart(
        val cause: IOException,
    ) : GitRun

    object TimedOut : GitRun
}

internal fun runGitCapturing(root: FsPath, command: List<String>): GitRun {
    val process = try {
        ProcessBuilder(command).withGitWorkTree().directory(File(root.value)).start()
    } catch (e: IOException) {
        return GitRun.CannotStart(e)
    }

    // stderr is drained on its own thread: a process that fills the error pipe while we are
    // still reading stdout would otherwise block forever.
    val errorOutput = StringBuilder()
    val errorDrain = Thread {
        catching { process.errorStream.use { errorOutput.append(it.readBytes().toString(Charsets.UTF_8)) } }
    }
    errorDrain.isDaemon = true
    errorDrain.start()

    val output = process.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
    if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return GitRun.TimedOut
    }
    errorDrain.join(TimeUnit.SECONDS.toMillis(1))
    return GitRun.Exited(process.exitValue(), output, errorOutput.toString().trim())
}
