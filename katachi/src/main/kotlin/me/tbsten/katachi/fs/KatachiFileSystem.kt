package me.tbsten.katachi.fs

import me.tbsten.katachi.InternalKatachiApi

/**
 * Every file system access katachi makes, in four operations.
 *
 * The surface is deliberately this small. There is **no operation that reads a file's
 * contents**: katachi itself only ever looks at the structure of the tree — which names sit
 * in which directory. Reading contents is the job of `katachi-konsist`, which needs real
 * files anyway.
 *
 * Keeping it to four operations is what makes the in-memory implementation used by katachi's
 * own tests cheap, and it is also what lets a filtered view ([GitTrackedFileSystem]) wrap a
 * real tree without the traversal above it knowing.
 *
 * ## Example 1: a minimal implementation of the four operations
 * ```kt
 * val fileSystem = object : KatachiFileSystem {
 *     override val workingDirectory: FsPath = FsPath.of("/repo/app")
 *     override fun exists(path: FsPath): Boolean = path == workingDirectory
 *     override fun isDirectory(path: FsPath): Boolean = path == workingDirectory
 *     override fun list(directory: FsPath): List<FsPath> =
 *         if (directory == workingDirectory) listOf(directory / "build.gradle.kts") else emptyList()
 * }
 * ```
 */
@InternalKatachiApi
public interface KatachiFileSystem {
    /**
     * Where the check starts looking for the project root. Always absolute.
     *
     * ## Example 1: read where a file system starts looking
     * ```kt
     * val fileSystem = RealFileSystem()
     * println("Searching from ${fileSystem.workingDirectory}")
     * ```
     */
    public val workingDirectory: FsPath

    /**
     * Whether anything — file or directory — sits at [path].
     *
     * ## Example 1: check whether a marker file sits at a path
     * ```kt
     * val fileSystem = RealFileSystem()
     * val hasSettings = fileSystem.exists(fileSystem.workingDirectory / "settings.gradle.kts")
     * ```
     */
    public fun exists(path: FsPath): Boolean

    /**
     * Whether [path] is a directory the traversal may descend into. A symbolic link is never
     * a directory, so the traversal cannot be sent around a cycle.
     *
     * ## Example 1: decide whether to descend into a path
     * ```kt
     * val fileSystem = RealFileSystem()
     * val src = fileSystem.workingDirectory / "src"
     * if (fileSystem.isDirectory(src)) fileSystem.list(src)
     * ```
     */
    public fun isDirectory(path: FsPath): Boolean

    /**
     * The direct children of [directory], **sorted by name**, so that the order of a report
     * does not depend on the OS. Empty when [directory] is not a directory.
     *
     * ## Example 1: read a directory's children, sorted by name
     * ```kt
     * val fileSystem = RealFileSystem()
     * val names = fileSystem.list(fileSystem.workingDirectory).map { it.name }
     * ```
     */
    public fun list(directory: FsPath): List<FsPath>
}
