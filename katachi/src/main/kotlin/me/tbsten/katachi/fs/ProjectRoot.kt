package me.tbsten.katachi.fs

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiCheckException

/**
 * The directory the check treats as the top of the project.
 *
 * It is where the walk starts and what every reported path is relative to. Whether git
 * applies is not decided here: `files = gitTracked()` asks git itself, because a project
 * often sits below the repository root, where no `.git` is found at [path] (see
 * [FileSelection.GitTracked]).
 *
 * ## Example 1: hide one directory below the project root from the check
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.fs.FileSelection
 * import me.tbsten.katachi.fs.FsPath
 * import me.tbsten.katachi.fs.KatachiFileSystem
 * import me.tbsten.katachi.fs.ProjectRoot
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
    override fun toString(): String = "ProjectRoot($path)"
}

/**
 * No project root above the working directory.
 *
 * @property workingDirectory the directory the search started from.
 *
 * ## Example 1: report where the search started
 * ```kt
 * shouldThrow<KatachiProjectRootNotFoundException> { projectArchitecture.assert() }
 *     .workingDirectory.value shouldBe "/repo/app"
 * ```
 */
public class KatachiProjectRootNotFoundException internal constructor(
    public val workingDirectory: FsPath,
) : KatachiCheckException(
    message = "Cannot find the project root above $workingDirectory. " +
        "katachi looks for a Gradle wrapper (gradlew), a Maven wrapper (mvnw) or a git " +
        "directory (.git) in the working directory and in every directory above it.",
)
