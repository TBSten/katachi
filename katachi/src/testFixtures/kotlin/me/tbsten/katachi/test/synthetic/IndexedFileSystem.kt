package me.tbsten.katachi.test.synthetic

import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem

/**
 * An in-memory tree that keeps each directory's children, sorted, ahead of time.
 *
 * Unlike the `FakeFileSystem` of katachi's own specs, whose `list` filters every path of the
 * tree on each call, [list] here is one map lookup. At 10k files the fake would otherwise be
 * what a benchmark measures.
 *
 * Every ancestor of a file is a directory, and so is [workingDirectory].
 *
 * ## Example 1: build a tree from absolute paths
 * ```kt
 * val fileSystem = IndexedFileSystem.of("/repo", listOf("/repo/a/B.kt", "/repo/c.txt"))
 * fileSystem.list(FsPath.of("/repo")).map { it.name } shouldBe listOf("a", "c.txt")
 * ```
 */
class IndexedFileSystem private constructor(
    override val workingDirectory: FsPath,
    private val files: Set<FsPath>,
    private val children: Map<FsPath, List<FsPath>>,
) : KatachiFileSystem {
    override fun exists(path: FsPath): Boolean = path in files || path in children

    override fun isDirectory(path: FsPath): Boolean = path in children

    override fun list(directory: FsPath): List<FsPath> = children[directory].orEmpty()

    override fun toString(): String = "IndexedFileSystem($workingDirectory, files=${files.size})"

    /** Where [IndexedFileSystem] values are built. */
    companion object {
        /** A tree holding [files], all absolute, with [workingDirectory] as a directory. */
        fun of(workingDirectory: String, files: Iterable<String>): IndexedFileSystem {
            val filePaths = files.map { FsPath.of(it) }.toSet()
            val children = HashMap<FsPath, MutableSet<FsPath>>()
            fun addDirectoryChain(path: FsPath) {
                var child = path
                var parent = child.parent
                // Stops at the first ancestor that already lists this child: everything above
                // it was registered when that ancestor was.
                while (parent != null) {
                    val siblings = children.getOrPut(parent) { HashSet() }
                    if (!siblings.add(child) && child != path) return
                    child = parent
                    parent = child.parent
                }
                children.getOrPut(child) { HashSet() }
            }
            val working = FsPath.of(workingDirectory)
            children.getOrPut(working) { HashSet() }
            addDirectoryChain(working)
            filePaths.forEach(::addDirectoryChain)
            return IndexedFileSystem(
                workingDirectory = working,
                files = filePaths,
                children = children.mapValues { (_, value) -> value.sortedBy { it.name } },
            )
        }
    }
}

/**
 * This project as an [IndexedFileSystem], with `.git/HEAD` added as the project root marker.
 *
 * `.git/` is a directory katachi knows is not the project's own, so the marker never shows up
 * as a violation.
 */
fun SyntheticProject.inMemoryFileSystem(): IndexedFileSystem =
    IndexedFileSystem.of(
        workingDirectory = root.value,
        files = (files + GIT_MARKER).map { (root / it).value },
    )

/** The file that makes [SyntheticProject.root] the project root. */
internal const val GIT_MARKER: String = ".git/HEAD"
