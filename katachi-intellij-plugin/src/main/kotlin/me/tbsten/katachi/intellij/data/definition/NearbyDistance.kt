package me.tbsten.katachi.intellij.data.definition

internal sealed interface NearbySegment {
    class Literal(val text: String) : NearbySegment

    class Wildcard(val text: String, val glob: KatachiGlob) : NearbySegment

    object DoubleStar : NearbySegment
}

private const val DOUBLE_STAR = "**"

internal fun nearbySegmentsOf(directoryPattern: String): List<NearbySegment> =
    directoryPattern.split('/').map { segment ->
        if (segment == DOUBLE_STAR) return@map NearbySegment.DoubleStar
        // The whole pattern compiled already, so a level of it compiles too.
        val glob = KatachiGlob.compile(segment)
        if (glob.hasWildcard) NearbySegment.Wildcard(segment, glob) else NearbySegment.Literal(KatachiGlobLexer.unescapeLiteral(segment))
    }

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
                // A `?` or `[...]` that does not hold the file's own level has no level to be filled with.
                filled += (if (segment.glob.matches(level)) level else segment.filledWith(level)) ?: return null
                next++
            }

            NearbySegment.DoubleStar -> {
                // The literals before it may already have run past the end of a shallow directory; a `**` then stands for no level at all.
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

internal fun acceptsExtension(filePattern: String?, fileName: String): Boolean {
    if (filePattern == null) return true
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
    // Too many ways to write it out to tell apart: leave the decision to the name check.
    val patterns = KatachiGlobLexer.expandBraces(filePattern) ?: return true
    return patterns.any { acceptsExtensionOf(it, extension) }
}

private fun acceptsExtensionOf(pattern: String, extension: String): Boolean {
    val dot = KatachiGlobLexer.scanLeniently(pattern).lastOrNull { it is KatachiGlobToken.Literal && it.character == '.' }?.start
        ?: return KatachiGlobLexer.hasGlobWildcard(pattern) || extension.isEmpty()
    val patternExtension = pattern.substring(dot + 1)
    return if (KatachiGlobLexer.hasGlobWildcard(patternExtension)) {
        KatachiGlob.compile(patternExtension).matches(extension)
    } else {
        KatachiGlobLexer.unescapeLiteral(patternExtension) == extension
    }
}

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
            var bestCharacters = characters[row - 1][column - 1] + if (same) 0 else levenshtein(left[row - 1], right[column - 1])
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

private fun NearbySegment.Wildcard.filledWith(level: String): String? {
    if (KatachiGlobLexer.hasNonCapturingWildcard(text)) return null
    return KatachiGlobLexer.unescapeLiteral(KatachiGlobLexer.replaceCapturingStars(text) { level })
}
