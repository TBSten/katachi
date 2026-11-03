package me.tbsten.katachi.report.internal

// Only ASCII a path segment may not carry is encoded; non-ASCII stays raw (GitHub reads the file as an IRI).
internal fun sarifUriOf(relativePath: String): String =
    relativePath.split('/').joinToString("/") { encodeSegment(it) }

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
