package me.tbsten.katachi.test.architecture

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
 * `processor`, `check` and `report` share one layer. The walk and its results (`Violation` and
 * the rest) live in `check`, next to the `assert()` and the checks that produce them, and
 * `processor.internal.ProjectWalk` hands that same walk to every processor; `check` in turn is
 * built on `processor`. `report` turns the violations of a run into a SARIF file: `check`'s
 * `assertWith` writes it, and it reads `check`'s violations and report blocks. Each imports
 * another, so none may come before the others: they may import each other freely, and all
 * three may import nothing after them (except `processor.internal.DefinitionErrorReport`, see
 * `ide` below).
 *
 * `ide` writes the whole definition as JSON for the katachi IDE plugin. It comes after `template`
 * because that JSON carries the templates' own JSON, built by `template.internal`, next to what
 * `processor` and `check` know of the walk; nothing in `:katachi` imports it, with one exception:
 * `processor.internal.DefinitionErrorReport` imports four names from `ide.internal` and
 * `template.internal` (see `ProcessorRole`). runMain writes definition-errors.json even when the
 * entry point cannot be read, so it cannot call `ide` through a registry. The two names from
 * `template.internal` (`encodeToJson`, `writeFileAtomically`) are the JSON encoder and the atomic
 * file writer that the `ide` export uses too; sharing them keeps definition-errors.json written
 * the same way as the definition JSON.
 *
 * `konsist` and `konture` are the layers of `:katachi-konsist` and `:katachi-konture`, listed
 * together because the two backends are siblings and neither is "after" the other. Nothing in
 * `:katachi` can import either — the Gradle dependency runs the other way — so their entry is
 * not a rule that might break but a copy of one that cannot. It is listed so that the layer
 * rules in `roles/` name a complete set of forbidden targets rather than falling silent at the
 * end of the table.
 */
private val LAYERS: List<List<String>> =
    listOf(
        listOf(""),
        listOf("util"),
        listOf("dsl"),
        listOf("processor", "check", "report"),
        listOf("docs"),
        listOf("template"),
        listOf("ide"),
        listOf("konsist", "konture"),
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
 * The imports a file of [layer] may not have, one `ktFileContent { }` import pattern per layer
 * after it: `me.tbsten.katachi.docs.**`, `me.tbsten.katachi.template.**`, … in table order.
 *
 * Each library role writes them as prohibited lines under `` `import`("**") ``, next to
 * `` `package`(matchingDirectory) ``:
 *
 * ```kt
 * ktFileContent {
 *     `package`(matchingDirectory)
 *     `import`("**")
 *     laterLayerImportsOf(LAYER).forEach { not.`import`(it) }
 *     allow(anythingElse)
 * }
 * ```
 *
 * ## Why the package line sits in the same block
 *
 * The two halves of a role disagree about what they read. A `layout { }` pins a file by the
 * **directory** it sits in; the prohibited lines decide what that file may import by the
 * **package** its importers name. Nothing else says the two agree, and where they do not the
 * table stops meaning anything: a file written into `dsl/` under `package me.tbsten.katachi.util`
 * is checked as `dsl` and imported as `util`, so `util` would gain everything `dsl` can reach
 * without a single rule going red. `` `package`(matchingDirectory) `` closes that, and it has to
 * live in the same block because a role holds one `ktFileContent { }`. It asks for the directory
 * below `src/main/kotlin/` exactly; the Konsist `hasMatchingPath` it replaced only asked that the
 * path end with the package, so `package katachi.dsl`, `package sl` and a file with no package
 * line at all passed in `dsl/`.
 *
 * ## Why a list of patterns and not a helper that writes the block
 *
 * katachi records the first stack frame outside itself as the place of the block and of each of
 * its lines, so a helper declared *here* would make every layer role's report point at this file.
 * Returning only the patterns keeps the lines in the role's own file; the `forEach` there is
 * where a report says the prohibited line was written. The same holds for the `konsist { }`
 * rules the roles share through `InternalPackages.kt` and `KdocExamples.kt`: their names and
 * predicates are shared, the calls are not.
 *
 * ## What a pattern matches
 *
 * `**` crosses the dots, so `me.tbsten.katachi.dsl.**` matches `me.tbsten.katachi.dsl.gradle.div`
 * as well as a star import, which `ktFileContent { }` reads as the path `me.tbsten.katachi.dsl.*`.
 * The `konsist { }` rule it replaced compared Konsist's import name, which leaves the `.*` off,
 * so `import me.tbsten.katachi.docs.*` slipped past it.
 * It never matches a hypothetical `me.tbsten.katachi.dslfoo`: the pattern is matched level by
 * level. A `**` may match no level at all, so the pattern would also match a declaration named
 * `dsl` in the root package itself — there is none.
 */
fun laterLayerImportsOf(layer: String): List<String> =
    layersAfter(layer).map { "$ROOT_PACKAGE.$it.**" }
