package me.tbsten.katachi.test.architecture.library

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The roles of `:katachi`, the library itself.
 *
 * Here a role is a layer, and that is an observation rather than a shortcut: in this
 * repository the kinds of file and the package layers happen to coincide. A file in
 * `processor/` is a piece of the processor machinery and cannot be anything else; the same
 * holds for `check/`, for `report/`, for `template/` and for `ide/`. Where the two came apart, the role follows the kind and not
 * the package — see `library/MarkerRole.kt` and `library/DslRole.kt`.
 *
 * Each role carries the same five rules, in the same order: the first two are the lines of one
 * `ktFileContent { }`, the other three are `konsist { }`.
 *
 * 1. **The layers it may not import** — `` not.`import`(…) `` under `` `import`("**") ``.
 *    Together these are what
 *    `katachi/src/test/kotlin/me/tbsten/katachi/test/PackageDependencySpec.kt` used to do by
 *    reading the sources as text. The forbidden list is built from one table, in
 *    `LayerImports.kt`, so the rules cannot drift apart.
 * 2. **That the package matches the directory** — `` `package`(matchingDirectory) ``, which is
 *    what keeps rule 1 meaning anything at all. See `laterLayerImportsOf` for why.
 * 3. **That internal code sits in a `.internal` package, and only there** —
 *    `INTERNAL_PACKAGE_RULE`. See `InternalPackages.kt` for what counts, and for the direct
 *    subtypes of a sealed type that Kotlin keeps next to it.
 * 4. **That every public declaration shows an example** — `KDOC_EXAMPLE_RULE`, the repository's
 *    KDoc convention. See `KdocExamples.kt`.
 * 5. **That no public declaration's KDoc puts a heading or a paragraph after its block tags** —
 *    `KDOC_TAG_ORDER_RULE`. Dokka stops rendering a KDoc's body at its first block tag, so
 *    anything written after one — most often a `## Example` that drifted below its `@param`s —
 *    renders broken. See `KdocExamples.kt`.
 *
 * The `ktFileContent { }` lets every declaration through (`allow(anythingElse)`): what a
 * declaration may be is rules 3 to 5, which read the KDoc and the `.internal` line that the block
 * cannot. `Marker` alone names what it holds, because it is one kind of file rather than a layer.
 *
 * ## Why every role file repeats the same four helpers
 *
 * `konsist { }` and `ktFileContent { }` are captured at the first stack frame outside katachi,
 * so one shared helper that wrote the constraint would become the declaration site of all of
 * them, and every violation would point at that helper instead of at the role. Each role file
 * therefore keeps its own `private` copy: the wording, the patterns and the predicates are shared
 * through `LayerImports.kt`, `InternalPackages.kt` and `KdocExamples.kt`, and the declaration
 * stays in the file that owns the role.
 */
fun DeclarationContainerScope.libraryGroup() = "library".group {
    title = "ライブラリ"

    marker()
    util()
    dsl()
    processor()
    check()
    report()
    docs()
    template()
    ide()
}
