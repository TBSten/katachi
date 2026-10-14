package me.tbsten.katachi.intellij.data.baseline

/**
 * The entries of a baseline, as katachi files them: by rule label (`UnexpectedFile`, `UnexpectedDirectory`,
 * `CaptureMismatch`) and path relative to the project root. katachi keys an entry by check, rule and path, never by line.
 * Only the entries of `LayoutCheck` are kept (see [LAYOUT_CHECK]): it is the one check that reports these three rules, so
 * an entry of another check under the same rule label is not the layout's. A `count` is not read either; whether a path turned up more often than the entry allows is the test's to say.
 *
 * ```kotlin
 * val ledger = BaselineLedger(mapOf("UnexpectedFile" to setOf("src/Legacy.kt")))
 * ledger.holdsFile("src/Legacy.kt")   // true
 * ```
 */
internal class BaselineLedger(private val pathsByRule: Map<String, Set<String>>) {
    /** Whether an entry of [rule] is filed under exactly [path] (katachi compares the path strings as they are). */
    fun holds(rule: String, path: String): Boolean = path in pathsByRule[rule].orEmpty()

    /** Whether an `UnexpectedFile` entry holds the file at [path]. */
    fun holdsFile(path: String): Boolean = holds(RULE_UNEXPECTED_FILE, path)

    /**
     * Whether an `UnexpectedDirectory` entry is filed under exactly [path]. katachi reports the top-most directory no role
     * knows, once, and does not walk into it: the entry of that directory is the only one that holds it, and a file or a
     * directory below it has no violation (and so no entry) of its own.
     */
    fun holdsDirectory(path: String): Boolean = holds(RULE_UNEXPECTED_DIRECTORY, path)

    /** Whether a `CaptureMismatch` entry holds the file at [path]. */
    fun holdsCaptureMismatch(path: String): Boolean = holds(RULE_CAPTURE_MISMATCH, path)

    override fun equals(other: Any?): Boolean = other is BaselineLedger && other.pathsByRule == pathsByRule
    override fun hashCode(): Int = pathsByRule.hashCode()
    override fun toString(): String = "BaselineLedger($pathsByRule)"

    companion object {
        /** The key of the check that reports `UnexpectedFile`, `UnexpectedDirectory` and `CaptureMismatch` in a baseline file. */
        const val LAYOUT_CHECK: String = "me.tbsten.katachi.check.LayoutCheck"
        const val RULE_UNEXPECTED_FILE: String = "UnexpectedFile"
        const val RULE_UNEXPECTED_DIRECTORY: String = "UnexpectedDirectory"
        const val RULE_CAPTURE_MISMATCH: String = "CaptureMismatch"
    }
}
