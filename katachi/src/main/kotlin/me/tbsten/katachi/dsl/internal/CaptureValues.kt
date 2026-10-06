package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.ModulePath

/**
 * What this entry's plain `capture("...")` names took from [path], by name: empty when the
 * entry does not match [path] or names nothing.
 *
 * A [LayoutEntryKind.File] entry is matched against [path] itself and an
 * [LayoutEntryKind.AnyFile] entry against its directory, the rule [FileConstraintCoverage.covers]
 * covers a file by. No other kind names a file.
 *
 * A name written on two levels reads the first level's value: [captureMismatchOf] has already
 * held the two to the same value for any file the entry allows. A derived occurrence has no
 * name of its own and gives nothing. A module key that names only some of its `*`s, or that was
 * never resolved to a module (a layout read without the project, whose module path is still a
 * pattern), gives nothing rather than a guess.
 */
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

/**
 * What this entry's file name level alone takes from [fileName]: `"${capture("name")}ViewModel.kt"`
 * gives `name = Home` for `HomeViewModel.kt`. Empty for an entry that is not a file, whose last
 * level does not match [fileName], or that names nothing there.
 *
 * For an example that is only a file name, where the levels above it are not known.
 */
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

/**
 * Whether this entry allows a file at [path]: its pattern matches (the file itself, or its
 * directory for an `anyFile()` entry), and no bound capture holds the file back. The one rule
 * [me.tbsten.katachi.check.internal.LayoutIndex.rolesOf] and [FileConstraintCoverage.covers]
 * share, so a constraint never covers a file the check reports.
 */
internal fun LayoutEntry.allows(path: String): Boolean {
    val target = matchTargetOf(path) ?: return false
    return glob.matches(target) && captureMismatchOf(path) == null
}

/** One occurrence of a variant, with what the file holds at it. */
internal class CaptureOccurrenceValue(val capture: PathCapture, val value: String)

/**
 * One occurrence of a bound name, as a `[CaptureMismatch]` lists it. [expected] is `null` for the
 * first plain occurrence of the name, whose value the others are held to.
 */
internal class CaptureOccurrenceDetail(
    val name: String,
    val transform: CaptureTransform?,
    /** 0-based level of the file's path. */
    val level: Int,
    val value: String,
    val expected: String?,
)

/**
 * Why a bound variant held a file back: every occurrence of a bound name, and the path the file
 * would have if each level read what the first plain occurrence of its name reads -- `null`
 * when a value that path needs could not be read.
 */
internal class CaptureMismatchDetail(
    val variant: LayoutCaptures,
    val occurrences: List<CaptureOccurrenceDetail>,
    val expectedPath: String?,
)

/**
 * What holds a file at [path] back from this entry, or `null` when the entry allows it (or does
 * not match it at all, which is a question for the pattern, not for this).
 *
 * Decided per variant: a variant that is not [LayoutCaptures.isBound] -- [LayoutCaptures.NONE]
 * included -- allows every file the pattern matches, so one such variant allows the file
 * whatever the others say. A bound variant allows the file only when every plain occurrence of
 * a name reads the first one's value and every derived occurrence reads its conversion of that
 * value, as plain string equality. When every variant holds the file back, the first declared
 * one says why.
 *
 * A name a module key gives is compared only once the key was resolved to a module, the rule
 * [captureValuesOf] reads it by; a layout read without the project compares nothing on it.
 */
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

/** [path] itself for a file entry, its directory for an `anyFile()` entry, `null` for a kind that names no file. */
private fun LayoutEntry.matchTargetOf(path: String): String? = when (kind) {
    LayoutEntryKind.File -> path
    // A file at the root has no directory to match: the root is no directory a role can claim,
    // and an empty one would be matched by a `*`, which no real level is.
    LayoutEntryKind.AnyFile -> if ('/' in path) path.substringBeforeLast('/') else null
    else -> null
}

/** One occurrence with its value, before the expected value is known. [capture] is `null` for a module key's. */
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

/**
 * [path] with the part each bound occurrence matched replaced by its expected value, and every
 * other character -- unnamed `*` and `**`, `?`, `[...]`, `{...}`, escapes -- kept as the file has it.
 * The parts are found by where [Glob.matchRanges] says each wildcard matched, so only what a
 * capture named is ever rewritten.
 */
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

/**
 * The value each of [captures] read, in order: the [PathCapture.wildcardIndex]-th single-level
 * group of its level. A `**` is never on a level with a capture, so the single-level groups of
 * one level are exactly its `*`s left to right. A capture whose group is not there (an entry
 * whose pattern has fewer `*`s than captures, which flattening never produces) is left out.
 */
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

/** The index into [Glob.groupKinds] of the [wildcardIndex]-th `*` of [level], or `null` when it has fewer. */
private fun groupIndexOf(glob: Glob, level: Int, wildcardIndex: Int): Int? =
    glob.groupKinds.indices.filter {
        glob.groupKinds[it] == GlobGroupKind.Single && glob.groupSegments[it] == level
    }.getOrNull(wildcardIndex)

/** The 0-based level of the last segment of this entry's module directory, or `-1` for the root project or no module. */
private fun LayoutEntry.moduleDirectoryLevel(): Int {
    val levels = path.split(Glob.PATH_SEPARATOR).size
    val inModule = if (pathInModule.isEmpty()) 0 else pathInModule.split(Glob.PATH_SEPARATOR).size
    return levels - inModule - 1
}

/**
 * The values a module key's names take from the module this entry was resolved to, as
 * `name to value` pairs in the key's order: a name the key gave twice appears twice, once per
 * `*`. Empty when the key gave no name, named only some of its `*`s, or was never resolved.
 *
 * [modulePath] is the module the entry sits in. Read without the project it is the key itself,
 * holding `*` or a `<name>` placeholder where a module's name belongs -- a character Gradle
 * refuses in a project name, so a path holding one names no real module and gives nothing.
 */
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

/**
 * [moduleOccurrencesOf] by name, the first `*` of a name keeping it. A key naming two `*`s alike
 * was only expanded to modules where they read the same, so the second is never different.
 */
private fun MutableMap<String, String>.putModuleValues(capture: ModuleCapture, modulePath: String?) {
    for ((name, value) in moduleOccurrencesOf(capture, modulePath)) putIfAbsent(name, value)
}

/** What only an unresolved module key holds: its own `*`, or a `<name>` placeholder. */
private const val UNRESOLVED_MODULE_CHARACTERS: String = "*<>"
