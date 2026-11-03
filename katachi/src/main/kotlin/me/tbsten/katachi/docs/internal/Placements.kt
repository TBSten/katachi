package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role

internal class Placement(
    val module: String?,
    val path: String,
    val description: String?,
    val isDirectory: Boolean,
)

internal fun placementsOf(entries: List<LayoutEntry>): Map<Role, List<Placement>> =
    entries.groupBy { it.role }.mapValues { (_, own) -> placementsOfRole(own) }

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
                isDirectory = entry.kind != LayoutEntryKind.File,
            )
        }
}

private fun LayoutEntry.moduleColumn(): String? = when (kind) {
    LayoutEntryKind.Ignore, LayoutEntryKind.AnyFile -> null
    else -> modulePath
}

private fun LayoutEntry.pathColumn(): String =
    if (moduleColumn() == null) path else pathInModule.ifEmpty { path }

private fun descriptionAt(path: String, descriptions: Map<String, String?>): String? {
    var current = path
    while (true) {
        descriptions[current]?.let { return it }
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isEmpty()) return null
        current = parent
    }
}
