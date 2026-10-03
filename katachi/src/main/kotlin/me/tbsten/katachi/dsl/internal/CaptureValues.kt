package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.ModulePath

/**
 * What this entry's `capture("...")` names took from [path], by name: empty when the entry does
 * not match [path] or names nothing.
 *
 * A [LayoutEntryKind.File] entry is matched against [path] itself and an
 * [LayoutEntryKind.AnyFile] entry against its directory, the rule [FileConstraintCoverage.covers]
 * covers a file by. No other kind names a file.
 *
 * Two names are left out rather than guessed, so a value here is always the one the layout meant:
 * - a capture on a level that also holds an unnamed `*` (`"*-${capture("b")}.kt"`): the entry
 *   keeps which level a name is on, not which `*` of it;
 * - a module key that names only some of its `*`s, or that was never resolved to a module (a
 *   layout read without the project, whose module path is still a pattern).
 */
internal fun LayoutEntry.captureValuesOf(path: String): Map<String, String> {
    if (captureVariants.isEmpty()) return emptyMap()
    val target = when (kind) {
        LayoutEntryKind.File -> path
        LayoutEntryKind.AnyFile -> path.substringBeforeLast('/', missingDelimiterValue = "")
        else -> return emptyMap()
    }
    val groups = glob.matchGroups(target) ?: return emptyMap()
    val values = LinkedHashMap<String, String>()
    for (variant in captureVariants) {
        variant.moduleCapture?.let { values.putModuleValues(it, modulePath) }
        values.putLevelValues(variant.pathCaptures, glob, groups) { it.segmentIndex }
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
        values.putLevelValues(onName, nameGlob, groups) { 0 }
    }
    return values
}

/**
 * Pairs each capture with the `*` it names: the k-th capture on a level is the k-th `*` of that
 * level, since `capture("...")` tokens read back out of a key left to right (see
 * [parseCaptureSegment]). A level whose `*`s outnumber its captures also holds an unnamed `*`,
 * and which one is which was not kept, so that level gives no value at all.
 */
private inline fun MutableMap<String, String>.putLevelValues(
    captures: List<PathCapture>,
    glob: Glob,
    groups: List<String>,
    levelOf: (PathCapture) -> Int,
) {
    for ((level, onLevel) in captures.groupBy(levelOf)) {
        val singles = glob.groupKinds.indices.filter {
            glob.groupKinds[it] == GlobGroupKind.Single && glob.groupSegments[it] == level
        }
        if (singles.size != onLevel.size) continue
        onLevel.forEachIndexed { index, capture -> putIfAbsent(capture.name, groups[singles[index]]) }
    }
}

/**
 * The values a module key's names take from the module this entry was resolved to.
 *
 * [modulePath] is the module the entry sits in. Read without the project it is the key itself,
 * holding `*` or a `<name>` placeholder where a module's name belongs -- a character Gradle
 * refuses in a project name, so a path holding one names no real module and gives nothing.
 */
private fun MutableMap<String, String>.putModuleValues(capture: ModuleCapture, modulePath: String?) {
    if (modulePath == null || capture.names.isEmpty()) return
    if (modulePath.any { it in UNRESOLVED_MODULE_CHARACTERS }) return
    // Re-compiling a key that already compiled once: nothing here can throw, so which site is
    // blamed does not matter.
    val pattern = ModulePattern.compile(capture.modulePattern, DeclarationSite.Unknown)
    // `ModuleCapture.names` drops the `*`s that were left unnamed, so only a key naming every
    // `*` says which name is which. `**` is never named and always last, after them.
    if (capture.names.size != pattern.singleWildcardCount) return
    val wildcards = pattern.match(ModulePath.of(modulePath)) ?: return
    capture.names.forEachIndexed { index, name -> wildcards.getOrNull(index)?.let { putIfAbsent(name, it) } }
}

/** What only an unresolved module key holds: its own `*`, or a `<name>` placeholder. */
private const val UNRESOLVED_MODULE_CHARACTERS: String = "*<>"
