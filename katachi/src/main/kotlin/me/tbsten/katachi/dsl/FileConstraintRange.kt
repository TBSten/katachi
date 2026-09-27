package me.tbsten.katachi.dsl

/**
 * How far below its directory block a constraint reaches.
 *
 * Passed as `scope = ...` to `fileConstraint { }` and to both forms of `konsist { }`. It only
 * matters inside a directory block: directly on a role, directly under `layout { }`, and in
 * `":".module { }` there is no directory to be "directly" in, so [DirectOnly] is refused there.
 *
 * Not called `...Scope` although the argument is: [FileConstraintScope] is already the DSL
 * receiver a constraint is written on.
 *
 * ## Example 1: a parent directory whose rule does not reach its children
 * ```kt
 * "Util" {
 *     layout {
 *         "util" {
 *             "does not use the KSP API".konsist(scope = FileConstraintRange.DirectOnly) {
 *                 files.must { file -> file.imports.none { it.name.startsWith("com.google.devtools.ksp") } }
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
     * Only the files the block declares directly: its own file declarations and its own
     * `anyFile()`. What its nested blocks declare, or what it declares a level down with `/`
     * or through a `**` segment, is left out.
     */
    DirectOnly,
}
