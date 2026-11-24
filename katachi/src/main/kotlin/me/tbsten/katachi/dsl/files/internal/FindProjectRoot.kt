package me.tbsten.katachi.dsl.files.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiInvalidProjectRootException
import me.tbsten.katachi.dsl.KatachiProjectRootNotFoundException
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.ProjectRoot

internal const val PROJECT_ROOT_PROPERTY: String = "katachi.projectRoot"

// Same set as Konsist's root providers, so a project that works with Konsist works with katachi.
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
 * Returns the directory the check treats as the project root.
 *
 * By default it walks up from the working directory and returns the first directory carrying a
 * marker: a Gradle wrapper, a Maven wrapper or git's own files.
 *
 * `-Dkatachi.projectRoot=<directory>` names the root instead, and no marker is looked for in it:
 * an old checkout of the project has the markers, but a bare copy of a source tree does not, and
 * both are valid things to check. A relative value is resolved against the working directory.
 * A blank value is the same as no value.
 *
 * ## Example 1: find the project root from a file system
 * ```kt
 * val fileSystem = RealFileSystem()
 * val root = findProjectRoot(fileSystem)
 * println("Checking the project at ${root.path}")
 * ```
 *
 * ## Example 2: check another tree with the current definition
 * ```kt
 * val root = findProjectRoot(fileSystem) { if (it == "katachi.projectRoot") "/tmp/old" else null }
 * ```
 *
 * @param systemProperty reads a JVM system property, `null` when it is not set.
 * @throws KatachiProjectRootNotFoundException when no parent carries any marker.
 * @throws KatachiInvalidProjectRootException when `katachi.projectRoot` is not an existing directory.
 */
@InternalKatachiApi
public fun findProjectRoot(
    fileSystem: KatachiFileSystem,
    systemProperty: (String) -> String? = System::getProperty,
): ProjectRoot {
    val given = systemProperty(PROJECT_ROOT_PROPERTY)?.trim().orEmpty()
    if (given.isNotEmpty()) return explicitProjectRoot(fileSystem, given)
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

private fun explicitProjectRoot(fileSystem: KatachiFileSystem, given: String): ProjectRoot {
    val parsed = FsPath.of(given)
    val path = if (parsed.isAbsolute) parsed else fileSystem.workingDirectory / given
    if (!fileSystem.isDirectory(path)) {
        throw KatachiInvalidProjectRootException(path, exists = fileSystem.exists(path))
    }
    return ProjectRoot(path)
}
