package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiAdjacentCaptureException
import me.tbsten.katachi.dsl.KatachiStrayCaptureTokenException

/**
 * Wraps [name] between two characters of the Unicode private use area, which never appear in a
 * layout key someone typed by hand.
 *
 * `capture(name)` hands this back as an ordinary `String`, so it can be embedded anywhere a
 * layout key is built with Kotlin's own string templates -- `"${capture("x")}Screen"`,
 * `"feature-${capture("x")}"`, `capture("x") / "src"`. Every place that reads a finished key
 * (a file name, a directory key, a module path) looks for this token before handing the key to
 * [me.tbsten.katachi.dsl.internal.Glob]: [parseCaptureSegment] strips it back out, and
 * substitutes a plain `*` for the glob compiler, which is what keeps a named capture checked
 * exactly like an unnamed wildcard.
 */
internal fun captureToken(name: String): String = "$CAPTURE_TOKEN_START$name$CAPTURE_TOKEN_END"

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
    val name = if (end > start) text.substring(start + 1, end) else text.substring(start + 1)
    throw KatachiStrayCaptureTokenException(where = where, name = name, declaredAt = declaredAt)
}

private const val CAPTURE_TOKEN_START: Char = ''
private const val CAPTURE_TOKEN_END: Char = ''

/** One piece of a [CaptureSegment]: plain text, or the name a `capture(...)` token stood for. */
internal sealed interface SegmentPart {
    data class Literal(val text: String) : SegmentPart
    data class Capture(val name: String) : SegmentPart
}

/**
 * One `/`-level of a layout key, once its `capture(...)` tokens have been read back out.
 *
 * Built once, by [parseCaptureSegment], and kept on the [LayoutNode] that segment became: the
 * check reads [globSegment], where every token is already a plain `*`, and template generation
 * reads [parts] to tell which parts of the finished path a `--arg` value fills in.
 */
internal class CaptureSegment(
    val parts: List<SegmentPart>,
) {
    /** The names this segment's tokens carry, in the order they appear. */
    val names: List<String> = parts.filterIsInstance<SegmentPart.Capture>().map { it.name }

    /** [parts], with every capture read as a plain `*` -- what the glob compiler is given. */
    val globSegment: String = parts.joinToString("") { part ->
        when (part) {
            is SegmentPart.Literal -> part.text
            is SegmentPart.Capture -> "*"
        }
    }

    /** For a message: every token shown as `${capture("name")}`, exactly as it was written. */
    fun display(): String = parts.joinToString("") { part ->
        when (part) {
            is SegmentPart.Literal -> part.text
            is SegmentPart.Capture -> "\${capture(\"${part.name}\")}"
        }
    }

    override fun toString(): String = "CaptureSegment(${display()})"
}

/**
 * Reads [segment] back into a [CaptureSegment], or `null` when it holds no `capture(...)` token
 * at all -- the common case, for which nothing further has to be done.
 *
 * @throws KatachiAdjacentCaptureException when two tokens sit next to each other with no literal
 *   between them, or a token sits next to a literal `*`: substituting both for `*` would fuse
 *   into `**`, which the glob compiler reads as the unrelated "any depth" wildcard rather than
 *   as two single levels.
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
            parts += SegmentPart.Capture(segment.substring(index + 1, end))
            index = end + 1
        } else {
            literal.append(character)
            index++
        }
    }
    if (literal.isNotEmpty()) parts += SegmentPart.Literal(literal.toString())
    val result = CaptureSegment(parts)
    requireNoAdjacentCaptures(result, declaredAt)
    return result
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
