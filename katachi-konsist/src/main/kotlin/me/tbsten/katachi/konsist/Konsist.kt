package me.tbsten.katachi.konsist

import me.tbsten.katachi.dsl.ConstraintScope

/**
 * Declares a named constraint over the files the block around it matched, written with
 * Konsist.
 *
 * `this` is the name the report prints next to the role, so it is a sentence about the code
 * ("feature の外に出さないこと") rather than an identifier. Where the call is written decides
 * what it covers: directly on a role it covers the union of every `layout { }` that role
 * declares, inside a directory block it covers that block's subtree and nothing else. That is
 * [ConstraintScope]'s rule, not this function's — `konsist { }` is one backend among several
 * and changes nothing about scoping.
 *
 * The block is stored, not run. It runs when a check that evaluates constraints runs, which
 * means `architecture { }` still reads no files and parses no Kotlin.
 *
 * `ConstraintScope` is taken as a context parameter rather than as a receiver so that the call
 * reads the same as katachi's own layout vocabulary and needs no import of the scope type.
 * `architecture { }` and `group { }` do not implement [ConstraintScope], so writing
 * `konsist { }` there does not compile: a group holds roles, not files, so there would be no
 * set of files for the block to be about.
 *
 * @param block what to ask of the covered files. See [KonsistScope].
 * @throws me.tbsten.katachi.dsl.KatachiConstraintNameException when the name is blank or holds
 *   a line break — it is printed on one line of a report block.
 * @throws me.tbsten.katachi.dsl.KatachiConstraintWithoutLayoutException when the role it is
 *   written on declares no `layout { }`, so nothing could ever be covered.
 *
 * ## Example 1: one rule, on one module
 * ```kt
 * "UseCase" {
 *     layout {
 *         ":core:domain".module {
 *             "外から使えること".konsist {
 *                 classes().must { it.hasPublicOrDefaultModifier }
 *             }
 *             mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
 *         }
 *     }
 * }
 * ```
 *
 * ## Example 2: one rule over everywhere the role lives
 * ```kt
 * "UseCase" {
 *     "invoke を持つこと".konsist {
 *         classes().withNameEndingWith("UseCase")
 *             .must { klass -> klass.hasFunction { it.name == "invoke" } }
 *     }
 *     layout { ":core:domain".module { "useCase" / "*UseCase".ktFile() } }
 * }
 * ```
 */
context(scope: ConstraintScope)
public fun String.konsist(block: KonsistScope.() -> Unit) {
    scope.constraint(name = this, check = KonsistConstraint(block))
}

/**
 * Declares an unnamed constraint over the files the block around it matched, written with
 * Konsist.
 *
 * Identical to the named form except that the report identifies it by its declaration site
 * alone. Worth it when the block is short enough to read as its own name, and when there is
 * only one constraint in that place; a report that has to say which of three rules failed
 * reads better with a name.
 *
 * @param block what to ask of the covered files. See [KonsistScope].
 * @throws me.tbsten.katachi.dsl.KatachiConstraintWithoutLayoutException when the role it is
 *   written on declares no `layout { }`, so nothing could ever be covered.
 *
 * ## Example 1: the only rule of one directory
 * ```kt
 * "UseCase" {
 *     layout {
 *         "core/domain/useCase" {
 *             konsist { classes().must { it.hasInternalModifier } }
 *             "*UseCase.kt".file()
 *         }
 *     }
 * }
 * ```
 */
context(scope: ConstraintScope)
public fun konsist(block: KonsistScope.() -> Unit) {
    scope.constraint(name = null, check = KonsistConstraint(block))
}
