package me.tbsten.katachi.fs

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.KatachiCheckException

/**
 * How the git invocation went wrong.
 *
 * One subtype per way it can, each holding what its own sentence needs. Internal bookkeeping:
 * the set grows with whatever katachi asks git for.
 *
 * ## Example 1: only fall back to the whole tree when git itself is missing
 * ```kt
 * import me.tbsten.katachi.InternalKatachiApi
 * import me.tbsten.katachi.fs.FsPath
 * import me.tbsten.katachi.fs.GitProblem
 * import me.tbsten.katachi.fs.KatachiFileSystem
 * import me.tbsten.katachi.fs.KatachiGitUnavailableException
 * import me.tbsten.katachi.fs.gitTrackedFileSystem
 *
 * @OptIn(InternalKatachiApi::class)
 * fun sourcesOf(delegate: KatachiFileSystem, root: FsPath): KatachiFileSystem =
 *     try {
 *         gitTrackedFileSystem(delegate, root)
 *     } catch (cause: KatachiGitUnavailableException) {
 *         // A real failure — a timeout, or git exiting non-zero — should not be swallowed
 *         // the same way as "git is not installed".
 *         if (cause.problem is GitProblem.CannotStart) delegate else throw cause
 *     }
 * ```
 */
@InternalKatachiApi
public sealed interface GitProblem {
    /**
     * The sentence this problem contributes, for the [command] that was run in [root].
     *
     * ## Example 1: rebuild the sentence a caught exception already carries
     * ```kt
     * import me.tbsten.katachi.InternalKatachiApi
     * import me.tbsten.katachi.fs.KatachiGitUnavailableException
     *
     * @OptIn(InternalKatachiApi::class)
     * fun describe(cause: KatachiGitUnavailableException): String =
     *     cause.problem.explain(cause.command, cause.root)
     * ```
     */
    public fun explain(command: String, root: FsPath): String

    /**
     * The process could not be started at all — usually git is not installed.
     *
     * ## Example 1: tell the developer to install git
     * ```kt
     * import me.tbsten.katachi.InternalKatachiApi
     * import me.tbsten.katachi.fs.GitProblem
     * import me.tbsten.katachi.fs.KatachiGitUnavailableException
     *
     * @OptIn(InternalKatachiApi::class)
     * fun installHint(cause: KatachiGitUnavailableException): String? =
     *     if (cause.problem is GitProblem.CannotStart) "Install git and re-run the check." else null
     * ```
     */
    @InternalKatachiApi
    public object CannotStart : GitProblem {
        override fun explain(command: String, root: FsPath): String =
            "Cannot run `$command` in $root. katachi checks the files git reports for " +
                "this project; set `files = wholeTree()` in `architecture { }` to walk the " +
                "whole directory tree instead."
    }

    /**
     * The process started but did not finish in time.
     *
     * ## Example 1: report how long katachi waited before giving up
     * ```kt
     * import me.tbsten.katachi.InternalKatachiApi
     * import me.tbsten.katachi.fs.GitProblem
     * import me.tbsten.katachi.fs.KatachiGitUnavailableException
     *
     * @OptIn(InternalKatachiApi::class)
     * fun timeoutMessage(cause: KatachiGitUnavailableException): String? {
     *     val problem = cause.problem as? GitProblem.TimedOut ?: return null
     *     return "git did not answer within ${problem.seconds}s"
     * }
     * ```
     */
    @InternalKatachiApi
    public class TimedOut internal constructor(
        public val seconds: Long,
    ) : GitProblem {
        override fun explain(command: String, root: FsPath): String =
            "`$command` in $root did not finish within $seconds seconds."
    }

    /**
     * The process finished, with a non-zero exit code.
     *
     * ## Example 1: surface git's own stderr when the command itself failed
     * ```kt
     * import me.tbsten.katachi.InternalKatachiApi
     * import me.tbsten.katachi.fs.GitProblem
     * import me.tbsten.katachi.fs.KatachiGitUnavailableException
     *
     * @OptIn(InternalKatachiApi::class)
     * fun failureMessage(cause: KatachiGitUnavailableException): String? {
     *     val problem = cause.problem as? GitProblem.Failed ?: return null
     *     return "git exited ${problem.exitCode}: ${problem.stderr}"
     * }
     * ```
     */
    @InternalKatachiApi
    public class Failed internal constructor(
        public val exitCode: Int,
        public val stderr: String,
    ) : GitProblem {
        override fun explain(command: String, root: FsPath): String =
            "`$command` in $root exited with $exitCode: $stderr"
    }
}

/**
 * git could not answer which files belong to the project.
 *
 * @property command the command line that was run.
 * @property root the directory it was run in.
 *
 * ## Example 1: fall back to the whole tree when git is unavailable
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiGitUnavailableException) {
 *     println("`${cause.command}` failed in ${cause.root}")
 * }
 * ```
 */
public class KatachiGitUnavailableException internal constructor(
    public val command: String,
    public val root: FsPath,
    @property:InternalKatachiApi public val problem: GitProblem,
    cause: Throwable? = null,
) : KatachiCheckException(problem.explain(command, root), cause)

