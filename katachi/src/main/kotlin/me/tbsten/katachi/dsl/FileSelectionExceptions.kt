package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.internal.GitProblem
import me.tbsten.katachi.internal.fileUri

/**
 * git could not answer which files belong to the project.
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
