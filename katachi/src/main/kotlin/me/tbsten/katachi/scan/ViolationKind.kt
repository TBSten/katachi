package me.tbsten.katachi.scan

/**
 * What kind of problem a violation is — which is the same thing as what the reader has to do
 * about it, so a report groups its blocks by this.
 *
 * The declaration order is the order the blocks appear in a report. [Failed] stays last no
 * matter what — `Scan` and `validate` sort blocks with `sortedBy { it.kind.ordinal }`, and every
 * existing report's ordering depends on [Failed] being the last kind — while a new entry goes in
 * wherever it actually belongs among the rest, ahead of [Failed].
 *
 * ## Example 1: count violations by kind
 * ```kt
 * ViolationKind.entries.associateWith { kind ->
 *     projectArchitecture.validate().count { it.kind == kind }
 * }
 * ```
 */
public enum class ViolationKind {
    /**
     * Something exists that no role allows. Delete it, move it, or declare it.
     *
     * ## Example 1: list files and directories nothing declared
     * ```kt
     * projectArchitecture.validate().filter { it.kind == ViolationKind.Unexpected }
     * ```
     */
    Unexpected,

    /**
     * Something declared does not exist. Create it, or drop the declaration.
     *
     * ## Example 1: list declarations with nothing at their path yet
     * ```kt
     * projectArchitecture.validate().filter { it.kind == ViolationKind.Missing }
     * ```
     */
    Missing,

    /**
     * A constraint declared with `constraint { }` — `konsist { }` included — does not hold.
     * Fix the code, or the constraint.
     *
     * ## Example 1: list the constraints that failed
     * ```kt
     * projectArchitecture.validate(ConstraintCheck()).filter { it.kind == ViolationKind.Constraint }
     * ```
     */
    Constraint,

    /**
     * Two or more declarations claim the same thing, so which one it belongs to is not decided.
     * Decide, and drop or narrow the rest.
     *
     * Katachi itself only ever reports this kind with [Severity.Warning]: nothing that carries
     * it is, on its own, a reason `assert()` fails.
     *
     * ## Example 1: list what more than one declaration claims
     * ```kt
     * projectArchitecture.validate().filter { it.kind == ViolationKind.Ambiguous }
     * ```
     */
    Ambiguous,

    /**
     * A declaration does not say what it is for. Write the missing explanation.
     *
     * Katachi itself only ever reports this kind with [Severity.Warning]: nothing that carries
     * it is, on its own, a reason `assert()` fails.
     *
     * ## Example 1: list declarations missing their explanation
     * ```kt
     * projectArchitecture.validate().filter { it.kind == ViolationKind.Unexplained }
     * ```
     */
    Unexplained,

    /**
     * The check itself failed at a path, so that path is neither allowed nor reported.
     *
     * A run holding one of these is a partial result: everything else in the report was
     * checked, and the path named here was not. The failure is kept rather than dropped
     * because a check that quietly skips what it could not look at is a check with a hole
     * in it.
     *
     * ## Example 1: tell a partial result from a complete one
     * ```kt
     * val partial = projectArchitecture.validate().any { it.kind == ViolationKind.Failed }
     * ```
     */
    Failed,
}