/**
 * A view of [delegate] that only shows the files git considers part of the project.
 *
 * A directory is visible when at least one visible file sits somewhere below it, so an
 * entirely ignored directory such as `build/` disappears rather than showing up empty.
 *
 * Paths outside [root] are passed through untouched: the filter is about what belongs to the
 * project, and nothing outside the project is part of that question.
 *
 * ## Example 1: reuse one `git ls-files` run across several checks
 * ```kt
 * import me.tbsten.katachi.InternalKatachiApi
 * import me.tbsten.katachi.fs.FsPath
 * import me.tbsten.katachi.fs.GitTrackedFileSystem
 * import me.tbsten.katachi.fs.KatachiFileSystem
 *
 * // `gitTrackedFileSystem` runs git itself; this reuses a list already fetched once, so a
 * // second check against the same tree does not spawn git again.
 * @OptIn(InternalKatachiApi::class)
 * fun viewFor(delegate: KatachiFileSystem, root: FsPath, trackedPaths: List<String>): KatachiFileSystem =
 *     GitTrackedFileSystem(delegate, root, trackedPaths)
 * ```
 *
 * @param trackedPaths paths relative to [root], `/` separated, as `git ls-files` prints them.
 */
@InternalKatachiApi
public class GitTrackedFileSystem(
    private val delegate: KatachiFileSystem,
    private val root: FsPath,
    trackedPaths: Collection<String>,
) : KatachiFileSystem {
    private val visibleFiles: Set<FsPath>
    private val visibleDirectories: Set<FsPath>

    init {
        val files = mutableSetOf<FsPath>()
        val directories = mutableSetOf(root)
        for (relative in trackedPaths) {
            val path = root / relative
            if (path == root) continue
            files += path
            var parent = path.parent
            while (parent != null && parent.startsWith(root)) {
                directories += parent
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

    override fun toString(): String =
        "GitTrackedFileSystem($root, files=${visibleFiles.size})"

    private fun isFiltered(path: FsPath): Boolean = path != root && path.startsWith(root)

    private fun isVisible(path: FsPath): Boolean =
        path in visibleFiles || path in visibleDirectories
}

/** How long to wait for `git ls-files` before giving up. */
private const val GIT_TIMEOUT_SECONDS: Long = 60L

private val GIT_LS_FILES: List<String> = listOf(
    "git",
    "ls-files",
    "--cached",
    "--others",
    "--exclude-standard",
    "-z",
)

private val GIT_INSIDE_WORK_TREE: List<String> = listOf("git", "rev-parse", "--is-inside-work-tree")

/**
 * Asks git whether [root] is inside a work tree.
 *
 * This is the question that decides whether [FileSelection.GitTracked] applies, and it is
 * deliberately asked of git rather than answered by looking for a `.git` entry at [root].
 * A Gradle project frequently sits below the repository root — katachi's own samples do,
 * and so does any build inside a monorepo — and there `.git` is further up while
 * `git ls-files` run at [root] still reports exactly that subtree.
 *
 * Returns `false` when git is missing or says no, which makes the check fall back to
 * walking the whole tree. A failure *after* git has claimed the root is a different matter
 * and is reported: see [gitLsFiles].
 */
internal fun isInsideGitWorkTree(root: FsPath): Boolean {
    val process = try {
        ProcessBuilder(GIT_INSIDE_WORK_TREE)
            .directory(File(root.value))
            .redirectErrorStream(true)
            .start()
    } catch (_: IOException) {
        return false
    }
    val output = process.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
    if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return false
    }
    return process.exitValue() == 0 && output.trim() == "true"
}

/**
 * Wraps [delegate] so that only the files git reports for [root] are visible.
 *
 * git is run **once**, at the root, and the answer is kept as a set. The traversal above
 * this never spawns a process.
 *
 * ## Example 1: build a FileSelection that always uses git-tracked files
 * ```kt
 * import me.tbsten.katachi.InternalKatachiApi
 * import me.tbsten.katachi.fs.FileSelection
 * import me.tbsten.katachi.fs.KatachiFileSystem
 * import me.tbsten.katachi.fs.ProjectRoot
 * import me.tbsten.katachi.fs.gitTrackedFileSystem
 *
 * @OptIn(InternalKatachiApi::class)
 * object AlwaysGitTracked : FileSelection {
 *     override fun fileSystemFor(
 *         delegate: KatachiFileSystem,
 *         projectRoot: ProjectRoot,
 *     ): KatachiFileSystem = gitTrackedFileSystem(delegate, projectRoot.path)
 * }
 * ```
 *
 * @throws KatachiGitUnavailableException when git cannot be run or fails.
 */
@InternalKatachiApi
public fun gitTrackedFileSystem(delegate: KatachiFileSystem, root: FsPath): KatachiFileSystem =
    GitTrackedFileSystem(delegate, root, gitLsFiles(root))

/**
 * Runs `git ls-files --cached --others --exclude-standard -z` in [root].
 *
 * `--cached` is what git tracks, `--others --exclude-standard` adds files that are not
 * tracked yet but are not ignored either — a file gets checked the moment it is written,
 * without waiting for a `git add`. `-z` keeps names with spaces or non-ASCII characters
 * intact, which git would otherwise quote.
 */
internal fun gitLsFiles(root: FsPath): List<String> {
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
