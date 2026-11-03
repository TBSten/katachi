package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.captureDeclarationSite

/**
 * A DSL receiver a constraint can be written on: a role block, and every block inside a
 * `layout { }`.
 *
 * Where it is written decides what it covers. Directly on a role it covers every `layout { }`
 * that role declares; inside a directory block it covers that block's own subtree, or with
 * `scope = FileConstraintRange.DirectOnly` only the files that block declares directly.
 * `architecture { }` and groups do not offer it: they hold roles, not files.
 *
 * ## Example 1: constrain one directory, and the whole role
 * ```kt
 * "UseCase".role {
 *     fileConstraint("leaves no TODO") { subject ->
 *         subject.files.filter { it.endsWith("Todo.kt") }.map { FileConstraintFailure(it) }
 *     }
 *     layout {
 *         "core/domain" {
 *             fileConstraint("is usable from outside") { subject -> /* core/domain only */ emptyList() }
 *             mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile()
 *         }
 *     }
 * }
 * ```
 *
 * ## Example 2: a parent directory whose rule does not reach its children
 * ```kt
 * "Util".role {
 *     layout {
 *         "util" {
 *             fileConstraint("uses the stdlib only", scope = FileConstraintRange.DirectOnly) { subject -> /* util/ only */ emptyList() }
 *             "*".ktFile()
 *             "ksp" { "*".ktFile() } // not covered: util/ksp/ may use the KSP API
 *         }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface FileConstraintScope {
    /**
     * Declares a constraint over the files the block around it matched.
     *
     * The block is stored, not evaluated: it runs when a check that evaluates constraints runs
     * (see `FileConstraintCheck`), against the files that exist.
     *
     * @param name what to call it, shown in the report next to the role. `null` means it is
     *   named by its declaration site alone. It must hold at least one non-whitespace
     *   character and no line break, because it is printed on one line of a report block.
     * @param declaredAt where to point the report at. The default is the first stack frame
     *   outside `me.tbsten.katachi.*`, which is the line that called this. **A wrapper of your
     *   own has to pass its own caller's site**, or every report points at the wrapper.
     * @param scope how far below the surrounding directory block it reaches.
     *   [FileConstraintRange.Subtree], the default, covers the whole subtree;
     *   [FileConstraintRange.DirectOnly] covers only the files the block declares directly.
     * @param check what to ask of the covered files.
     * @throws KatachiFileConstraintNameException when [name] is blank or holds a line break.
     * @throws KatachiFileConstraintDirectOnlyWithoutDirectoryException when [scope] is
     *   [FileConstraintRange.DirectOnly] somewhere with no directory of its own: directly on a
     *   role or directly under `layout { }`.
     * @throws KatachiFileConstraintDirectOnlyCoversNothingException when [scope] is
     *   [FileConstraintRange.DirectOnly] and the block declares no file directly.
     */
    @ExperimentalKatachiApi
    public fun fileConstraint(
        name: String? = null,
        declaredAt: DeclarationSite = captureDeclarationSite(),
        scope: FileConstraintRange = FileConstraintRange.Subtree,
        check: FileConstraint,
    )
}
