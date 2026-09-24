package me.tbsten.katachi.docs

import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role

/**
 * One row of a role page's `## 配置場所` table: a place its files may live, as declared.
 *
 * It is what a `layout { }` says rather than what the project holds, so a pattern is here
 * whether or not a file sits at it. That is the whole point of generating documentation from
 * the declarations: the page describes the rule, and the check is what says whether the
 * repository keeps it.
 */
internal class Placement(
    /** The Gradle module, or `null` when this place is not inside a `module { }` block. */
    val module: String?,
    /** Relative to [module] when there is one, relative to the project root otherwise. */
    val path: String,
    /** The `description = "..."` this place was given, or the nearest one above it. */
    val description: String?,
)

/**
 * The places every role declared, by role, in declaration order.
 *
 * Built from the flattened entries rather than from the `layout { }` blocks, because the blocks
 * have to be evaluated to say anything at all and the flattening is where that already happens.
 */
internal fun placementsOf(entries: List<LayoutEntry>): Map<Role, List<Placement>> =
    entries.groupBy { it.role }.mapValues { (_, own) -> placementsOfRole(own) }

/**
 * The rows of one role's table.
 *
 * Two kinds of entry are left out. A plain directory claims no file -- it is the path on the way
 * to one, and listing `src`, `src/main` and `src/main/kotlin` above every pattern would bury the
 * patterns themselves. And what a `module { }` block injects (`build/`, `build.gradle.kts`) is
 * katachi's own doing, so it would put the same two rows on the page of every role that names
 * the module.
 */
private fun placementsOfRole(entries: List<LayoutEntry>): List<Placement> {
    val descriptions = entries.filter { it.description != null }.associate { it.path to it.description }
    return entries
        .filterNot { it.synthetic }
        .filter { it.kind != LayoutEntryKind.Directory }
        .map { entry ->
            Placement(
                module = entry.moduleColumn(),
                path = entry.pathColumn(),
                description = descriptionAt(entry.path, descriptions),
            )
        }
}

/**
 * The module a row names, or `null` for a row that has no module column to fill.
 *
 * `ignore()` and `anyFile()` are about a directory rather than about files in a module's own
 * layout, and a path declared outside a `module { }` block has no module at all. Both say the
 * same thing: there is nothing to put in that column, so the path is written out in full
 * instead.
 */
private fun LayoutEntry.moduleColumn(): String? = when (kind) {
    LayoutEntryKind.Ignore, LayoutEntryKind.AnyFile -> null
    else -> modulePath
}

/** The path a row names: relative to its module when it names one, absolute to the root if not. */
private fun LayoutEntry.pathColumn(): String =
    if (moduleColumn() == null) path else pathInModule.ifEmpty { path }

/**
 * The description of [path], or of the nearest directory above it that has one.
 *
 * `description = "..."` is written in a block, and the block a reader means when they write it
 * is almost always the module block: "files of this role that live *here* are the ones that
 * ...". The pattern several levels below it is what the table has a row for, so the text has to
 * travel down to it.
 */
private fun descriptionAt(path: String, descriptions: Map<String, String?>): String? {
    var current = path
    while (true) {
        descriptions[current]?.let { return it }
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isEmpty()) return null
        current = parent
    }
}
