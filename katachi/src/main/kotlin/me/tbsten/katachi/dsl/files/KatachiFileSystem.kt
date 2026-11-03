package me.tbsten.katachi.dsl.files

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Every file system access katachi makes, in four operations.
 *
 * There is **no operation that reads a file's contents**: katachi only looks at which names sit
 * in which directory. Because the surface is this small, a filtered view such as
 * `files = gitTracked()` or a [me.tbsten.katachi.dsl.FileSelection] of your own can wrap a real
 * tree.
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
@ExperimentalKatachiApi
public interface KatachiFileSystem {
    /**
     * Where the check starts looking for the project root. Always absolute.
     */
    public val workingDirectory: FsPath

    /**
     * Whether anything — file or directory — sits at [path].
     */
    public fun exists(path: FsPath): Boolean

    /**
     * Whether [path] is a directory the traversal may descend into. A symbolic link is never
     * a directory, so the traversal cannot be sent around a cycle.
     */
    public fun isDirectory(path: FsPath): Boolean

    /**
     * The direct children of [directory], **sorted by name**, so that the order of a report
     * does not depend on the OS.
     *
     * Empty when [directory] is not a directory.
     */
    public fun list(directory: FsPath): List<FsPath>
}
