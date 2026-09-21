package me.tbsten.katachi.check

/**
 * What kind of problem a violation is — which is the same thing as what the reader has to do
 * about it, so a report groups its blocks by this.
 *
 * The declaration order is the order the blocks appear in a report, so an entry is appended
 * rather than inserted.
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
     * A constraint declared with `konsist { }` does not hold. Fix the code, or the constraint.
     *
     * ## Example 1: list `konsist { }` constraints that failed
     * ```kt
     * projectArchitecture.validate().filter { it.kind == ViolationKind.Constraint }
     * ```
     */
    Constraint,

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
