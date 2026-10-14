package me.tbsten.katachi.intellij.data.baseline

/**
 * The entries of a baseline, as katachi files them: by rule label (`UnexpectedFile`, `UnexpectedDirectory`,
 * `CaptureMismatch`, ...) and path relative to the project root. katachi keys an entry by check, rule and path, never
 * by line, and the check is dropped here: the plugin cannot tell which checks the project runs, so an entry of any check
 * counts. A `count` is not read either; whether a path turned up more often than the entry allows is the test's to say.
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
     * Whether an `UnexpectedDirectory` entry holds [path], a directory, or a directory above it. katachi reports
     * the top-most directory no role knows and does not walk into it, so the files below it have no violation of their
     * own: the entry of the directory is the only one that can hold them.
     */
    fun holdsDirectoryAbove(path: String): Boolean =
        pathsByRule[RULE_UNEXPECTED_DIRECTORY].orEmpty().any { directory -> path == directory || path.startsWith("$directory/") }

    /** Whether a `CaptureMismatch` entry holds the file at [path]. */
    fun holdsCaptureMismatch(path: String): Boolean = holds(RULE_CAPTURE_MISMATCH, path)

    override fun equals(other: Any?): Boolean = other is BaselineLedger && other.pathsByRule == pathsByRule
    override fun hashCode(): Int = pathsByRule.hashCode()
    override fun toString(): String = "BaselineLedger($pathsByRule)"

    companion object {
        const val RULE_UNEXPECTED_FILE: String = "UnexpectedFile"
        const val RULE_UNEXPECTED_DIRECTORY: String = "UnexpectedDirectory"
        const val RULE_CAPTURE_MISMATCH: String = "CaptureMismatch"
    }
}
