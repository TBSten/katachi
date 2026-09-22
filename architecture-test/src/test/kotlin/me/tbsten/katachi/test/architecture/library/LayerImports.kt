package me.tbsten.katachi.test.architecture.library

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * The layers of the library, bottom first.
 *
 * A file may import from its own layer and from every layer **before** it in this list, and
 * from nothing after it. Sub-packages belong to the layer they sit under, so `dsl.gradle` and
 * `dsl.kotlin` are `dsl`.
 *
 * The empty name is the root package `me.tbsten.katachi` itself, which holds the opt-in
 * markers and the exception bases and depends on nothing.
 *
 * `konsist` is `:katachi-konsist`'s layer. Nothing in `:katachi` can import it — the Gradle
 * dependency runs the other way — so its entry is not a rule that might break but a copy of
 * one that cannot. It is listed so that the seven rules below name a complete set of forbidden
 * targets rather than falling silent at the end of the table.
 */
private val LAYERS: List<String> = listOf("", "fs", "dsl", "scan", "processor", "check", "konsist")

/** The package every layer name is relative to. */
private const val ROOT_PACKAGE: String = "me.tbsten.katachi"

/**
 * The layers after [layer], worded to fit on the one line a constraint name gets.
 *
 * The hand-written spec this replaced opened its failure with the whole table, so that a
 * reader saw the rule and not only the breach. An `[UnsatisfiedConstraint]` block has no room
 * for a table, so the rule is folded into the constraint's name instead: the report line reads
 * `scan / processor / check / konsist を import しないこと`.
 */
fun laterLayersOf(layer: String): String =
    LAYERS.drop(LAYERS.indexOf(layer) + 1).joinToString(" / ")

/**
 * A predicate selecting files that import a layer after [layer].
 *
 * Deliberately **not** a function that wraps `konsist { }`. `ConstraintScope.constraint`
 * captures the first stack frame outside katachi as the declaration site, so a helper that
 * declared the constraint would make all seven reports point at this file — and `konsist { }`
 * has no parameter for passing a site through. Returning only the predicate keeps the
 * declaration at the call site, inside the role it belongs to.
 *
 * The trailing dot on each forbidden prefix matters: `me.tbsten.katachi.dsl` alone would also
 * match a hypothetical `me.tbsten.katachi.dslfoo`. A wildcard import lands here as
 * `me.tbsten.katachi.dsl.*`, which the prefix catches unchanged.
 */
fun importsLaterLayerThan(layer: String): (KoFileDeclaration) -> Boolean {
    val forbidden = LAYERS.drop(LAYERS.indexOf(layer) + 1).map { "$ROOT_PACKAGE.$it." }
    return { file -> file.imports.any { import -> forbidden.any { import.name.startsWith(it) } } }
}
