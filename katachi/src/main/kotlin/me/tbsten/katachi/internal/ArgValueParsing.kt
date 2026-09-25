package me.tbsten.katachi.internal

/**
 * How a `--arg` string becomes a value, for every reader of one.
 *
 * A processor's `Args` and a template's typed parameters share one command line, so they share
 * one rule: `--arg flag=True` must not be refused by one of them and read by the other. Each
 * function answers `null` rather than throwing, and the caller words the failure in its own
 * terms.
 */
internal object ArgValueParsing {
    fun int(raw: String): Int? = raw.toIntOrNull()
    fun long(raw: String): Long? = raw.toLongOrNull()
    fun short(raw: String): Short? = raw.toShortOrNull()
    fun byte(raw: String): Byte? = raw.toByteOrNull()
    fun float(raw: String): Float? = raw.toFloatOrNull()
    fun double(raw: String): Double? = raw.toDoubleOrNull()
    fun boolean(raw: String): Boolean? = raw.toBooleanStrictOrNull()
    fun char(raw: String): Char? = raw.singleOrNull()

    /**
     * Whether [raw] is a whole number that [int] refuses only because it does not fit.
     *
     * Long is tried before BigInteger because `toBigIntegerOrNull` refuses the leading `+` that
     * `toIntOrNull` accepts. Both rest on `Character.digit`, so full-width digits that overflow
     * count as out of range too. A `+` number past Long is the one case left as unreadable.
     */
    fun isOutOfIntRange(raw: String): Boolean =
        raw.toIntOrNull() == null && (raw.toLongOrNull() != null || raw.toBigIntegerOrNull() != null)

    /** The position of the candidate spelled exactly as [raw], or `null` when none is. */
    fun enumIndex(candidates: List<String>, raw: String): Int? =
        candidates.indexOf(raw).takeIf { it >= 0 }
}
