package me.tbsten.katachi.intellij.data.baseline

// Only `LayoutCheck` entries are kept: another check under the same rule label is not the layout's. `count` is not read.
internal class BaselineLedger(private val pathsByRule: Map<String, Set<String>>) {
    fun holds(rule: String, path: String): Boolean = path in pathsByRule[rule].orEmpty()

    fun holdsFile(path: String): Boolean = holds(RULE_UNEXPECTED_FILE, path)

    fun holdsDirectory(path: String): Boolean = holds(RULE_UNEXPECTED_DIRECTORY, path)

    fun holdsCaptureMismatch(path: String): Boolean = holds(RULE_CAPTURE_MISMATCH, path)

    override fun equals(other: Any?): Boolean = other is BaselineLedger && other.pathsByRule == pathsByRule
    override fun hashCode(): Int = pathsByRule.hashCode()
    override fun toString(): String = "BaselineLedger($pathsByRule)"

    companion object {
        const val LAYOUT_CHECK: String = "me.tbsten.katachi.check.LayoutCheck"
        const val RULE_UNEXPECTED_FILE: String = "UnexpectedFile"
        const val RULE_UNEXPECTED_DIRECTORY: String = "UnexpectedDirectory"
        const val RULE_CAPTURE_MISMATCH: String = "CaptureMismatch"
    }
}
