package me.tbsten.katachi.fs

import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.fs.internal.GitProblem

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
    internal val problem: GitProblem,
    cause: Throwable? = null,
) : KatachiCheckException(problem.explain(command, root), cause)
