package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.internal.GitProblem
import me.tbsten.katachi.internal.fileUri

/**
 * git could not answer which files belong to the project.
 *
 * Thrown under `files = gitTracked()` when the project is in a git repository (a `.git` at the
 * project root or above it) but git cannot be run there, refuses the repository, or fails to
 * list its files. The message carries what git said. The check stops rather than walking every
 * file on disk, which would give other results than a run where git works.
 *
 * ## Example 1: fall back to the whole tree when git is unavailable
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiGitUnavailableException) {
 *     println("`${cause.command}` failed in ${cause.root}")
 * }
 * ```
 *
 * @property command the command line that was run.
 * @property root the directory it was run in.
 */
public class KatachiGitUnavailableException internal constructor(
    public val command: String,
    public val root: FsPath,
    internal val problem: GitProblem,
    cause: Throwable? = null,
) : KatachiCheckException(problem.explain(command, root), cause)

/**
 * No project root above the working directory.
 *
 * ## Example 1: report where the search started
 * ```kt
 * shouldThrow<KatachiProjectRootNotFoundException> { projectArchitecture.assert() }
 *     .workingDirectory.value shouldBe "/repo/app"
 * ```
 *
 * @property workingDirectory the directory the search started from.
 */
public class KatachiProjectRootNotFoundException internal constructor(
    public val workingDirectory: FsPath,
) : KatachiCheckException(
    message = "Cannot find the project root above ${fileUri(workingDirectory.value)}. " +
        "katachi looks for a Gradle wrapper (gradlew), a Maven wrapper (mvnw) or a git " +
        "directory (.git) in the working directory and in every directory above it.",
)

/**
 * `-Dkatachi.projectRoot` does not name a directory.
 *
 * A mistyped path would otherwise check an empty tree and report no violations, which looks like
 * a pass. The check stops instead.
 *
 * ## Example 1: report which path was rejected
 * ```kt
 * shouldThrow<KatachiInvalidProjectRootException> { projectArchitecture.assert() }
 *     .path.value shouldBe "/tmp/old"
 * ```
 *
 * @property path the directory `katachi.projectRoot` resolved to.
 * @property exists whether something other than a directory sits at `path`.
 */
public class KatachiInvalidProjectRootException internal constructor(
    public val path: FsPath,
    public val exists: Boolean,
) : KatachiCheckException(
    message = "-Dkatachi.projectRoot points at ${fileUri(path.value)}, which is " +
        (if (exists) "not a directory (a symbolic link is not followed either)" else "not found") + ". " +
        "Give the path of the directory to check, or drop -Dkatachi.projectRoot to look for the " +
        "project root from the working directory.",
)
