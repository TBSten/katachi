package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.data.placement.conversionOf
import me.tbsten.katachi.intellij.model.CaptureOccurrenceModel
import me.tbsten.katachi.intellij.model.CaptureRef
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel

/** Why a bound entry held a file back: every occurrence of a bound name, and the path the file would have been allowed at. */
internal class CaptureMismatchDetail(val occurrences: List<CaptureOccurrenceModel>, val expectedPath: String?)

/** What only an unresolved module key holds: its own `*`, or a `<name>` placeholder. A module path with one names no real module. */
private const val UNRESOLVED_MODULE_CHARACTERS: String = "*<>"

/**
 * What holds a file at [path] back from [entry], whose compiled pattern is [glob], or `null` when the entry allows it
 * (or does not match it at all, which is the pattern's question). The rule of katachi's `LayoutEntry.captureMismatchOf`
 * (design capture-check 2.2 and 3.8), over the one capture variant the JSON writes per entry:
 *
 * - Bound: a name on two levels or more, or a derived occurrence (`transform != null`). An entry with no binding allows
 *   every file its pattern matches, whatever its captures read.
 * - A `File` entry reads the file's own path, an `anyFile()` entry its directory. Each occurrence reads the
 *   [CaptureRef.wildcardIndex]-th `*` of its level. A module key's names are read from [LayoutEntryModel.modulePath],
 *   only when that is a real module's path (not `*` or `<name>`), as the levels of the module's directory.
 * - The first plain occurrence of a name is the value; every other plain occurrence is held to it as a string, and a
 *   derived one to its conversion of it. A conversion this plugin does not know compares nothing.
 *
 * ```kotlin
 * captureMismatchOf(entry, glob, "feature/home/src/main/kotlin/com/example/feature/settings/HomeScreen.kt")
 *     ?.occurrences?.map { "${it.name} ${it.value} at ${it.level}" } // feature home at 1, feature settings at 7, ...
 * ```
 */
internal fun captureMismatchOf(entry: LayoutEntryModel, glob: KatachiGlob, path: String): CaptureMismatchDetail? {
    val moduleNames = entry.moduleCapture?.names.orEmpty()
    val plainNames = moduleNames + entry.captures.filter { it.transform == null }.map { it.name }
    val isBound = plainNames.groupingBy { it }.eachCount().any { it.value > 1 } || entry.captures.any { it.transform != null }
    if (!isBound) return null
    val target = matchTargetOf(entry, path) ?: return null
    val groups = glob.matchGroups(target) ?: return null

    val occurrences = moduleOccurrencesOf(entry) + entry.captures.mapNotNull { capture ->
        glob.singleGroupValue(groups, capture.segmentIndex, capture.wildcardIndex)?.let { BoundOccurrence(capture.name, capture.transform, capture.segmentIndex, it, capture) }
    }
    val plainCount = occurrences.filter { it.transform == null }.groupingBy { it.name }.eachCount()
    val derivedNames = occurrences.filter { it.transform != null }.map { it.name }.toSet()
    val bound = occurrences.filter { (plainCount[it.name] ?: 0) > 1 || it.name in derivedNames }
    if (bound.isEmpty()) return null

    val sourceOf = LinkedHashMap<String, String>()
    for (occurrence in bound) if (occurrence.transform == null) sourceOf.putIfAbsent(occurrence.name, occurrence.value)

    var mismatched = false
    var expectedUnknown = false
    val expectedOf = HashMap<CaptureRef, String>()
    val sourcesSeen = HashSet<String>()
    val details = bound.map { occurrence ->
        val source = sourceOf[occurrence.name]
        val conversion = occurrence.transform?.let { conversionOf(it) }
        val expected = when {
            // The first plain occurrence is what the others are held to.
            occurrence.transform == null && sourcesSeen.add(occurrence.name) -> null
            // A derived occurrence whose plain capture is only on an unresolved module key, or whose conversion is a newer katachi's.
            source == null || (occurrence.transform != null && conversion == null) -> {
                expectedUnknown = true
                null
            }
            else -> conversion?.invoke(source) ?: source
        }
        if (expected != null && expected != occurrence.value) mismatched = true
        occurrence.capture?.let { expectedOf[it] = expected ?: occurrence.value }
        CaptureOccurrenceModel(occurrence.name, occurrence.transform, occurrence.level, occurrence.value, expected)
    }
    if (!mismatched) return null

    val expectedPath = if (expectedUnknown) null else expectedPathOf(entry, glob, path, groups, expectedOf)
    return CaptureMismatchDetail(details, expectedPath)
}

