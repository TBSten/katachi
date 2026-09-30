package me.tbsten.katachi.dsl.files

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * The directory the check treats as the top of the project.
 *
 * It is where the walk starts and what every reported path is relative to. Whether git
 * applies is not decided here: `files = gitTracked()` asks git itself, because a project
 * often sits below the repository root, where no `.git` is found at [path][ProjectRoot.path] (see
 * [me.tbsten.katachi.dsl.FileSelection.GitTracked]).
 *
 * ## Example 1: hide one directory below the project root from the check
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.dsl.FileSelection
 * import me.tbsten.katachi.dsl.files.FsPath
 * import me.tbsten.katachi.dsl.files.KatachiFileSystem
 * import me.tbsten.katachi.dsl.files.ProjectRoot
 *
 * // Hides the project's `generated/` directory from the check, and nothing else.
 * @OptIn(ExperimentalKatachiApi::class)
 * object WithoutGenerated : FileSelection {
 *     override fun fileSystemFor(
 *         delegate: KatachiFileSystem,
 *         projectRoot: ProjectRoot,
 *     ): KatachiFileSystem = object : KatachiFileSystem by delegate {
 *         override fun list(directory: FsPath): List<FsPath> =
 *             delegate.list(directory).filterNot { it == projectRoot.path / "generated" }
 *     }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class ProjectRoot internal constructor(
    /** The root directory itself. */
    public val path: FsPath,
) {
    /** `ProjectRoot(<path>)`. */
    override fun toString(): String = "ProjectRoot($path)"
}
