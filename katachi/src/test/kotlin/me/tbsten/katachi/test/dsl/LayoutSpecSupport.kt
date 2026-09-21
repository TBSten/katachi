package me.tbsten.katachi.test.dsl

import me.tbsten.katachi.check.FsPath
import me.tbsten.katachi.check.ModuleIndex
import me.tbsten.katachi.check.ModuleResolver
import me.tbsten.katachi.check.moduleIndex
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.test.check.fakeFileSystem

/**
 * Flattens a single `layout { }` block, wrapped in the smallest architecture that can hold
 * it.
 *
 * The block is written in the calling spec, so every declaration site a flattened entry
 * carries points at that spec rather than at this file.
 */
internal fun layoutOf(block: LayoutScope.() -> Unit): List<LayoutEntry> =
    architecture {
        "group".group {
            "Role" { layout(block) }
        }
    }.flattenLayout()

/** [layoutOf] against a project whose modules are [moduleIndex]. See [moduleIndexOf]. */
internal fun layoutOf(moduleIndex: ModuleIndex, block: LayoutScope.() -> Unit): List<LayoutEntry> =
    architecture {
        "group".group {
            "Role" { layout(block) }
        }
    }.flattenLayout(moduleIndex)

/**
 * A project whose modules are exactly [moduleDirectories], each given the build file that
 * makes a directory a module.
 *
 * ```kotlin
 * moduleIndexOf("core/data", "feature/home")
 * ```
 */
internal fun moduleIndexOf(
    vararg moduleDirectories: String,
    resolver: ModuleResolver = ModuleResolver.Conventional,
): ModuleIndex {
    val fileSystem = fakeFileSystem(workingDirectory = "/repo") {
        "/repo" {
            moduleDirectories.forEach { "$it/build.gradle.kts"() }
        }
    }
    return moduleIndex(fileSystem, FsPath.of("/repo"), resolver)
}

/**
 * The part of a flattened entry that a declaration decides, with everything that depends on
 * where it was written left out.
 *
 * Two spellings of the same layout are compared through this, because `/` chaining and
 * nested blocks cannot be written on the same line and so never share a declaration site.
 */
internal fun List<LayoutEntry>.shape(): List<String> = map { entry ->
    buildString {
        append(entry.path)
        append(" [")
        append(entry.kind)
        append(']')
        if (entry.required) append(" required")
        entry.description?.let { append(" \"").append(it).append('"') }
    }
}
