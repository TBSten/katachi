package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiAdjacentCaptureException
import me.tbsten.katachi.dsl.KatachiCaptureInsideBracketsException
import me.tbsten.katachi.dsl.KatachiCaptureTransformOnMixedTextException
import me.tbsten.katachi.dsl.KatachiChainedCaptureTransformException
import me.tbsten.katachi.dsl.KatachiStrayCaptureTokenException
import me.tbsten.katachi.dsl.KatachiUnknownCaptureTransformException

// The private-use marks never appear in hand-typed keys; every reader of a finished key must strip them via
// parseCaptureSegment. A name holds no `.`, so `.` is the one separator.
internal fun captureToken(name: String, transform: CaptureTransform? = null): String =
    "$CAPTURE_TOKEN_START$name${transform?.let { "$TRANSFORM_SEPARATOR${it.spelling}" }.orEmpty()}$CAPTURE_TOKEN_END"

internal fun String.containsCaptureToken(): Boolean = CAPTURE_TOKEN_START in this

// Tokens are stripped only from layout keys; reject them wherever else a DSL String is read (description,
// constraint name, template content).
internal fun requireNoCaptureToken(text: String, where: String, declaredAt: DeclarationSite) {
    if (!text.containsCaptureToken()) return
    val start = text.indexOf(CAPTURE_TOKEN_START)
    val end = text.indexOf(CAPTURE_TOKEN_END, start + 1)
    val inner = if (end > start) text.substring(start + 1, end) else text.substring(start + 1)
    throw KatachiStrayCaptureTokenException(
        where = where,
        name = inner.substringBefore(TRANSFORM_SEPARATOR),
        declaredAt = declaredAt,
    )
}

// Must stay a real (non-inline) frame so captureDeclarationSite can skip past it; the site is read only when
// throwing.
internal fun String.asTransformedCaptureToken(transform: CaptureTransform): String? {
    if (!containsCaptureToken()) return null
    val start = indexOf(CAPTURE_TOKEN_START)
    val end = indexOf(CAPTURE_TOKEN_END, start + 1)
    val wholeString = start == 0 && end == lastIndex
    if (!wholeString) {
        throw KatachiCaptureTransformOnMixedTextException(
            text = displayCaptureTokens(this),
            names = captureNamesIn(this),
            transform = transform.spelling,
            declaredAt = captureDeclarationSite(),
        )
    }
    val inner = substring(1, end)
    val name = inner.substringBefore(TRANSFORM_SEPARATOR)
    val first = inner.substringAfter(TRANSFORM_SEPARATOR, missingDelimiterValue = "")
    if (first.isNotEmpty()) {
        throw KatachiChainedCaptureTransformException(
            name = name,
            first = first,
            second = transform.spelling,
            declaredAt = captureDeclarationSite(),
        )
    }
    return captureToken(name, transform)
}

internal fun displayCaptureTokens(text: String): String =
    CAPTURE_TOKEN.replace(text) { match -> displayOf(match.groupValues[1]) }

private fun captureNamesIn(text: String): List<String> =
    CAPTURE_TOKEN.findAll(text).map { it.groupValues[1].substringBefore(TRANSFORM_SEPARATOR) }.toList()

private fun displayOf(inner: String): String {
    val name = inner.substringBefore(TRANSFORM_SEPARATOR)
    val transform = inner.substringAfter(TRANSFORM_SEPARATOR, missingDelimiterValue = "")
    return "\${capture(\"$name\")" + (if (transform.isEmpty()) "" else ".$transform") + "}"
}

private const val CAPTURE_TOKEN_START: Char = ''
private const val CAPTURE_TOKEN_END: Char = ''
private const val TRANSFORM_SEPARATOR: Char = '.'

private val CAPTURE_TOKEN: Regex = Regex("$CAPTURE_TOKEN_START([^$CAPTURE_TOKEN_END]*)$CAPTURE_TOKEN_END")

internal sealed interface SegmentPart {
    data class Literal(val text: String) : SegmentPart

    data class Capture(val name: String, val transform: CaptureTransform?) : SegmentPart
}

internal data class SegmentCapture(val name: String, val transform: CaptureTransform?, val wildcardIndex: Int)

