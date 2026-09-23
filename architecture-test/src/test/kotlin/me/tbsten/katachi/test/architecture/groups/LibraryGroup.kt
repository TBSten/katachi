package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.check
import me.tbsten.katachi.test.architecture.roles.dsl
import me.tbsten.katachi.test.architecture.roles.fileSystem
import me.tbsten.katachi.test.architecture.roles.marker
import me.tbsten.katachi.test.architecture.roles.processor
import me.tbsten.katachi.test.architecture.roles.scan

/**
 * The roles of `:katachi`, the library itself.
 *
 * Here a role is a layer, and that is an observation rather than a shortcut: in this
 * repository the kinds of file and the package layers happen to coincide. A file in `scan/`
 * is a piece of the traversal and cannot be anything else; the same holds for `fs/`, for
 * `processor/` and for `check/`. Where the two came apart, the role follows the kind and not
 * the package — see `roles/MarkerRole.kt` and `roles/DslRole.kt`.
 *
 * Each role carries the same three `konsist { }` rules, in the same order.
 *
 * 1. **The layers it may not import.** Together these are what
 *    `katachi/src/test/kotlin/me/tbsten/katachi/test/PackageDependencySpec.kt` used to do by
 *    reading the sources as text. The forbidden list is built from one table, in
 *    `LayerImports.kt`, so the rules cannot drift apart.
 * 2. **That the package matches the directory** — `PACKAGE_MATCHES_PATH_RULE`, which is what
 *    keeps rule 1 meaning anything at all. See its own KDoc.
 * 3. **That every public declaration shows an example** — `KDOC_EXAMPLE_RULE`, the repository's
 *    KDoc convention. See `KdocExamples.kt`.
 *
 * ## Why every role file repeats the same three helpers
 *
 * `konsist { }` is captured at the first stack frame outside katachi, so one shared helper
 * that wrote the constraint would become the declaration site of all of them, and every
 * violation would point at that helper instead of at the role. Each role file therefore keeps
 * its own `private` copy: the wording and the predicates are shared through `LayerImports.kt`
 * and `KdocExamples.kt`, and the declaration stays in the file that owns the role.
 */
fun DeclarationContainerScope.libraryGroup() = "library".group {
    title = "ライブラリ"

    marker()
    fileSystem()
    dsl()
    scan()
    processor()
    check()
}
