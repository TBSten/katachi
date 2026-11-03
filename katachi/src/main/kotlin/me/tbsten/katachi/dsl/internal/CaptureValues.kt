package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.ModulePath

// A name written on two levels reads the first level's value. Unresolved module keys give nothing rather than a
// guess.
internal fun LayoutEntry.captureValuesOf(path: String): Map<String, String> {
    if (captureVariants.isEmpty()) return emptyMap()
    val target = matchTargetOf(path) ?: return emptyMap()
    val groups = glob.matchGroups(target) ?: return emptyMap()
    val values = LinkedHashMap<String, String>()
    for (variant in captureVariants) {
        variant.moduleCapture?.let { values.putModuleValues(it, modulePath) }
        for (occurrence in occurrenceValuesOf(variant.pathCaptures, glob, groups) { it.segmentIndex }) {
            if (occurrence.capture.transform == null) values.putIfAbsent(occurrence.capture.name, occurrence.value)
        }
    }
    return values
}

internal fun LayoutEntry.captureValuesOfFileName(fileName: String): Map<String, String> {
    if (kind != LayoutEntryKind.File || captureVariants.isEmpty()) return emptyMap()
    val levels = path.split(Glob.PATH_SEPARATOR)
    val lastLevel = levels.lastIndex
    // One level of a pattern that compiled whole, so it compiles on its own too.
    val nameGlob = Glob.compile(levels.last())
    val groups = nameGlob.matchGroups(fileName) ?: return emptyMap()
    val values = LinkedHashMap<String, String>()
    for (variant in captureVariants) {
        val onName = variant.pathCaptures.filter { it.segmentIndex == lastLevel }
        // Every capture of this compiled level sits on its level 0.
        for (occurrence in occurrenceValuesOf(onName, nameGlob, groups) { 0 }) {
            if (occurrence.capture.transform == null) values.putIfAbsent(occurrence.capture.name, occurrence.value)
        }
    }
    return values
}

// The single rule shared with LayoutIndex.rolesOf and FileConstraintCoverage.covers, so a constraint never covers
// a file the check reports.
internal fun LayoutEntry.allows(path: String): Boolean {
    val target = matchTargetOf(path) ?: return false
    return glob.matches(target) && captureMismatchOf(path) == null
}

internal class CaptureOccurrenceValue(val capture: PathCapture, val value: String)

internal class CaptureOccurrenceDetail(
    val name: String,
    val transform: CaptureTransform?,
    val level: Int,
    val value: String,
    val expected: String?,
)

internal class CaptureMismatchDetail(
    val variant: LayoutCaptures,
    val occurrences: List<CaptureOccurrenceDetail>,
    val expectedPath: String?,
)

// A variant that is not bound allows every file the pattern matches, so one such variant allows the file whatever
// the others say.
internal fun LayoutEntry.captureMismatchOf(path: String): CaptureMismatchDetail? {
    if (captureVariants.isEmpty() || captureVariants.any { !it.isBound }) return null
    val target = matchTargetOf(path) ?: return null
    val groups = glob.matchGroups(target) ?: return null
    var first: CaptureMismatchDetail? = null
    for (variant in captureVariants) {
        val detail = mismatchOf(variant, path, groups) ?: return null
        if (first == null) first = detail
    }
    return first
}

private fun LayoutEntry.matchTargetOf(path: String): String? = when (kind) {
    LayoutEntryKind.File -> path
    // A file at the root has no directory to match: the root is no directory a role can claim,
    // and an empty one would be matched by a `*`, which no real level is.
    LayoutEntryKind.AnyFile -> if ('/' in path) path.substringBeforeLast('/') else null
    else -> null
}

private class BoundOccurrence(
    val name: String,
    val transform: CaptureTransform?,
    val level: Int,
    val value: String,
    val capture: PathCapture?,
)

