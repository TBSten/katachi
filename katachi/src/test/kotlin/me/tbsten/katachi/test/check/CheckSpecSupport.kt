package me.tbsten.katachi.test.check

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.test.fs.FakeFileSystem
import me.tbsten.katachi.test.fs.FakeFileSystemScope
import me.tbsten.katachi.test.fs.fakeFileSystem

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

/**
 * Runs [block] with [System.err] swapped for a buffer, and returns whatever it printed.
 *
 * Standard error is `assertWith`'s only channel for a run that holds nothing but warnings, so a
 * spec that wants to pin what came out of it has to capture the stream rather than read a
 * return value. The original stream is restored even when [block] throws, so one spec's capture
 * can never leak into the next.
 */
internal fun capturingStandardError(block: () -> Unit): String {
    val original = System.err
    val buffer = ByteArrayOutputStream()
    System.setErr(PrintStream(buffer, true, "UTF-8"))
    try {
        block()
    } finally {
        System.setErr(original)
    }
    return buffer.toString("UTF-8")
}
