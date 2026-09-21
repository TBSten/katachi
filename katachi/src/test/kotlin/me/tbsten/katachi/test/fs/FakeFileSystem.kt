package me.tbsten.katachi.test.fs

import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.fs.KatachiFileSystem

/**
 * An in-memory tree, built by [fakeFileSystem].
 *
 * It holds names only — a file has no contents, because [KatachiFileSystem] has no way to
 * read any. Matching is case sensitive, unlike a default macOS volume; a spec that wants to
 * know what happens on such a volume has to use a real directory.
 */
class FakeFileSystem internal constructor(
    override val workingDirectory: FsPath,
    private val files: Set<FsPath>,
    private val directories: Set<FsPath>,
) : KatachiFileSystem {
    override fun exists(path: FsPath): Boolean = path in files || path in directories

    override fun isDirectory(path: FsPath): Boolean = path in directories

    override fun list(directory: FsPath): List<FsPath> {
        if (directory !in directories) return emptyList()
        return (files + directories)
            .filter { it.parent == directory }
            .sortedBy { it.name }
    }

    override fun toString(): String = "FakeFileSystem($workingDirectory, files=${files.size})"
}

/**
 * Builds a [FakeFileSystem].
 *
 * `"name" { }` is a directory and `"name"()` is a file. A key may hold several levels
 * (`"gradle/wrapper"`), and a key starting with `/` is absolute rather than relative to the
 * enclosing block.
 *
 * ```kotlin
 * val fileSystem = fakeFileSystem(workingDirectory = "/repo/app") {
 *   "/repo" {
 *     "gradlew"()
 *     "docs" { "README.md"() }
 *   }
 * }
 * ```
 */
fun fakeFileSystem(
    workingDirectory: String,
    block: FakeFileSystemScope.() -> Unit,
): FakeFileSystem {
    val builder = FakeFileSystemBuilder()
    FakeFileSystemScope(builder, FsPath.of("/")).block()
    return builder.build(FsPath.of(workingDirectory))
}

class FakeFileSystemBuilder internal constructor() {
    private val files = mutableSetOf<FsPath>()
    private val directories = mutableSetOf(FsPath.of("/"))

    internal fun addFile(path: FsPath) {
        addAncestorsOf(path)
        files += path
    }

    internal fun addDirectory(path: FsPath) {
        addAncestorsOf(path)
        directories += path
    }

    internal fun build(workingDirectory: FsPath): FakeFileSystem {
        // The working directory is part of the tree even when nothing was declared in it.
        addDirectory(workingDirectory)
        return FakeFileSystem(workingDirectory, files.toSet(), directories.toSet())
    }

    private fun addAncestorsOf(path: FsPath) {
        var parent = path.parent
        while (parent != null) {
            directories += parent
            parent = parent.parent
        }
    }
}

/** Receiver of a [fakeFileSystem] block. One instance per directory level. */
class FakeFileSystemScope internal constructor(
    private val builder: FakeFileSystemBuilder,
    private val directory: FsPath,
) {
    /** Declares a directory and describes what is inside it. */
    operator fun String.invoke(block: FakeFileSystemScope.() -> Unit) {
        val path = resolve(this)
        builder.addDirectory(path)
        FakeFileSystemScope(builder, path).block()
    }

    /** Declares a file. */
    operator fun String.invoke() {
        builder.addFile(resolve(this))
    }

    private fun resolve(key: String): FsPath =
        if (key.startsWith("/")) FsPath.of(key) else directory / key
}
