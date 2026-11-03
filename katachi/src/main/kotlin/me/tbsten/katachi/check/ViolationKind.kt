package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * What kind of problem a violation is, which is also what you have to do about it; a report
 * groups its blocks by this.
 *
 * The declaration order is the order the blocks appear in a report, with [Failed] always last.
 *
 * ## Example 1: count violations by kind
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert()
 * }
 * ViolationKind.entries.associateWith { kind -> failure.violations.count { it.kind == kind } }
 * ```
 */
public enum class ViolationKind {
    /**
     * Something exists that no role allows. Delete it, move it, or declare it.
     *
     * A file or directory nothing declared, or a file whose bound captures disagree
     * ([CaptureMismatch]).
     */
    Unexpected,

    /** Something declared does not exist. Create it, or drop the declaration. */
    Missing,

    /**
     * A constraint declared with `fileConstraint { }` — `konsist { }` included — does not hold.
     * Fix the code, or the constraint.
     */
    FileConstraint,

    /**
     * A baseline entry holds back more violations than the project still has. Shrink the entry.
     *
     * Some of what it held back was fixed; shrinking it keeps the fixed violation from coming
     * back unnoticed.
     */
    @ExperimentalKatachiApi
    Stale,

    /**
     * Two or more declarations claim the same thing, so which one it belongs to is not decided.
     * Decide, and drop or narrow the rest.
     *
     * katachi only reports this kind with [Severity.Warning].
     */
    Ambiguous,

    /**
     * A declaration does not say what it is for. Write the missing explanation.
     *
     * katachi only reports this kind with [Severity.Warning].
     */
    Unexplained,

    /**
     * The check itself failed at a path, so that path is neither allowed nor reported.
     *
     * A run holding one of these is a partial result: everything else in the report was
     * checked, and the path named here was not.
     */
    Failed,
}
