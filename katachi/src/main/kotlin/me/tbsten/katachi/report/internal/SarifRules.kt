package me.tbsten.katachi.report.internal

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

/** One `tool.driver.rules[]` entry: one label of violation. */
internal class SarifRule(
    val id: String,
    val name: String,
    val shortDescription: String,
    val level: String,
)

/** The `level` SARIF gives a violation of [severity]. */
internal fun sarifLevelOf(severity: Severity): String = when (severity) {
    Severity.Error -> "error"
    Severity.Warning -> "warning"
}

/**
 * Whether katachi itself wrote [violation]'s type -- the eleven that have a block of their own
 * in `ViolationReport.kt`. Anything else came from a check outside katachi, and its rule keeps
 * the label the check gave it.
 */
internal fun isKatachiViolation(violation: Violation): Boolean = when (violation) {
    is UnexpectedFile,
    is UnexpectedDirectory,
    is MissingFile,
    is UncheckedFile,
    is UncheckedDirectory,
    is UncheckedCheck,
    is UnsatisfiedFileConstraint,
    is UncheckedFileConstraint,
    is AmbiguousLayout,
    is MissingDescription,
    is StaleBaselineEntry,
    -> true

    else -> false
}

/** `katachi.<label>` for katachi's own violations, the bare label for anyone else's. */
internal fun sarifRuleIdOf(violation: Violation): String =
    if (isKatachiViolation(violation)) "katachi.${violation.label}" else violation.label

/** One sentence per label of katachi's own, for `shortDescription`. */
private val KATACHI_RULE_DESCRIPTIONS: Map<String, String> = mapOf(
    "UnexpectedFile" to "A file exists that no role's layout allows.",
    "UnexpectedDirectory" to "A directory exists that no role's layout mentions. Nothing below it was checked.",
    "MissingFile" to "A file a role declared without a wildcard does not exist.",
    "UnsatisfiedFileConstraint" to "A file constraint (fileConstraint { } or konsist { }) rejected a file.",
    "UncheckedFileConstraint" to "A file constraint was not evaluated or failed, so the run is a partial result.",
    "UncheckedFile" to "The check failed at this file, so nothing is known about it.",
    "UncheckedDirectory" to "The check failed at this directory, so part of what it would have said is unknown.",
    "UncheckedCheck" to "A check handed to assert(...) threw, so nothing it would have reported is known.",
    "StaleBaselineEntry" to "A baseline entry holds back more violations than the project still has.",
    "AmbiguousLayout" to "Two or more roles claim the same path.",
    "MissingDescription" to "A role that may live in more than one place has a place without a description.",
)

private const val FOREIGN_RULE_DESCRIPTION: String = "Reported by a check outside katachi."

/**
 * The rules of one run: one per rule id that turned up in [violations], in id order. A foreign
 * label takes the level of the first violation that carried it.
 */
internal fun sarifRulesOf(violations: List<Violation>): List<SarifRule> {
    val byId = LinkedHashMap<String, SarifRule>()
    for (violation in violations) {
        val id = sarifRuleIdOf(violation)
        if (id in byId) continue
        val own = isKatachiViolation(violation)
        byId[id] = SarifRule(
            id = id,
            name = violation.label,
            shortDescription = if (own) KATACHI_RULE_DESCRIPTIONS.getValue(violation.label) else FOREIGN_RULE_DESCRIPTION,
            level = sarifLevelOf(violation.severity),
        )
    }
    return byId.values.sortedBy { it.id }
}
