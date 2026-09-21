package me.tbsten.katachi.test.check

import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.architecture

/**
 * A tree with a project root marker the check never runs into.
 *
 * `.git/` is one of the directories katachi knows is not the project's own, so the marker
 * that makes `/repo` the root cannot itself turn up as a violation. Pair it with
 * [architectureOf], which selects `wholeTree()`: a spec about the traversal has no business
 * starting a `git` process, and the git filter is covered by its own spec.
 */
internal fun repositoryOf(
    workingDirectory: String = "/repo",
    block: FakeFileSystemScope.() -> Unit,
): FakeFileSystem = fakeFileSystem(workingDirectory) {
    "/repo" {
        ".git" { "HEAD"() }
        block()
    }
}

/** An architecture over the whole tree, for a spec that hands [Architecture.validate] a fake. */
internal fun architectureOf(block: ArchitectureScope.() -> Unit): Architecture = architecture {
    files = wholeTree()
    block()
}

/**
 * The smallest architecture holding one `layout { }` block.
 *
 * The block is written in the calling spec, so a violation carrying a declaration site points
 * at that spec rather than at this file.
 */
internal fun layoutArchitecture(
    group: String = "app",
    role: String = "Role",
    block: LayoutScope.() -> Unit,
): Architecture = architectureOf {
    group.group { role { layout(block) } }
}

/** Violations as their report's first lines, which is what a spec about the traversal means. */
internal fun List<Violation>.labels(): List<String> = map { "[${it.label}] ${it.path}" }
