package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite

/**
 * [path] with the `*` of each of [captures] replaced by what [valueOf] answers for it, level by
 * level; an occurrence [valueOf] answers `null` for keeps its `*`.
 *
 * The one place an occurrence is written into a path, shared by the check (a mismatch's
 * expected path), template generation (the file to write), the previews (`${name}` /
 * `<name.transform>`) and a message. What each occurrence reads as is the caller's: nothing here
 * converts a value or interprets a placeholder, so the four cannot drift apart on how a value
 * lands. A `*` no capture names stays as written unless [unnamedOf] gives it a value, by level
 * and by its index among that level's `*`s -- the check fills those with what the file holds.
 *
 * An escaped `\*`, and a `*` inside `{...}` or `[...]`, is not a captured wildcard and is copied as written, as [Glob] reads it.
 */
internal fun fillCapturedSegments(
    path: String,
    captures: List<PathCapture>,
    unnamedOf: (segmentIndex: Int, wildcardIndex: Int) -> String? = { _, _ -> null },
    valueOf: (PathCapture) -> String?,
): List<String> {
    val segments = path.split('/').toMutableList()
    if (captures.isEmpty()) return segments
    for ((segmentIndex, onSegment) in captures.groupBy { it.segmentIndex }) {
        if (segmentIndex !in segments.indices) continue
        val named = HashMap<Int, String>()
        for (capture in onSegment) valueOf(capture)?.let { named[capture.wildcardIndex] = it }
        segments[segmentIndex] = GlobLexer.replaceCapturingStars(segments[segmentIndex]) { wildcardIndex ->
            named[wildcardIndex] ?: unnamedOf(segmentIndex, wildcardIndex)
        }
    }
    return segments
}

/** [fillCapturedSegments] joined back into a path. */
internal fun fillCaptures(
    path: String,
    captures: List<PathCapture>,
    valueOf: (PathCapture) -> String?,
): String = fillCapturedSegments(path, captures, valueOf = valueOf).joinToString("/")

/**
 * [path] with every capture of [variant] filled in: its path occurrences by [valueOf], and the
 * module key's `*`s by [moduleValues] when [path] still starts with the key itself.
 *
 * The second only happens to an entry flattened without the modules -- the declarations alone,
 * as a preview or a message reads them. There the key is kept as its conventional directory -- a
 * `feature` directory with a `*` level below it, for `":feature:*"` -- and filling it in the
 * same way gives the directory the values would pick. An entry flattened against a module the
 * values picked already starts with that module's directory and is left as it is. A key whose
 * names [moduleValues] does not all hold is left as it is too.
 */
internal fun fillCapturedPath(
    path: String,
    variant: LayoutCaptures,
    moduleValues: Map<String, String>,
    valueOf: (PathCapture) -> String?,
): String {
    val filled = fillCaptures(path, variant.pathCaptures, valueOf)
    val module = variant.moduleCapture ?: return filled
    val values = module.names.map { moduleValues[it] ?: return filled }
    val prefix = conventionalDirectoryOf(module.modulePattern)
    if (filled != prefix && !filled.startsWith("$prefix/")) return filled
    // Re-compiling an already-substituted pattern (`*`, not a capture token): nothing here can
    // raise KatachiAdjacentCaptureException, so which declaration site is blamed does not matter.
    val pattern = ModulePattern.compile(module.modulePattern, DeclarationSite.Unknown)
    return conventionalDirectoryOf(pattern.filledIn(values)) + filled.removePrefix(prefix)
}

/** [modulePattern] as [ModulePattern.conventionalDirectory] spells it, without compiling it again. */
private fun conventionalDirectoryOf(modulePattern: String): String =
    modulePattern.removePrefix(":").replace(':', '/')

/**
 * What [this] reads as in a preview, a message or a report's Pattern line: `${name}` for a plain
 * occurrence, which a `--arg` fills, and `<name.transform>` for a derived one, which katachi
 * fills from that value. The two spellings are what `katachiTemplates`, the template JSON, a
 * `[CaptureMismatch]` block and the IDE plugin share.
 */
internal fun PathCapture.placeholder(): String =
    transform?.let { "<$name.${it.spelling}>" } ?: ("\$" + "{" + name + "}")
