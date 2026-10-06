package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiAdjacentCaptureException
import me.tbsten.katachi.dsl.KatachiCaptureInsideBracketsException
import me.tbsten.katachi.dsl.KatachiCaptureTransformOnMixedTextException
import me.tbsten.katachi.dsl.KatachiChainedCaptureTransformException
import me.tbsten.katachi.dsl.KatachiStrayCaptureTokenException
import me.tbsten.katachi.dsl.KatachiUnknownCaptureTransformException

/**
 * Wraps [name] -- and the [transform] a derived capture carries, as `name.spelling` -- between
 * two characters of the Unicode private use area, which never appear in a layout key someone
 * typed by hand.
 *
 * `capture(name)` hands this back as an ordinary `String`, so it can be embedded anywhere a
 * layout key is built with Kotlin's own string templates -- `"${capture("x")}Screen"`,
 * `"feature-${capture("x")}"`, `capture("x") / "src"`. Every place that reads a finished key
 * (a file name, a directory key, a module path) looks for this token before handing the key to
 * [me.tbsten.katachi.dsl.internal.Glob]: [parseCaptureSegment] strips it back out, and
 * substitutes a plain `*` for the glob compiler, which is what keeps a named capture matched
 * exactly like an unnamed wildcard. A name holds no `.` (see `requireValidIdentifier`), so the
 * `.` before a transform's spelling is the one separator the token needs.
 */
internal fun captureToken(name: String, transform: CaptureTransform? = null): String =
    "$CAPTURE_TOKEN_START$name${transform?.let { "$TRANSFORM_SEPARATOR${it.spelling}" }.orEmpty()}$CAPTURE_TOKEN_END"

/** Whether [this] embeds a [captureToken] anywhere. */
internal fun String.containsCaptureToken(): Boolean = CAPTURE_TOKEN_START in this

/**
 * Refuses [text] if it embeds a `capture(...)` token: it was read somewhere other than a layout
 * key, which is the only place a token is stripped back out.
 *
 * Called wherever katachi takes a `String` from the DSL that is not itself a layout key --
 * `description`, a `fileConstraint` name, and, once a `.template { }` block has been evaluated,
 * its returned content.
 *
 * @param where a short noun phrase for the message, such as `"a description"`.
 * @throws KatachiStrayCaptureTokenException when [text] embeds a token.
 */
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

/**
 * [this], a `String` a naming conversion was applied to, as the derived capture token that
 * conversion makes of it -- or `null` when [this] holds no token at all, which is the common
 * case and the one every `nameWords` caller outside a layout key takes: nothing is parsed and no
 * stack is walked for a plain name.
 *
 * | receiver | result |
 * |---|---|
 * | no token | `null`: the caller converts the characters as it always did |
 * | one plain token and nothing else | the token with [transform] attached; the characters are not changed |
 * | one token that already carries a conversion | [KatachiChainedCaptureTransformException] |
 * | a token beside other text, or two tokens | [KatachiCaptureTransformOnMixedTextException] |
 *
 * The declaration site is read only on the way to an exception, inside this function, so it has
 * to stay a real (non-inline) frame for `captureDeclarationSite` to skip past.
 */
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

/** Every token of [text] written as `${capture("name")}` / `${capture("name").pascalCase}`, the rest kept as it is. */
internal fun displayCaptureTokens(text: String): String =
    CAPTURE_TOKEN.replace(text) { match -> displayOf(match.groupValues[1]) }

/** The names of every token in [text], in order, conversions dropped. */
private fun captureNamesIn(text: String): List<String> =
    CAPTURE_TOKEN.findAll(text).map { it.groupValues[1].substringBefore(TRANSFORM_SEPARATOR) }.toList()

/** `${capture("name")}` or `${capture("name").pascalCase}` for what sits between a token's two marks. */
private fun displayOf(inner: String): String {
    val name = inner.substringBefore(TRANSFORM_SEPARATOR)
    val transform = inner.substringAfter(TRANSFORM_SEPARATOR, missingDelimiterValue = "")
    return "\${capture(\"$name\")" + (if (transform.isEmpty()) "" else ".$transform") + "}"
}

private const val CAPTURE_TOKEN_START: Char = ''
private const val CAPTURE_TOKEN_END: Char = ''
private const val TRANSFORM_SEPARATOR: Char = '.'

private val CAPTURE_TOKEN: Regex = Regex("$CAPTURE_TOKEN_START([^$CAPTURE_TOKEN_END]*)$CAPTURE_TOKEN_END")

/** One piece of a [CaptureSegment]: plain text, or the capture a `capture(...)` token stood for. */
internal sealed interface SegmentPart {
    data class Literal(val text: String) : SegmentPart

    /** [transform] is `null` for a plain capture, which takes a `--arg` value of its own. */
    data class Capture(val name: String, val transform: CaptureTransform?) : SegmentPart
}

/**
 * One capture of a [CaptureSegment], with which `*` of the segment it is: [wildcardIndex] counts
 * every unescaped `*` the glob compiler reads out of the segment, named or not, from the left.
 */
internal data class SegmentCapture(val name: String, val transform: CaptureTransform?, val wildcardIndex: Int)

/**
 * One `/`-level of a layout key, once its `capture(...)` tokens have been read back out.
 *
 * Built once, by [parseCaptureSegment], and kept on the [LayoutNode] that segment became: the
 * check reads [globSegment], where every token is already a plain `*`, and the levels of
 * [captures] are what a bound capture is compared on and what template generation fills a
 * `--arg` value into.
 */
internal class CaptureSegment(
    val parts: List<SegmentPart>,
) {
    /** The captures of this segment, in the order they appear, each with its `*`'s index. */
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

    /** The names this segment's tokens carry, in the order they appear, a derived one's included. */
    val names: List<String> = captures.map { it.name }

    /** [parts], with every capture read as a plain `*` -- what the glob compiler is given. */
    val globSegment: String = globSegmentOf(parts)

    /** For a message: every token shown as `${capture("name")}` (`.pascalCase` kept), exactly as it was written. */
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

/**
 * Reads [segment] back into a [CaptureSegment], or `null` when it holds no `capture(...)` token
 * at all -- the common case, for which nothing further has to be done.
 *
 * @throws KatachiAdjacentCaptureException when two tokens sit next to each other with no literal
 *   between them, or a token sits next to a literal `*`: substituting both for `*` would fuse
 *   into `**`, which the glob compiler reads as the unrelated "any depth" wildcard rather than
 *   as two single levels.
 * @throws KatachiUnknownCaptureTransformException when a token names a conversion `Naming.kt`
 *   does not have -- which no layout can write, since only those conversions make a token.
 */
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

/**
 * Rejects a token that [GlobLexer] reads inside `{...}` or `[...]`: a `*` there is never handed
 * back as a value, so the name could not be read.
 *
 * @throws KatachiCaptureInsideBracketsException when a token sits inside brackets.
 */
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

/**
 * Rejects a token that abuts another wildcard -- another token, or a literal `*` -- with no
 * literal between them. A lone token with nothing beside it at all (the start or end of the
 * segment) is fine: that is the ordinary "one whole level" case `capture("x")` alone supports.
 */
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
