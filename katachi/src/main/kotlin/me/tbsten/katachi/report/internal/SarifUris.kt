package me.tbsten.katachi.report.internal

/**
 * [relativePath], `/` separated, as the `artifactLocation.uri` of a location: each segment
 * percent-encoded the way RFC 3986 asks of a URI path, so that a space, a `#`, a `?` or a `%` in
 * a directory or file name cannot cut the reference short or start a fragment.
 *
 * Only ASCII that a path segment may not carry is encoded. Non-ASCII stays as it is, like
 * everywhere else in the document: GitHub reads the file as an IRI, and a Japanese file name
 * is more useful to a reader raw than as `%E3%...`.
 */
internal fun sarifUriOf(relativePath: String): String =
    relativePath.split('/').joinToString("/") { encodeSegment(it) }

/** ASCII a `pchar` of RFC 3986 may be without encoding: unreserved, sub-delims, `:` and `@`. */
private const val SEGMENT_SAFE: String =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=:@"

private fun encodeSegment(segment: String): String {
    if (segment.all { it.code > 0x7F || it in SEGMENT_SAFE }) return segment
    return buildString {
        for (ch in segment) {
            if (ch.code > 0x7F || ch in SEGMENT_SAFE) {
                append(ch)
            } else {
                append('%')
                append(HEX[ch.code shr 4])
                append(HEX[ch.code and 0x0F])
            }
        }
    }
}

private const val HEX: String = "0123456789ABCDEF"
