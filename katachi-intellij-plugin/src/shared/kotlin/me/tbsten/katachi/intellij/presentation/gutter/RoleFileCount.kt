package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel

/**
 * How many files a role holds, counted from the `matchedFiles` of its file entries: the one rule the definition ◆, its Code
 * Vision and the source file's menu (via [SiblingFiles]) share. It is the number of distinct files listed, plus what an entry
 * whose list was cut ([LayoutEntryModel.truncated]) left out. Only `File` / `AnyFile` entries hold files.
 *
 * `null` is "not known": the role has file entries but the walk behind every one of them could not run
 * ([LayoutEntryModel.matchedCount] is `null`), which is not the same as holding none. A role with no file entry holds 0.
 *
 * TODO: have [SiblingFiles.of] call [hiddenFiles] instead of its own copy of the sum once its owner's stages are merged;
 *  `RoleFileCountTest` holds the two equal until then.
 *
 * ```kotlin
 * val count = RoleFileCount.of(role.layout)   // 3, or null: "? files"
 * ```
 */
internal object RoleFileCount {
    fun of(entries: List<LayoutEntryModel>): Int? {
        val holders = holders(entries)
        if (holders.isNotEmpty() && holders.all { it.matchedCount == null }) return null
        return holders.flatMap { it.matchedFiles }.distinct().size + hiddenFiles(holders)
    }

    /** The file entries of [entries]. */
    fun holders(entries: List<LayoutEntryModel>): List<LayoutEntryModel> =
        entries.filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }

    /** The distinct files the lists of [entries] name: a lower bound of what they hold when [isCut]. */
    fun listed(entries: List<LayoutEntryModel>): Int = holders(entries).flatMap { it.matchedFiles }.distinct().size

    /** Whether the JSON cut the list of one of the file entries of [entries] (`matchedFilesTruncated`). */
    fun isCut(entries: List<LayoutEntryModel>): Boolean = holders(entries).any { it.truncated }

    /** What the cut lists of [holders] left out. */
    fun hiddenFiles(holders: List<LayoutEntryModel>): Int = holders.sumOf { entry ->
        if (entry.truncated) ((entry.matchedCount ?: entry.matchedFiles.size) - entry.matchedFiles.size).coerceAtLeast(0) else 0
    }
}