/** One occurrence with its value, before the expected value is known. [capture] is `null` for a module key's. */
private class BoundOccurrence(val name: String, val transform: String?, val level: Int, val value: String, val capture: CaptureRef?)

/** [path] itself for a file entry, its directory for an `anyFile()` entry, `null` for a kind that names no file. */
private fun matchTargetOf(entry: LayoutEntryModel, path: String): String? = when (entry.kind) {
    LayoutEntryKind.File -> path
    LayoutEntryKind.AnyFile -> path.parentPath()
    else -> null
}

/**
 * The values a module key's names take from the module [entry] was resolved to, in the key's order, on the level of the
 * module directory's last segment. Empty when the key gave no name, named only some of its `*`s, or was never resolved.
 */
private fun moduleOccurrencesOf(entry: LayoutEntryModel): List<BoundOccurrence> {
    val capture = entry.moduleCapture ?: return emptyList()
    val modulePath = entry.modulePath ?: return emptyList()
    if (capture.names.isEmpty() || modulePath.any { it in UNRESOLVED_MODULE_CHARACTERS }) return emptyList()
    val pattern = try {
        KatachiGlob.compile(capture.pattern, KatachiGlob.MODULE_SEPARATOR)
    } catch (failure: KatachiGlobSyntaxException) {
        return emptyList()
    }
    // Only a key naming every `*` says which name is which (`names` drops the unnamed ones).
    if (capture.names.size != pattern.singleWildcardCount) return emptyList()
    val groups = pattern.matchGroups(modulePath) ?: return emptyList()
    val singles = pattern.groupKinds.indices.filter { pattern.groupKinds[it] == GlobGroupKind.Single }
    val level = moduleDirectoryLevel(entry)
    return capture.names.mapIndexedNotNull { index, name -> singles.getOrNull(index)?.let { BoundOccurrence(name, null, level, groups[it], capture = null) } }
}

/** The 0-based level of the last segment of the entry's module directory, or `-1` for the root project or no module. */
private fun moduleDirectoryLevel(entry: LayoutEntryModel): Int {
    val levels = entry.pattern.split('/').size
    val inModule = if (entry.pathInModule.isEmpty()) 0 else entry.pathInModule.split('/').size
    return levels - inModule - 1
}

/**
 * [path] with every level a bound occurrence sits on filled with its expected value, every other `*` and every `**`
 * kept as the file has it, and escapes undone. Built from the entry's pattern, the one source that says which `*` each
 * occurrence is; an `anyFile()` entry keeps the file's own name below.
 */
private fun expectedPathOf(entry: LayoutEntryModel, glob: KatachiGlob, path: String, groups: List<String>, expectedOf: Map<CaptureRef, String>): String {
    val segments = entry.pattern.split('/')
    var group = 0
    val filled = ArrayList<String>(segments.size)
    for ((segmentIndex, segment) in segments.withIndex()) {
        if (segment == "**") {
            val taken = groups.getOrElse(group++) { "" }
            if (taken.isNotEmpty()) filled += taken
            continue
        }
        var wildcardIndex = 0
        filled += buildString {
            var index = 0
            while (index < segment.length) {
                val character = segment[index]
                when {
                    character == '\\' && index + 1 < segment.length -> {
                        append(segment[index + 1])
                        index += 2
                    }

                    character == '*' -> {
                        val capture = entry.captures.firstOrNull { it.segmentIndex == segmentIndex && it.wildcardIndex == wildcardIndex }
                        append(capture?.let { expectedOf[it] } ?: groups.getOrElse(group) { "" })
                        group++
                        wildcardIndex++
                        index++
                    }

                    else -> {
                        append(character)
                        index++
                    }
                }
            }
        }
    }
    val expected = filled.joinToString("/")
    return if (entry.kind == LayoutEntryKind.AnyFile) "$expected/${path.substringAfterLast('/')}" else expected
}
