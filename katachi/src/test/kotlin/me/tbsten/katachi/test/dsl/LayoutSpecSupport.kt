package me.tbsten.katachi.test.dsl

import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.flattenLayout

/**
 * Flattens a single `layout { }` block, wrapped in the smallest architecture that can hold
 * it.
 *
 * The block is written in the calling spec, so every declaration site a flattened entry
 * carries points at that spec rather than at this file.
 */
@OptIn(InternalKatachiApi::class)
internal fun layoutOf(block: LayoutScope.() -> Unit): List<LayoutEntry> =
    architecture {
        "group".group {
            "Role" { layout(block) }
        }
    }.flattenLayout()

/**
 * The part of a flattened entry that a declaration decides, with everything that depends on
 * where it was written left out.
 *
 * Two spellings of the same layout are compared through this, because `/` chaining and
 * nested blocks cannot be written on the same line and so never share a declaration site.
 */
@OptIn(InternalKatachiApi::class)
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
