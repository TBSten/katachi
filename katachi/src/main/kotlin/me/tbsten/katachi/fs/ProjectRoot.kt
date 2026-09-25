package me.tbsten.katachi.fs

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.KatachiCheckException

/**
 * A kind of file that marks the top of a project. Same set as Konsist's root providers, so a
 * project that works with Konsist works with katachi without extra configuration.
 *
 * Only which directory carries one matters: [findProjectRoot] stops at the first directory
 * that carries any of them, whichever kind it is.
 */
internal enum class ProjectRootMarker(val markerPaths: List<String>) {
    Gradle(
        listOf(
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
        ),
    ),
    Maven(
        listOf(
            "mvnw",
            "mvnw.cmd",
            ".mvn/wrapper/maven-wrapper.jar",
            ".mvn/wrapper/maven-wrapper.properties",
        ),
    ),
    // `.git` itself is listed alongside the three files inside it, because in a worktree and in
    // a submodule `.git` is a *file* holding `gitdir: <path>` and none of the three exist. Without
    // this entry, katachi run from a worktree would not stop at a repository root -- which a
    // subagent working in `.claude/worktrees/` hits every time. `exists` answers for a file and a
    // directory alike, so the one entry covers both shapes.
    Git(listOf(".git", ".git/config", ".git/HEAD", ".git/refs")),
}

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

/**
 * Walks up from the working directory and returns the first directory carrying a marker: a
 * Gradle wrapper, a Maven wrapper or git's own files.
 *
 * There is no way to pass a root in explicitly. A test that wants a different root passes a
 * different [KatachiFileSystem], which keeps the search itself under test rather than
 * bypassed.
 *
 * ## Example 1: find the project root from a file system
 * ```kt
 * val fileSystem = RealFileSystem()
 * val root = findProjectRoot(fileSystem)
 * println("Checking the project at ${root.path}")
 * ```
 *
 * @throws KatachiProjectRootNotFoundException when no parent carries any marker.
 */
@InternalKatachiApi
public fun findProjectRoot(fileSystem: KatachiFileSystem): ProjectRoot {
    var current: FsPath? = fileSystem.workingDirectory
    while (current != null) {
        val directory = current
        val carriesMarker = ProjectRootMarker.entries.any { marker ->
            marker.markerPaths.any { fileSystem.exists(directory / it) }
        }
        if (carriesMarker) return ProjectRoot(directory)
        current = directory.parent
    }
    throw KatachiProjectRootNotFoundException(fileSystem.workingDirectory)
}
