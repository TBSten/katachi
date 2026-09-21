// The traversal is written entirely against katachi's internal file system and layout types.
@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.flattenLayout

/**
 * Directories that are not this project's files at all, whatever `files` says.
 *
 * The list stops here. `.kotlin/`, `local.properties`, `.DS_Store` and `build/` are **not**
 * on it: what a machine leaves lying around never converges into a list, which is exactly
 * the job the default `files = gitTracked()` hands to git. Under that default this set is
 * already redundant; it earns its place under `wholeTree()`, and when a project root was
 * found without git being there to answer.
 */
private val FOREIGN_DIRECTORY_NAMES: Set<String> = setOf(".git", ".gradle", ".idea")

/**
 * Runs the check against the real file system and returns every violation.
 *
 * Nothing is thrown, including when the definition allows nothing at all: `assert()` is what
 * turns this into a test failure, and a report or a baseline can sit on it instead.
 *
 * @throws ProjectRootNotFoundException when no directory above the working directory carries
 *   a Gradle, Maven or git marker.
 * @throws GitUnavailableException when `files = gitTracked()` and the project root is a git
 *   repository, but git cannot be run.
 */
@InternalKatachiApi
public fun Architecture.validate(): List<Violation> = validate(RealFileSystem())

/**
 * [validate] against [fileSystem], which katachi's own specs use to hand it a tree that only
 * exists in memory.
 *
 * The project root is looked for in [fileSystem] itself, unfiltered: a marker such as
 * `.git/HEAD` belongs to none of the file sets `files` can select, so the search has to run
 * before the selection is applied. The module index is built the same way, because a module
 * whose `build.gradle.kts` a file set happens to leave out is still a module, and a module
 * path key with a wildcard would otherwise quietly expand to nothing.
 */
@InternalKatachiApi
public fun Architecture.validate(fileSystem: KatachiFileSystem): List<Violation> {
    val projectRoot = findProjectRoot(fileSystem)
    return Scan(
        fileSystem = files.fileSystemFor(fileSystem, projectRoot),
        root = projectRoot.path,
        layout = LayoutIndex(flattenLayout(moduleIndex(fileSystem, projectRoot.path, moduleResolver))),
    ).run()
}

/**
 * One walk of the tree below the project root.
 *
 * The walk is driven by the declarations, not by the tree: it descends into a directory only
 * while some role still has something to say about what is inside it, and reports the
 * directory itself otherwise.
 */
private class Scan(
    private val fileSystem: KatachiFileSystem,
    private val root: FsPath,
    private val layout: LayoutIndex,
) {
    private val violations = mutableListOf<Violation>()

    /** Every file the walk reached, as a project relative path. */
    private val visitedFiles = mutableSetOf<String>()

    fun run(): List<Violation> {
        walk(root, "")
        for (entry in layout.requiredFiles) {
            if (entry.path in visitedFiles) continue
            violations += MissingFile(
                path = entry.path,
                role = entry.role,
                declaredAt = entry.declaredAt,
            )
        }
        // Blocks are grouped by kind, because the reader does something different with each
        // group. `sortedBy` is stable, so within a group the walk order survives: parents
        // before children, names in order.
        return violations.sortedBy { it.kind.ordinal }
    }

    private fun walk(directory: FsPath, path: String) {
        for (child in fileSystem.list(directory)) {
            val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
            if (fileSystem.isDirectory(child)) visitDirectory(child, childPath) else visitFile(childPath)
        }
    }

    private fun visitDirectory(directory: FsPath, path: String) {
        if (directory.name in FOREIGN_DIRECTORY_NAMES) return
        if (layout.isIgnored(path)) return
        if (!layout.isKnown(path)) {
            violations += UnexpectedDirectory(path)
            return
        }
        walk(directory, path)
    }

    private fun visitFile(path: String) {
        visitedFiles += path
        if (layout.allowsFile(path)) return
        violations += UnexpectedFile(path = path, nearby = layout.nearbyOf(path))
    }
}
