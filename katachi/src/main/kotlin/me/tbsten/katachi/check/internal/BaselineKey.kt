package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.AmbiguousLayout
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

/**
 * What identifies one entry of the baseline: which check, which rule, which path — plus, for a
 * failed constraint, which role, constraint and declaration. Never a line number: adding a line
 * above a violation must not turn it into a new one.
 *
 * Ordered the way the file lists entries within a check: path, then rule, then the rest, with a
 * missing value before a present one.
 */
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

/** How the baseline treats a kind of violation. */
internal enum class BaselineRule {
    /** Keyed by check, label and path. */
    ByPath,

    /** Keyed by check, label and path, plus the role, constraint name and declaration name. */
    ByDeclaration,

    /** Never held back: a hole in the run, a warning, or the baseline talking about itself. */
    NotHeldBack,
}

/**
 * The rule for [violation]. Every violation katachi declares has a branch of its own —
 * `BaselineKeyCoverageSpec` holds that — and anything else is read by its severity and kind.
 */
internal fun baselineRuleOf(violation: Violation): BaselineRule = when (violation) {
    is UnexpectedFile -> BaselineRule.ByPath
    is UnexpectedDirectory -> BaselineRule.ByPath
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

/**
 * The key [violation] is filed under when [check] reported it, or `null` when the baseline never
 * holds it back. A Warning never has a key, whatever its type.
 */
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
