package me.tbsten.katachi.test.check

import me.tbsten.katachi.check.FsPath
import me.tbsten.katachi.check.KatachiFileSystem

/** What [ForbiddenFileSystem] throws, naming the operation that was not supposed to happen. */
internal class FileSystemTouchedException(operation: String) :
    IllegalStateException("The file system was touched: $operation")

/**
 * A tree that fails the spec instead of answering.
 *
 * For specs whose claim is that nothing is read at all. A fake tree that simply answers
 * cannot make that claim: every assertion about it still passes when the code under test
 * quietly walked it, so the spec would go green either way.
 *
 * Note that it also refuses [workingDirectory], which is the very first thing the project
 * root search asks for — so a run that resolves the root eagerly fails here rather than
 * further in.
 */
internal object ForbiddenFileSystem : KatachiFileSystem {
    override val workingDirectory: FsPath get() = refuse("workingDirectory")

    override fun exists(path: FsPath): Boolean = refuse("exists($path)")

    override fun isDirectory(path: FsPath): Boolean = refuse("isDirectory($path)")

    override fun list(directory: FsPath): List<FsPath> = refuse("list($directory)")

    override fun toString(): String = "ForbiddenFileSystem"

    private fun refuse(operation: String): Nothing = throw FileSystemTouchedException(operation)
}