private fun LayoutEntry.mismatchOf(variant: LayoutCaptures, path: String, groups: List<String>): CaptureMismatchDetail? {
    val moduleValues = variant.moduleCapture?.let { moduleOccurrencesOf(it, modulePath) }.orEmpty()
    val pathValues = occurrenceValuesOf(variant.pathCaptures, glob, groups) { it.segmentIndex }
    val moduleLevel = moduleDirectoryLevel()
    val occurrences = moduleValues.map { (name, value) -> BoundOccurrence(name, null, moduleLevel, value, capture = null) } +
        pathValues.map { BoundOccurrence(it.capture.name, it.capture.transform, it.capture.segmentIndex, it.value, it.capture) }

    val plainCount = occurrences.filter { it.transform == null }.groupingBy { it.name }.eachCount()
    val derivedNames = occurrences.filter { it.transform != null }.map { it.name }.toSet()
    val bound = occurrences.filter { (plainCount[it.name] ?: 0) > 1 || it.name in derivedNames }
    if (bound.isEmpty()) return null

    val sourceOf = LinkedHashMap<String, String>()
    for (occurrence in bound) if (occurrence.transform == null) sourceOf.putIfAbsent(occurrence.name, occurrence.value)

    var mismatched = false
    var expectedUnknown = false
    val expectedOf = HashMap<PathCapture, String>()
    val sourcesSeen = HashSet<String>()
    val details = bound.map { occurrence ->
        val source = sourceOf[occurrence.name]
        val expected = when {
            // The first plain occurrence is what the others are held to.
            occurrence.transform == null && sourcesSeen.add(occurrence.name) -> null
            source == null -> {
                // A derived occurrence whose plain capture is only on an unresolved module key.
                expectedUnknown = true
                null
            }
            else -> occurrence.transform?.apply?.invoke(source) ?: source
        }
        if (expected != null && expected != occurrence.value) mismatched = true
        occurrence.capture?.let { expectedOf[it] = expected ?: occurrence.value }
        CaptureOccurrenceDetail(occurrence.name, occurrence.transform, occurrence.level, occurrence.value, expected)
    }
    if (!mismatched) return null

    val expectedPath = if (expectedUnknown) null else expectedPathOf(variant, path, expectedOf)
    return CaptureMismatchDetail(variant, details, expectedPath)
}

private fun LayoutEntry.expectedPathOf(
    variant: LayoutCaptures,
    path: String,
    expectedOf: Map<PathCapture, String>,
): String {
    val target = matchTargetOf(path) ?: return path
    val ranges = glob.matchRanges(target) ?: return path
    val replacements = variant.pathCaptures.mapNotNull { capture ->
        val expected = expectedOf[capture] ?: return@mapNotNull null
        val range = groupIndexOf(glob, capture.segmentIndex, capture.wildcardIndex)?.let { ranges.getOrNull(it) }
        range?.let { it to expected }
    }
    val expected = replacements.sortedByDescending { it.first.first }
        .fold(target) { text, (range, value) -> text.replaceRange(range, value) }
    return if (kind == LayoutEntryKind.AnyFile) "$expected/${path.substringAfterLast('/')}" else expected
}

// A `**` is never on a level with a capture, so a level's single-level groups are its `*`s left to right.
private inline fun occurrenceValuesOf(
    captures: List<PathCapture>,
    glob: Glob,
    groups: List<String>,
    levelOf: (PathCapture) -> Int,
): List<CaptureOccurrenceValue> = captures.mapNotNull { capture ->
    groupValueOf(glob, groups, levelOf(capture), capture.wildcardIndex)?.let { CaptureOccurrenceValue(capture, it) }
}

private fun groupValueOf(glob: Glob, groups: List<String>, level: Int, wildcardIndex: Int): String? =
    groupIndexOf(glob, level, wildcardIndex)?.let { groups.getOrNull(it) }

private fun groupIndexOf(glob: Glob, level: Int, wildcardIndex: Int): Int? =
    glob.groupKinds.indices.filter {
        glob.groupKinds[it] == GlobGroupKind.Single && glob.groupSegments[it] == level
    }.getOrNull(wildcardIndex)

private fun LayoutEntry.moduleDirectoryLevel(): Int {
    val levels = path.split(Glob.PATH_SEPARATOR).size
    val inModule = if (pathInModule.isEmpty()) 0 else pathInModule.split(Glob.PATH_SEPARATOR).size
    return levels - inModule - 1
}

// Read without the project, the key is itself, with `*` or `<name>` where a module's name belongs. Empty for
// unnamed, partly named or unresolved keys.
private fun moduleOccurrencesOf(capture: ModuleCapture, modulePath: String?): List<Pair<String, String>> {
    if (modulePath == null || capture.names.isEmpty()) return emptyList()
    if (modulePath.any { it in UNRESOLVED_MODULE_CHARACTERS }) return emptyList()
    // Re-compiling a key that already compiled once: nothing here can throw, so which site is
    // blamed does not matter.
    val pattern = ModulePattern.compile(capture.modulePattern, DeclarationSite.Unknown)
    // `ModuleCapture.names` drops the `*`s that were left unnamed, so only a key naming every
    // `*` says which name is which. `**` is never named and always last, after them.
    if (capture.names.size != pattern.singleWildcardCount) return emptyList()
    val wildcards = pattern.match(ModulePath.of(modulePath)) ?: return emptyList()
    return capture.names.mapIndexedNotNull { index, name -> wildcards.getOrNull(index)?.let { name to it } }
}

private fun MutableMap<String, String>.putModuleValues(capture: ModuleCapture, modulePath: String?) {
    for ((name, value) in moduleOccurrencesOf(capture, modulePath)) putIfAbsent(name, value)
}

private const val UNRESOLVED_MODULE_CHARACTERS: String = "*<>"
