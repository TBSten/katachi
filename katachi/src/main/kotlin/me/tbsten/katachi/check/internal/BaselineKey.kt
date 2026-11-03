package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.check.CaptureMismatch
import me.tbsten.katachi.check.MissingDescription
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.UncheckedCheck
import me.tbsten.katachi.check.UncheckedDirectory
import me.tbsten.katachi.check.UncheckedFile
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnexpectedDirectory
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind

// Never include a line number: adding a line above a violation must not make it a new one.
internal data class BaselineKey(
    val check: String,
    val rule: String,
    val path: String,
    val role: String? = null,
    val constraint: String? = null,
    val declaration: String? = null,
) : Comparable<BaselineKey> {
    override fun compareTo(other: BaselineKey): Int = ORDER.compare(this, other)

    private companion object {
        val ORDER: Comparator<BaselineKey> = compareBy<BaselineKey> { it.check }
            .thenBy { it.path }
            .thenBy { it.rule }
            .thenBy(nullsFirst()) { it.role }
            .thenBy(nullsFirst()) { it.constraint }
            .thenBy(nullsFirst()) { it.declaration }
    }
}

internal enum class BaselineRule {
    ByPath,

    ByDeclaration,

    NotHeldBack,
}

internal fun baselineRuleOf(violation: Violation): BaselineRule = when (violation) {
    is UnexpectedFile -> BaselineRule.ByPath
    is UnexpectedDirectory -> BaselineRule.ByPath
    is CaptureMismatch -> BaselineRule.ByPath
    is MissingFile -> BaselineRule.ByPath
    is UnsatisfiedFileConstraint -> BaselineRule.ByDeclaration
    // What the run could not look at is a hole, not a violation. Holding it back would make the
    // hole permanent while the report looked complete.
    is UncheckedFile -> BaselineRule.NotHeldBack
    is UncheckedDirectory -> BaselineRule.NotHeldBack
    is UncheckedCheck -> BaselineRule.NotHeldBack
    is UncheckedFileConstraint -> BaselineRule.NotHeldBack
    // Warnings never fail a run, so there is nothing to hold back.
    is AmbiguousLayout -> BaselineRule.NotHeldBack
    is MissingDescription -> BaselineRule.NotHeldBack
    // The ledger would be holding back its own complaint about itself.
    is StaleBaselineEntry -> BaselineRule.NotHeldBack
    else -> when {
        violation.severity != Severity.Error -> BaselineRule.NotHeldBack
        violation.kind == ViolationKind.Failed || violation.kind == ViolationKind.Stale -> BaselineRule.NotHeldBack
        else -> BaselineRule.ByPath
    }
}

internal fun baselineKeyOf(check: String, violation: Violation): BaselineKey? {
    if (violation.severity != Severity.Error) return null
    return when (baselineRuleOf(violation)) {
        BaselineRule.NotHeldBack -> null
        BaselineRule.ByPath -> BaselineKey(check = check, rule = violation.label, path = violation.path)
        BaselineRule.ByDeclaration -> {
            val constraint = violation as? UnsatisfiedFileConstraint
            BaselineKey(
                check = check,
                rule = violation.label,
                path = violation.path,
                role = constraint?.role?.qualifiedName,
                constraint = constraint?.constraintName,
                declaration = constraint?.declaration,
            )
        }
    }
}
