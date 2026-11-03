package me.tbsten.katachi.dsl

/**
 * How far below its directory block a constraint reaches.
 *
 * Passed as `scope = ...` to `fileConstraint { }`. It only matters inside a directory block:
 * directly on a role or directly under `layout { }` there is no directory to be "directly" in,
 * so [DirectOnly] is refused there.
 *
 * ## Example 1: a parent directory whose rule does not reach its children
 * ```kt
 * "Util".role {
 *     layout {
 *         "util" {
 *             fileConstraint("uses the stdlib only", scope = FileConstraintRange.DirectOnly) { subject ->
 *                 emptyList() // sees the files directly in util only
 *             }
 *             "*".ktFile()           // covered
 *             "ksp" { "*".ktFile() } // not covered: util/ksp/ may use the KSP API
 *         }
 *     }
 * }
 * ```
 *
 * @see FileConstraintScope.fileConstraint
 */
public enum class FileConstraintRange {
    /** Every file declared below the block, nested directories included. The default. */
    Subtree,

    /**
     * Only the files the block declares directly: its own file declarations and its own `anyFile()`.
     *
     * What its nested blocks declare, or what it declares a level down with `/` or through a `**`
     * segment, is left out.
     */
    DirectOnly,
}
