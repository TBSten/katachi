package me.tbsten.katachi.test.fs

import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.fs.KatachiFileSystem

/**
 * A tree that throws instead of answering, for the paths a spec names.
 *
 * Paths are absolute, the way the traversal sees them (`/repo/src/App.kt`), and the throwable
 * is built per call so that one spec can hand the same tree to several runs.
 *
 * The three operations are configured separately because the readers of the tree react to
 * them differently. A path whose `isDirectory` throws cannot even be classified, while a
 * directory whose `list` throws is known to be a directory and its siblings are still
 * answerable. `exists` is the one only the module search asks, which makes it the way to break
 * that search alone: the walk never calls it, so a spec can fail one without the other.
 *
 * Nothing is configured by default, so a spec that names no path here is handed the delegate's
 * own answers.
 */
internal class ThrowingFileSystem(
    private val delegate: KatachiFileSystem,
    private val onIsDirectory: Map<String, () -> Throwable> = emptyMap(),
    private val onList: Map<String, () -> Throwable> = emptyMap(),
    private val onExists: Map<String, () -> Throwable> = emptyMap(),
) : KatachiFileSystem {
    override val workingDirectory: FsPath get() = delegate.workingDirectory

    override fun exists(path: FsPath): Boolean {
        onExists[path.value]?.let { throw it() }
        return delegate.exists(path)
    }

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

/**
 * [this] with the path at [path] throwing when its existence is asked about.
 *
 * The module search is the only reader that asks, so this breaks that search and leaves the
 * walk of the tree answering exactly as it did before.
 */
internal fun KatachiFileSystem.failingToExistAt(path: String, cause: () -> Throwable): KatachiFileSystem =
    ThrowingFileSystem(this, onExists = mapOf(path to cause))
