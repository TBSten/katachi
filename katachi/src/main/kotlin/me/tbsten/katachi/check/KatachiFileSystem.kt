package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.InternalKatachiApi

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
 */
@InternalKatachiApi
public interface KatachiFileSystem {
    /** Where the check starts looking for the project root. Always absolute. */
    public val workingDirectory: FsPath

    /** Whether anything — file or directory — sits at [path]. */
    public fun exists(path: FsPath): Boolean

    /**
     * Whether [path] is a directory the traversal may descend into. A symbolic link is never
     * a directory, so the traversal cannot be sent around a cycle.
     */
    public fun isDirectory(path: FsPath): Boolean

    /**
     * The direct children of [directory], **sorted by name**, so that the order of a report
     * does not depend on the OS. Empty when [directory] is not a directory.
     */
    public fun list(directory: FsPath): List<FsPath>
}