internal class CaptureSegment(
    val parts: List<SegmentPart>,
) {
    val captures: List<SegmentCapture> = buildList {
        // Each capture is the `*` at its own offset in [globSegment]; the `*` before it that
        // stand for a value are the ones the glob lexer calls capturing.
        val stars = GlobLexer.scanLeniently(globSegmentOf(parts)).filterIsInstance<GlobToken.Star>()
        var offset = 0
        for (part in parts) {
            when (part) {
                is SegmentPart.Literal -> offset += part.text.length
                is SegmentPart.Capture -> {
                    add(SegmentCapture(part.name, part.transform, stars.count { it.capturing && it.start < offset }))
                    offset++
                }
            }
        }
    }

    val names: List<String> = captures.map { it.name }

    val globSegment: String = globSegmentOf(parts)

    fun display(): String = parts.joinToString("") { part ->
        when (part) {
            is SegmentPart.Literal -> part.text
            is SegmentPart.Capture -> "\${capture(\"${part.name}\")" + (part.transform?.let { ".${it.spelling}" } ?: "") + "}"
        }
    }

    override fun toString(): String = "CaptureSegment(${display()})"
}

private fun globSegmentOf(parts: List<SegmentPart>): String = parts.joinToString("") { part ->
    when (part) {
        is SegmentPart.Literal -> part.text
        is SegmentPart.Capture -> "*"
    }
}

// Adjacent tokens, or a token beside a literal `*`, would fuse into `**` (any depth), so they are rejected.
internal fun parseCaptureSegment(segment: String, declaredAt: DeclarationSite): CaptureSegment? {
    if (!segment.containsCaptureToken()) return null
    val parts = mutableListOf<SegmentPart>()
    val literal = StringBuilder()
    var index = 0
    while (index < segment.length) {
        val character = segment[index]
        if (character == CAPTURE_TOKEN_START) {
            if (literal.isNotEmpty()) {
                parts += SegmentPart.Literal(literal.toString())
                literal.clear()
            }
            val end = segment.indexOf(CAPTURE_TOKEN_END, index + 1)
            val inner = segment.substring(index + 1, end)
            val name = inner.substringBefore(TRANSFORM_SEPARATOR)
            val spelling = inner.substringAfter(TRANSFORM_SEPARATOR, missingDelimiterValue = "")
            val transform = if (spelling.isEmpty()) {
                null
            } else {
                CaptureTransform.fromSpelling(spelling)
                    ?: throw KatachiUnknownCaptureTransformException(name = name, spelling = spelling)
            }
            parts += SegmentPart.Capture(name, transform)
            index = end + 1
        } else {
            literal.append(character)
            index++
        }
    }
    if (literal.isNotEmpty()) parts += SegmentPart.Literal(literal.toString())
    val result = CaptureSegment(parts)
    requireNoCapturesInsideBrackets(result, declaredAt)
    requireNoAdjacentCaptures(result, declaredAt)
    return result
}

// A `*` inside `{...}` or `[...]` is never handed back as a value, so a name there could not be read.
private fun requireNoCapturesInsideBrackets(segment: CaptureSegment, declaredAt: DeclarationSite) {
    val tokens = GlobLexer.scanLeniently(segment.globSegment)
    var offset = 0
    for (part in segment.parts) {
        when (part) {
            is SegmentPart.Literal -> offset += part.text.length
            is SegmentPart.Capture -> {
                val enclosing = tokens.firstOrNull {
                    (it is GlobToken.Alternation || it is GlobToken.CharClass) && offset >= it.start && offset < it.end
                }
                if (enclosing != null) {
                    throw KatachiCaptureInsideBracketsException(
                        key = segment.display(),
                        name = part.name,
                        opening = if (enclosing is GlobToken.Alternation) '{' else '[',
                        declaredAt = declaredAt,
                    )
                }
                offset++
            }
        }
    }
}

// A lone token covering a whole level is fine; abutting wildcards are not.
private fun requireNoAdjacentCaptures(segment: CaptureSegment, declaredAt: DeclarationSite) {
    // A token touches whatever character sits right against it: the last character of a
    // literal before it, or the first character of a literal after it.
    fun touchesFromBefore(part: SegmentPart?): Boolean =
        part is SegmentPart.Capture || (part is SegmentPart.Literal && part.text.endsWith('*'))
    fun touchesFromAfter(part: SegmentPart?): Boolean =
        part is SegmentPart.Capture || (part is SegmentPart.Literal && part.text.startsWith('*'))

    segment.parts.forEachIndexed { index, part ->
        if (part !is SegmentPart.Capture) return@forEachIndexed
        val before = segment.parts.getOrNull(index - 1)
        val after = segment.parts.getOrNull(index + 1)
        if (!touchesFromBefore(before) && !touchesFromAfter(after)) return@forEachIndexed
        val names = buildList {
            if (before is SegmentPart.Capture) add(before.name)
            add(part.name)
            if (after is SegmentPart.Capture) add(after.name)
        }.distinct()
        throw KatachiAdjacentCaptureException(key = segment.display(), names = names, declaredAt = declaredAt)
    }
}
