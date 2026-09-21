package me.tbsten.katachi.test.check

import me.tbsten.katachi.check.FsPath
import me.tbsten.katachi.check.KatachiFileSystem

/**
 * A tree that throws instead of answering, for the paths a spec names.
 *
 * Paths are absolute, the way the traversal sees them (`/repo/src/App.kt`), and the throwable
 * is built per call so that one spec can hand the same tree to several runs.
 *
 * The two operations are configured separately because the traversal reacts to them
 * differently. A path whose `isDirectory` throws cannot even be classified, while a directory
 * whose `list` throws is known to be a directory and its siblings are still answerable.
 */
internal class ThrowingFileSystem(
    private val delegate: KatachiFileSystem,
    private val onIsDirectory: Map<String, () -> Throwable> = emptyMap(),
    private val onList: Map<String, () -> Throwable> = emptyMap(),
) : KatachiFileSystem {
    override val workingDirectory: FsPath get() = delegate.workingDirectory

    // `exists` is left alone on purpose: finding the project root and discovering the modules
    // both run before the walk and are not covered by it, so a spec about the walk must not
    // break them.
    override fun exists(path: FsPath): Boolean = delegate.exists(path)

    override fun isDirectory(path: FsPath): Boolean {
        onIsDirectory[path.value]?.let { throw it() }
        return delegate.isDirectory(path)
    }

    override fun list(directory: FsPath): List<FsPath> {
        onList[directory.value]?.let { throw it() }
        return delegate.list(directory)
    }

    override fun toString(): String = "ThrowingFileSystem($delegate)"
}

/** [this] with the thing at [path] throwing as soon as the walk looks at it. */
internal fun KatachiFileSystem.failingAt(path: String, cause: () -> Throwable): KatachiFileSystem =
    ThrowingFileSystem(this, onIsDirectory = mapOf(path to cause))

/** [this] with the directory at [path] throwing when it is listed. */
internal fun KatachiFileSystem.failingToListAt(path: String, cause: () -> Throwable): KatachiFileSystem =
    ThrowingFileSystem(this, onList = mapOf(path to cause))
