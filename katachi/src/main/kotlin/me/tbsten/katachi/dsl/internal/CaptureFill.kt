package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite

// Escaped `\*` and `*` inside `{...}` or `[...]` are not captured wildcards. Callers decide how a value reads;
// nothing here converts one.
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

internal fun fillCaptures(
    path: String,
    captures: List<PathCapture>,
    valueOf: (PathCapture) -> String?,
): String = fillCapturedSegments(path, captures, valueOf = valueOf).joinToString("/")

// Module-key `*`s are filled only for entries flattened without modules (preview/message), where the key stays as
// its conventional directory.
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

private fun conventionalDirectoryOf(modulePattern: String): String =
    modulePattern.removePrefix(":").replace(':', '/')

internal fun PathCapture.placeholder(): String =
    transform?.let { "<$name.${it.spelling}>" } ?: ("\$" + "{" + name + "}")
