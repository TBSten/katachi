package me.tbsten.katachi.dsl.files.internal

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.dsl.KatachiGitUnavailableException
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.internal.fileUri

internal sealed interface GitProblem {
    fun explain(command: String, root: FsPath): String

    object CannotStart : GitProblem {
        override fun explain(command: String, root: FsPath): String =
            "Cannot run `$command` in ${fileUri(root.value)}. katachi checks the files git " +
                "reports for this project; set `files = wholeTree()` in `architecture { }` to walk the " +
                "whole directory tree instead."
    }

    class TimedOut(
        val seconds: Long,
    ) : GitProblem {
        override fun explain(command: String, root: FsPath): String =
            "`$command` in ${fileUri(root.value)} did not finish within $seconds seconds."
    }

    class Failed(
        val exitCode: Int,
        val stderr: String,
    ) : GitProblem {
        override fun explain(command: String, root: FsPath): String =
            "`$command` in ${fileUri(root.value)} exited with $exitCode: $stderr"
    }
}

internal class GitTrackedFileSystem(
    private val delegate: KatachiFileSystem,
    private val root: FsPath,
    trackedPaths: Collection<String>,
) : KatachiFileSystem, OutsideProjectDirectories {
    // Kept in NFC and looked up in NFC: unless core.precomposeunicode is true, git on macOS prints a name as the
    // disk spells it (NFD when Finder or a Mac zip wrote it), while the JDK lists it in NFC.
    private val visibleFiles: Set<FsPath>
    private val visibleDirectories: Set<FsPath>

    init {
        val nfcRoot = root.toNfc()
        val files = mutableSetOf<FsPath>()
        val directories = mutableSetOf(nfcRoot)
        for (relative in trackedPaths) {
            val path = (root / relative).toNfc()
            if (path == nfcRoot) continue
            files += path
            // Every directory in the set already has all of its ancestors up to [root] in it
            // ([root] is there from the start), so the walk up stops at the first one seen.
            var parent = path.parent
            while (parent != null && parent.startsWith(nfcRoot)) {
                if (!directories.add(parent)) break
                parent = parent.parent
            }
        }
        visibleFiles = files
        visibleDirectories = directories
    }

    override val workingDirectory: FsPath get() = delegate.workingDirectory

    override fun exists(path: FsPath): Boolean =
        (!isFiltered(path) || isVisible(path)) && delegate.exists(path)

    override fun isDirectory(path: FsPath): Boolean =
        (!isFiltered(path) || path.toNfc() in visibleDirectories) && delegate.isDirectory(path)

    override fun list(directory: FsPath): List<FsPath> =
        delegate.list(directory).filter { !isFiltered(it) || isVisible(it) }

    // A path git printed as one entry — a submodule, a nested repository — sits in
    // [visibleFiles] rather than [visibleDirectories], and is not outside the project.
    override fun holdsNothingOfTheProject(directory: FsPath): Boolean =
        isFiltered(directory) && !isVisible(directory)

    override fun toString(): String =
        "GitTrackedFileSystem($root, files=${visibleFiles.size})"

    private fun isFiltered(path: FsPath): Boolean = path != root && path.startsWith(root)

    private fun isVisible(path: FsPath): Boolean {
        val key = path.toNfc()
        return key in visibleFiles || key in visibleDirectories
    }

    // An NFC path (any ASCII one) is returned as is, so the usual lookup builds no new string.
    private fun FsPath.toNfc(): FsPath =
        if (java.text.Normalizer.isNormalized(value, java.text.Normalizer.Form.NFC)) {
            this
        } else {
            FsPath.of(java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC))
        }
}

// The module search runs on the unfiltered tree; it asks this to skip directories the view drops wholesale
// (node_modules etc.).
internal interface OutsideProjectDirectories {
    fun holdsNothingOfTheProject(directory: FsPath): Boolean
}

private val GIT_LS_FILES: List<String> = listOf(
    "git",
    "ls-files",
    "--cached",
    "--others",
    "--exclude-standard",
    "-z",
)

// Ask git rather than look for `.git` at root: a project may sit below the repository root. Failures after git
// claims the root are reported by gitLsFiles.
internal fun isInsideGitWorkTree(root: FsPath): Boolean =
    runGit(root, "rev-parse", "--is-inside-work-tree")?.trim() == "true"

// git runs once at the root and the answer is kept as a set.
internal fun gitTrackedFileSystem(delegate: KatachiFileSystem, root: FsPath): KatachiFileSystem =
    GitTrackedFileSystem(delegate, root, gitLsFiles(root))

// `--others --exclude-standard` includes untracked, non-ignored files; `-z` keeps unusual file names unquoted.
private fun gitLsFiles(root: FsPath): List<String> {
    val commandLine = GIT_LS_FILES.joinToString(" ")
    val process = try {
        ProcessBuilder(GIT_LS_FILES).directory(File(root.value)).start()
    } catch (e: IOException) {
        throw KatachiGitUnavailableException(commandLine, root, GitProblem.CannotStart, e)
    }

    // stderr is drained on its own thread: a process that fills the error pipe while we are
    // still reading stdout would otherwise block forever.
    val errorOutput = StringBuilder()
    val errorDrain = Thread {
        runCatching { process.errorStream.use { errorOutput.append(it.readBytes().toString(Charsets.UTF_8)) } }
    }
    errorDrain.isDaemon = true
    errorDrain.start()

    val output = process.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
    if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw KatachiGitUnavailableException(
            commandLine,
            root,
            GitProblem.TimedOut(GIT_TIMEOUT_SECONDS),
        )
    }
    errorDrain.join(TimeUnit.SECONDS.toMillis(1))

    val exitCode = process.exitValue()
    if (exitCode != 0) {
        throw KatachiGitUnavailableException(
            commandLine,
            root,
            GitProblem.Failed(exitCode, errorOutput.toString().trim()),
        )
    }
    return output.split('\u0000').filter { it.isNotEmpty() }
}
