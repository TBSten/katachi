package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiCheckException

/**
 * A kind of file that marks the top of a project. Same set as Konsist's root providers, so a
 * project that works with Konsist works with katachi without extra configuration.
 */
@InternalKatachiApi
public enum class ProjectRootMarker(internal val markerPaths: List<String>) {
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
    Git(listOf(".git/config", ".git/HEAD", ".git/refs")),
}

/**
 * The directory the check treats as the top of the project, together with **every** marker
 * found there.
 *
 * All the markers are reported, not just the first one, because the caller needs more than
 * the location: `files = gitTracked()` behaves differently depending on whether the root is
 * a git repository at all (see [FileSelection]).
 */
@InternalKatachiApi
public class ProjectRoot internal constructor(
    /** The root directory itself. */
    public val path: FsPath,
    /** Every marker kind present at [path], never empty. */
    public val markers: Set<ProjectRootMarker>,
) {
    /** Whether [path] carries git's own marker files. */
    public val isGitRepository: Boolean get() = ProjectRootMarker.Git in markers

    override fun toString(): String =
        "ProjectRoot($path, markers=${markers.map { it.name }.sorted()})"
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
 * Walks up from the working directory and returns the first directory carrying a marker.
 *
 * There is no way to pass a root in explicitly. A test that wants a different root passes a
 * different [KatachiFileSystem], which keeps the search itself under test rather than
 * bypassed.
 *
 * @throws KatachiProjectRootNotFoundException when no parent carries any marker.
 */
@InternalKatachiApi
public fun findProjectRoot(fileSystem: KatachiFileSystem): ProjectRoot {
    var current: FsPath? = fileSystem.workingDirectory
    while (current != null) {
        val directory = current
        val markers = ProjectRootMarker.entries.filterTo(linkedSetOf()) { marker ->
            marker.markerPaths.any { fileSystem.exists(directory / it) }
        }
        if (markers.isNotEmpty()) return ProjectRoot(directory, markers)
        current = directory.parent
    }
    throw KatachiProjectRootNotFoundException(fileSystem.workingDirectory)
}
