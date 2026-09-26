package me.tbsten.katachi.test.synthetic

import java.util.concurrent.ConcurrentHashMap
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem

/**
 * A [KatachiFileSystem] that forwards to [delegate] and counts every call, per path.
 *
 * Made for work-amount tests: "one `assert()` lists each directory once" is
 * `listCalls.values.max() shouldBe 1`, a number that does not move with the runner's speed.
 * Reading [workingDirectory] is not counted. The counters are safe to update from several
 * threads, and [reset] clears them between runs.
 *
 * ## Example 1: count how often each directory is listed
 * ```kt
 * val fileSystem = CountingFileSystem(project.inMemoryFileSystem())
 * project.architecture().validate(fileSystem)
 * fileSystem.listCalls.values.max() shouldBe 1
 * ```
 */
class CountingFileSystem(private val delegate: KatachiFileSystem) : KatachiFileSystem {
    private val exists = ConcurrentHashMap<FsPath, Int>()
    private val isDirectory = ConcurrentHashMap<FsPath, Int>()
    private val list = ConcurrentHashMap<FsPath, Int>()

    override val workingDirectory: FsPath get() = delegate.workingDirectory

    /** How many times [exists] was called, per path. A snapshot. */
    val existsCalls: Map<FsPath, Int> get() = exists.toMap()

    /** How many times [isDirectory] was called, per path. A snapshot. */
    val isDirectoryCalls: Map<FsPath, Int> get() = isDirectory.toMap()

    /** How many times [list] was called, per directory. A snapshot. */
    val listCalls: Map<FsPath, Int> get() = list.toMap()

    /** Calls of all three operations, summed. */
    val totalCalls: Int get() = exists.values.sum() + isDirectory.values.sum() + list.values.sum()

    /** Forgets every count. */
    fun reset() {
        exists.clear()
        isDirectory.clear()
        list.clear()
    }

    override fun exists(path: FsPath): Boolean {
        exists.merge(path, 1, Int::plus)
        return delegate.exists(path)
    }

    override fun isDirectory(path: FsPath): Boolean {
        isDirectory.merge(path, 1, Int::plus)
        return delegate.isDirectory(path)
    }

    override fun list(directory: FsPath): List<FsPath> {
        list.merge(directory, 1, Int::plus)
        return delegate.list(directory)
    }

    override fun toString(): String = "CountingFileSystem($delegate, calls=$totalCalls)"
}
