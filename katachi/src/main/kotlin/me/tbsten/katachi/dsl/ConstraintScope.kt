package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * A DSL receiver a constraint can be written on: a role block, and every block inside a
 * `layout { }`.
 *
 * Where it is written decides what it covers. Directly on a role it covers the union of every
 * `layout { }` that role declares; inside a directory block it covers that block's own subtree
 * and nothing else. That is the whole of the scoping rule, and it is why this is an interface
 * shared by the two receivers rather than one function on each of them.
 *
 * `ArchitectureScope` and `GroupScope` deliberately do **not** get it: a group holds roles, not
 * files, so there would be no set of files for a constraint written there to be about.
 *
 * ## Example 1: constrain one directory, and the whole role
 * ```kt
 * "UseCase" {
 *     constraint("has an invoke function") { subject -> /* every place below */ emptyList() }
 *     layout {
 *         ":core:domain".module {
 *             constraint("is usable from outside") { subject -> /* core/domain only */ emptyList() }
 *             mainSourceSet / kotlin / "useCase" / "*UseCase".ktFile()
 *         }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface ConstraintScope {
    /**
     * Declares a constraint over the files the block around it matched.
     *
     * The block is stored, not evaluated: it runs when a check that evaluates constraints
     * runs, against the files the one walk of the project found. Building `architecture { }`
     * therefore reads nothing and runs nothing, the same way `layout { }` is deferred.
     *
     * @param name what to call it, shown in the report next to the role. `null` means it is
     *   named by its declaration site alone. It must hold at least one non-whitespace
     *   character and no line break, because it is printed on one line of a report block.
     * @param declaredAt where to point the report at. The default is the first stack frame
     *   outside `me.tbsten.katachi.*`, which is the line that called this. **A wrapper of your
     *   own has to pass its own caller's site**, or every report points at the wrapper.
     * @param check what to ask of the covered files.
     * @throws KatachiConstraintNameException when [name] is blank or holds a line break.
     *
     * ## Example 1: a constraint written by hand, without any backend
     * ```kt
     * "UseCase" {
     *     constraint("leaves no TODO") { subject ->
     *         subject.files.filter { it.endsWith(".kt") }.map { ConstraintFailure(it) }
     *     }
     *     layout { "core/domain/useCase" { "*UseCase.kt".file() } }
     * }
     * ```
     *
     * ## Example 2: point the report somewhere other than the calling line
     * ```kt
     * "UseCase" {
     *     constraint("has an invoke function", DeclarationSite("DomainRules.kt", 12)) { subject ->
     *         emptyList()
     *     }
     *     layout { "core/domain/useCase" { "*UseCase.kt".file() } }
     * }
     * ```
     */
    @ExperimentalKatachiApi
    public fun constraint(
        name: String? = null,
        declaredAt: DeclarationSite = captureDeclarationSite(),
        check: FileSetConstraint,
    )
}
