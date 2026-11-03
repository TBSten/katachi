package me.tbsten.katachi.internal

internal object ArgValueParsing {
    fun int(raw: String): Int? = raw.toIntOrNull()
    fun long(raw: String): Long? = raw.toLongOrNull()
    fun short(raw: String): Short? = raw.toShortOrNull()
    fun byte(raw: String): Byte? = raw.toByteOrNull()
    fun float(raw: String): Float? = raw.toFloatOrNull()
    fun double(raw: String): Double? = raw.toDoubleOrNull()
    fun boolean(raw: String): Boolean? = raw.toBooleanStrictOrNull()
    fun char(raw: String): Char? = raw.singleOrNull()

    // Long before BigInteger: toBigIntegerOrNull refuses the leading + that toIntOrNull accepts.
    // A + number past Long stays unreadable.
    fun isOutOfIntRange(raw: String): Boolean =
        raw.toIntOrNull() == null && (raw.toLongOrNull() != null || raw.toBigIntegerOrNull() != null)

    fun enumIndex(candidates: List<String>, raw: String): Int? =
        candidates.indexOf(raw).takeIf { it >= 0 }
}
