package me.tbsten.katachi.dsl.files.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiProjectRootNotFoundException
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.ProjectRoot

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
