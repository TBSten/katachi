package me.tbsten.katachi.test.architecture

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
 * `util` comes straight after it: general-purpose helpers written against the standard library
 * alone, which every layer may use and which may use none of them.
 *
 * `dsl.files` — the file system the walk reads, and the git-backed view of it — is `dsl`.
 * `FileSelection` in `dsl` hands out the implementations in `dsl.files.internal`, and those
 * implementations throw the exceptions declared next to `FileSelection`, so the two import
 * each other; being one layer, they may.
 *
 * `processor` and `check` share one layer. The walk and its results (`Violation` and the rest)
 * live in `check`, next to the `assert()` and the checks that produce them, and
 * `processor.internal.ProjectWalk` hands that same walk to every processor; `check` in turn is
 * built on `processor`. Each imports the other, so neither may come before the other: they may
 * import each other freely, and both may import nothing after them.
 *
 * `konsist` is `:katachi-konsist`'s layer. Nothing in `:katachi` can import it — the Gradle
 * dependency runs the other way — so its entry is not a rule that might break but a copy of
 * one that cannot. It is listed so that the layer rules in `roles/` name a complete set of
 * forbidden targets rather than falling silent at the end of the table.
 */
private val LAYERS: List<List<String>> =
    listOf(
        listOf(""),
        listOf("util"),
        listOf("dsl"),
        listOf("processor", "check"),
        listOf("docs"),
        listOf("template"),
        listOf("konsist"),
    )

/** The layers after the one [layer] belongs to, flattened in table order. */
private fun layersAfter(layer: String): List<String> {
    val index = LAYERS.indexOfFirst { layer in it }
    require(index >= 0) { "`$layer` is not a layer in LAYERS" }
    return LAYERS.drop(index + 1).flatten()
}

/** The package every layer name is relative to. */
private const val ROOT_PACKAGE: String = "me.tbsten.katachi"

/**
 * The name every role declares the place-matches-package rule under.
 *
 * ## Why this rule is part of the layer table and not a tidiness rule
 *
 * The two halves of a role disagree about what they read. A `layout { }` pins a file by the
 * **directory** it sits in; [importsLaterLayerThan] decides what that file may import by the
 * **package** its importers name. Nothing so far says the two agree, and where they do not the
 * table stops meaning anything: a file written into `dsl/` under `package me.tbsten.katachi.util`
 * is checked as `dsl` and imported as `util`, so `util` would gain everything `dsl` can reach
 * without a single rule going red.
 *
 * One line per role closes it, and one line is all it takes because Konsist already asks the
 * question — `hasMatchingPath` compares a package declaration against its own file's path, so
 * unlike [laterLayersOf] this rule needs nothing said about *which* layer the role is. That is
 * also why the rule is a bare constant rather than a function: there is no per-role wording for
 * a helper to build, and `konsist { }` still has to be written in the role's own file so that a
 * report points there rather than here.
 */
const val PACKAGE_MATCHES_PATH_RULE: String = "package 宣言がファイルの置き場所と一致すること"

/**
 * The layers after [layer], worded to fit on the one line a constraint name gets.
 *
 * The hand-written spec this replaced opened its failure with the whole table, so that a
 * reader saw the rule and not only the breach. An `[UnsatisfiedFileConstraint]` block has no room
 * for a table, so the rule is folded into the constraint's name instead: the report line reads
 * `docs / template / konsist を import しないこと`.
 */
fun laterLayersOf(layer: String): String =
    layersAfter(layer).joinToString(" / ")

/**
 * A predicate selecting files that import a layer after [layer].
 *
 * Deliberately **not** a function that wraps `konsist { }`. `FileConstraintScope.fileConstraint`
 * captures the first stack frame outside katachi as the declaration site, so a helper declared
 * *here* would make every layer role's report point at this file — and `konsist { }` has no
 * parameter for passing a site through. Returning only the predicate keeps the declaration in
 * the role's own file.
 *
 * Wrapping `konsist { }` inside that file is fine, and is what each role does: a `private fun
 * LayoutScope.importsOnlyEarlierLayers()` sits next to the role it serves, so the captured
 * frame is still `roles/<Name>Role.kt`. What may not be shared is the `konsist { }` call, not
 * the idea of a helper.
 *
 * The trailing dot on each forbidden prefix matters: `me.tbsten.katachi.dsl` alone would also
 * match a hypothetical `me.tbsten.katachi.dslfoo`. A wildcard import lands here as
 * `me.tbsten.katachi.dsl.*`, which the prefix catches unchanged.
 */
fun importsLaterLayerThan(layer: String): (KoFileDeclaration) -> Boolean {
    val forbidden = layersAfter(layer).map { "$ROOT_PACKAGE.$it." }
    return { file -> file.imports.any { import -> forbidden.any { import.name.startsWith(it) } } }
}
