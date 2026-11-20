package me.tbsten.katachi.dsl.files.internal

import java.io.File
import me.tbsten.katachi.dsl.KatachiGitUnavailableException
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.internal.fileUri

// `dotGit` is the `.git` at or above the root that made an answer from git required. It is there when git gave no
// answer at all, and null once git has accepted the root.
internal sealed interface GitProblem {
    fun explain(command: String, root: FsPath): String

    class CannotStart(
        val dotGit: FsPath? = null,
    ) : GitProblem {
        override fun explain(command: String, root: FsPath): String {
            if (dotGit == null) {
                return "Cannot run `$command` in ${fileUri(root.value)}. katachi checks the files git " +
                    "reports for this project; set `files = wholeTree()` in `architecture { }` to walk the " +
                    "whole directory tree instead."
            }
            return buildString {
                appendLine(
                    "Cannot run `$command` in ${fileUri(root.value)} , although ${fileUri(dotGit.value)} exists.",
                )
                appendLine(GIT_REQUIRED)
                append("Install git, or put it on the PATH the check runs with; or $WHOLE_TREE_INSTEAD")
            }
        }
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
        val dotGit: FsPath? = null,
    ) : GitProblem {
        override fun explain(command: String, root: FsPath): String {
            if (dotGit == null) return "`$command` in ${fileUri(root.value)} exited with $exitCode: $stderr"
            return buildString {
                appendLine(
                    "`$command` in ${fileUri(root.value)} exited with $exitCode, although ${fileUri(dotGit.value)} exists.",
                )
                if (stderr.isNotEmpty()) {
                    appendLine("git said:")
                    for (line in stderr.lines()) appendLine("  $line".trimEnd())
                }
                appendLine(GIT_REQUIRED)
                val fix = if (stderr.isEmpty()) "Run `$command` there to see why" else "Fix what git reports"
                append("$fix, or $WHOLE_TREE_INSTEAD")
            }
        }
    }
}

private const val GIT_REQUIRED: String =
    "With a .git at the project root or above it, katachi takes the project's files from git, and does not " +
        "fall back to every file on disk: that would check build output and ignored files too, and give other " +
        "results than a run where git works."

private const val WHOLE_TREE_INSTEAD: String =
    "set `files = wholeTree()` in `architecture { }` to check every file on disk instead."

internal class GitTrackedFileSystem(
    private val delegate: KatachiFileSystem,
    private val root: FsPath,
    trackedPaths: Collection<String>,
) : KatachiFileSystem, OutsideProjectDirectories {
    private val visibleFiles: Set<FsPath>
    private val visibleDirectories: Set<FsPath>

    init {
        val files = mutableSetOf<FsPath>()
        val directories = mutableSetOf(root)
        for (relative in trackedPaths) {
            val path = root / relative
            if (path == root) continue
            files += path
            // Every directory in the set already has all of its ancestors up to [root] in it
            // ([root] is there from the start), so the walk up stops at the first one seen.
            var parent = path.parent
            while (parent != null && parent.startsWith(root)) {
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
        (!isFiltered(path) || path in visibleDirectories) && delegate.isDirectory(path)

    override fun list(directory: FsPath): List<FsPath> =
        delegate.list(directory).filter { !isFiltered(it) || isVisible(it) }

    // A path git printed as one entry — a submodule, a nested repository — sits in
    // [visibleFiles] rather than [visibleDirectories], and is not outside the project.
    override fun holdsNothingOfTheProject(directory: FsPath): Boolean =
        isFiltered(directory) && !isVisible(directory)

    override fun toString(): String =
        "GitTrackedFileSystem($root, files=${visibleFiles.size})"

    private fun isFiltered(path: FsPath): Boolean = path != root && path.startsWith(root)

    private fun isVisible(path: FsPath): Boolean =
        path in visibleFiles || path in visibleDirectories
}

// The module search runs on the unfiltered tree; it asks this to skip directories the view drops wholesale
// (node_modules etc.).
internal interface OutsideProjectDirectories {
    fun holdsNothingOfTheProject(directory: FsPath): Boolean
}

private val GIT_LS_FILES: List<String> = listOf(
    GIT,
    "ls-files",
    "--cached",
    "--others",
    "--exclude-standard",
    "-z",
)

// Ask git rather than look for `.git` at root: a project may sit below the repository root. A `.git` at or above
// root only decides what git failing means. Without one the project is in no repository and the whole tree is its
// own; with one, walking the whole tree would check other files than a run where git works, so this throws.
// Failures after git claims the root are reported by gitLsFiles. Tests replace [git], the executable.
internal fun isInsideGitWorkTree(root: FsPath, git: String = GIT): Boolean {
    // A root that is not on the disk (an in-memory tree) has no repository for git to answer about.
    if (!File(root.value).isDirectory) return false
    val command = listOf(git, "rev-parse", "--is-inside-work-tree")
    val run = runGitCapturing(root, command)
    if (run is GitRun.Exited && run.exitCode == 0) return run.output.trim() == "true"
    val dotGit = dotGitAtOrAbove(root) ?: return false
    val commandLine = command.joinToString(" ")
    throw when (run) {
        is GitRun.Exited ->
            KatachiGitUnavailableException(commandLine, root, GitProblem.Failed(run.exitCode, run.errorOutput, dotGit))
        is GitRun.CannotStart ->
            KatachiGitUnavailableException(commandLine, root, GitProblem.CannotStart(dotGit), run.cause)
        GitRun.TimedOut ->
            KatachiGitUnavailableException(commandLine, root, GitProblem.TimedOut(GIT_TIMEOUT_SECONDS))
    }
}

// Looked for on the disk, where git looks, rather than in the checked file system. `exists` answers for the
// directory of a repository and for the `gitdir:` file of a worktree or a submodule alike.
private fun dotGitAtOrAbove(root: FsPath): FsPath? =
    generateSequence(root) { it.parent }.map { it / ".git" }.firstOrNull { File(it.value).exists() }

// git runs once at the root and the answer is kept as a set.
internal fun gitTrackedFileSystem(delegate: KatachiFileSystem, root: FsPath): KatachiFileSystem =
    GitTrackedFileSystem(delegate, root, gitLsFiles(root))

// `--others --exclude-standard` includes untracked, non-ignored files; `-z` keeps unusual file names unquoted.
private fun gitLsFiles(root: FsPath): List<String> {
    val commandLine = GIT_LS_FILES.joinToString(" ")
    val output = when (val run = runGitCapturing(root, GIT_LS_FILES)) {
        is GitRun.CannotStart ->
            throw KatachiGitUnavailableException(commandLine, root, GitProblem.CannotStart(), run.cause)
        GitRun.TimedOut ->
            throw KatachiGitUnavailableException(commandLine, root, GitProblem.TimedOut(GIT_TIMEOUT_SECONDS))
        is GitRun.Exited -> {
            if (run.exitCode != 0) {
                throw KatachiGitUnavailableException(commandLine, root, GitProblem.Failed(run.exitCode, run.errorOutput))
            }
            run.output
        }
    }
    return output.split('\u0000').filter { it.isNotEmpty() }
}
