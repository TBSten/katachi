package me.tbsten.katachi.check.internal

import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.GlobLexer
import me.tbsten.katachi.dsl.internal.GlobToken

/**
 * One level of a declared directory pattern, read once per declaration so that a file is
 * matched against it without compiling anything again.
 */
internal sealed interface NearbySegment {
    /** A level with no wildcard, with its escapes undone. */
    class Literal(val text: String) : NearbySegment

    /** A level holding a `*`, `?` or `[...]`: the whole level, or part of it (`ui-*`). A `{...}` is written out before this. */
    class Wildcard(val text: String, val glob: Glob) : NearbySegment

    /** A `**` level, standing for zero levels or more. */
    object DoubleStar : NearbySegment
}

private const val DOUBLE_STAR = "**"

internal fun nearbySegmentsOf(directoryPattern: String): List<NearbySegment> =
    directoryPattern.split('/').map { segment ->
        if (segment == DOUBLE_STAR) return@map NearbySegment.DoubleStar
        // The whole pattern compiled already, so a level of it compiles too; what is asked
        // here is only whether the level holds a wildcard once escapes are read.
        val glob = Glob.compile(segment)
        if (glob.hasWildcard) NearbySegment.Wildcard(segment, glob) else NearbySegment.Literal(GlobLexer.unescapeLiteral(segment))
    }

/**
 * [pattern] with [directory] filled into its wildcards, or `null` when the directory is too
 * shallow to fill one.
 *
 * A wildcard level takes the level of [directory] at the same position: as it is when it matches,
 * and with every `*` replaced by it when it does not (`ui-*` against `home` reads `ui-home`).
 * A level that does not match and holds a `?` or `[...]` cannot be filled, and the place is dropped.
 * A `**` takes as many levels as are left over once the levels after it are lined up with
 * the end of [directory].
 */
internal fun instantiate(pattern: List<NearbySegment>, directory: List<String>): List<String>? {
    val filled = ArrayList<String>(directory.size + pattern.size)
    var next = 0
    for ((index, segment) in pattern.withIndex()) {
        when (segment) {
            is NearbySegment.Literal -> {
                filled += segment.text
                next++
            }

            is NearbySegment.Wildcard -> {
                val level = directory.getOrNull(next) ?: return null
                // A `?` or `[...]` that does not hold the file's own level has no level to be
                // filled with, so there is no place to offer.
                filled += (if (segment.glob.matches(level)) level else segment.filledWith(level)) ?: return null
                next++
            }

            NearbySegment.DoubleStar -> {
                // The literals before it may already have run past the end of a shallow
                // directory; a `**` then stands for no level at all.
                val start = next.coerceAtMost(directory.size)
                val after = pattern.size - index - 1
                val taken = (directory.size - start - after).coerceAtLeast(0)
                filled += directory.subList(start, start + taken)
                next += taken
            }
        }
    }
    return filled
}

/**
 * Whether a file named [fileName] could be declared by [filePattern], read from the pattern's
 * extension alone. Each way `{a,b}` can be written out is read on its own, and one accepting
 * the name is enough.
 *
 * What follows the last `.` outside `{...}` and `[...]` is the extension pattern: a wildcard
 * one is matched against the name's extension as a glob, and a literal one is compared as
 * text. A pattern with no such `.` takes every name when it holds a wildcard, and a name
 * without an extension only when it does not.
 */
internal fun acceptsExtension(filePattern: String?, fileName: String): Boolean {
    if (filePattern == null) return true
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
    // Too many ways to write it out to tell apart: leave the decision to the name check.
    val patterns = GlobLexer.expandBraces(filePattern) ?: return true
    return patterns.any { acceptsExtensionOf(it, extension) }
}

private fun acceptsExtensionOf(pattern: String, extension: String): Boolean {
    val dot = GlobLexer.scanLeniently(pattern).lastOrNull { it is GlobToken.Literal && it.character == '.' }?.start
        ?: return GlobLexer.hasGlobWildcard(pattern) || extension.isEmpty()
    val patternExtension = pattern.substring(dot + 1)
    return if (GlobLexer.hasGlobWildcard(patternExtension)) {
        Glob.compile(patternExtension).matches(extension)
    } else {
        GlobLexer.unescapeLiteral(patternExtension) == extension
    }
}

/** The edit distance between two lists of levels: one per level inserted, deleted or replaced. */
internal fun segmentDistance(left: List<String>, right: List<String>): Int {
    var previous = IntArray(right.size + 1) { it }
    var current = IntArray(right.size + 1)
    for (row in 1..left.size) {
        current[0] = row
        for (column in 1..right.size) {
            val replaced = previous[column - 1] + if (left[row - 1] == right[column - 1]) 0 else 1
            current[column] = minOf(replaced, previous[column] + 1, current[column - 1] + 1)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.size]
}

/**
 * The characters that [segmentDistance]'s edits touch: the Levenshtein distance of each
 * replaced pair of levels, and the whole length of each level inserted or deleted.
 *
 * Among the alignments with the fewest level edits, the one touching the fewest characters
 * is taken, so that the answer does not depend on which of two equal alignments a table
 * happened to be filled with.
 */
internal fun characterDistance(left: List<String>, right: List<String>): Int {
    val rows = left.size + 1
    val columns = right.size + 1
    val levels = Array(rows) { IntArray(columns) }
    val characters = Array(rows) { IntArray(columns) }
    for (row in 1 until rows) {
        levels[row][0] = row
        characters[row][0] = characters[row - 1][0] + left[row - 1].length
    }
    for (column in 1 until columns) {
        levels[0][column] = column
        characters[0][column] = characters[0][column - 1] + right[column - 1].length
    }
    for (row in 1 until rows) {
        for (column in 1 until columns) {
            val same = left[row - 1] == right[column - 1]
            var bestLevels = levels[row - 1][column - 1] + if (same) 0 else 1
            var bestCharacters = characters[row - 1][column - 1] +
                if (same) 0 else levenshtein(left[row - 1], right[column - 1])
            val deletedLevels = levels[row - 1][column] + 1
            val deletedCharacters = characters[row - 1][column] + left[row - 1].length
            if (deletedLevels < bestLevels || (deletedLevels == bestLevels && deletedCharacters < bestCharacters)) {
                bestLevels = deletedLevels
                bestCharacters = deletedCharacters
            }
            val insertedLevels = levels[row][column - 1] + 1
            val insertedCharacters = characters[row][column - 1] + right[column - 1].length
            if (insertedLevels < bestLevels || (insertedLevels == bestLevels && insertedCharacters < bestCharacters)) {
                bestLevels = insertedLevels
                bestCharacters = insertedCharacters
            }
            levels[row][column] = bestLevels
            characters[row][column] = bestCharacters
        }
    }
    return characters[left.size][right.size]
}

internal fun levenshtein(left: String, right: String): Int {
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    for (row in 1..left.length) {
        current[0] = row
        for (column in 1..right.length) {
            val replaced = previous[column - 1] + if (left[row - 1] == right[column - 1]) 0 else 1
            current[column] = minOf(replaced, previous[column] + 1, current[column - 1] + 1)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}

/**
 * [NearbySegment.Wildcard.text] with every capturing `*` replaced by [level] and escapes undone,
 * or `null` when it holds a `?` or `[...]`, which have no value to be replaced by.
 */
private fun NearbySegment.Wildcard.filledWith(level: String): String? {
    if (GlobLexer.hasNonCapturingWildcard(text)) return null
    return GlobLexer.unescapeLiteral(GlobLexer.replaceCapturingStars(text) { level })
}
