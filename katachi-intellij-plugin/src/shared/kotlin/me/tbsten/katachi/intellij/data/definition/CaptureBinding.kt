package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.data.placement.conversionOf
import me.tbsten.katachi.intellij.model.CaptureOccurrenceModel
import me.tbsten.katachi.intellij.model.CaptureRef
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel

internal class CaptureMismatchDetail(val occurrences: List<CaptureOccurrenceModel>, val expectedPath: String?)

private const val UNRESOLVED_MODULE_CHARACTERS: String = "*<>"

// Mirrors katachi's `LayoutEntry.captureMismatchOf`; a conversion this plugin does not know compares nothing.
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

    val expectedPath = if (expectedUnknown) null else expectedPathOf(entry, glob, path, expectedOf)
    return CaptureMismatchDetail(details, expectedPath)
}

private class BoundOccurrence(val name: String, val transform: String?, val level: Int, val value: String, val capture: CaptureRef?)

private fun matchTargetOf(entry: LayoutEntryModel, path: String): String? = when (entry.kind) {
    LayoutEntryKind.File -> path
    LayoutEntryKind.AnyFile -> path.parentPath().takeIf { '/' in path }
    else -> null
}

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

private fun moduleDirectoryLevel(entry: LayoutEntryModel): Int {
    val levels = entry.pattern.split('/').size
    val inModule = if (entry.pathInModule.isEmpty()) 0 else entry.pathInModule.split('/').size
    return levels - inModule - 1
}

private fun expectedPathOf(entry: LayoutEntryModel, glob: KatachiGlob, path: String, expectedOf: Map<CaptureRef, String>): String {
    val target = matchTargetOf(entry, path) ?: return path
    val ranges = glob.matchRanges(target) ?: return path
    val replacements = entry.captures.mapNotNull { capture ->
        val expected = expectedOf[capture] ?: return@mapNotNull null
        glob.singleGroupIndex(capture.segmentIndex, capture.wildcardIndex)?.let { ranges.getOrNull(it) }?.let { it to expected }
    }
    val expected = replacements.sortedByDescending { it.first.first }.fold(target) { text, (range, value) -> text.replaceRange(range, value) }
    return if (entry.kind == LayoutEntryKind.AnyFile) "$expected/${path.substringAfterLast('/')}" else expected
}
