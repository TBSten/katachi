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

internal fun fillCapturedPath(
    path: String,
    variant: LayoutCaptures,
    valueOf: (PathCapture) -> String?,
): String = fillCaptures(path, variant.pathCaptures, valueOf)

internal fun PathCapture.placeholder(): String =
    transform?.let { "<$name.${it.spelling}>" } ?: ("\$" + "{" + name + "}")
