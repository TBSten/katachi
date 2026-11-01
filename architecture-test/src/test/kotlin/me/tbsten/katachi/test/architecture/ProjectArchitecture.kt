package me.tbsten.katachi.test.architecture

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.test.architecture.backend.backendGroup
import me.tbsten.katachi.test.architecture.docs.docsGroup
import me.tbsten.katachi.test.architecture.gradle.gradleGroup
import me.tbsten.katachi.test.architecture.ideplugin.idePluginGroup
import me.tbsten.katachi.test.architecture.library.libraryGroup
import me.tbsten.katachi.test.architecture.plugin.pluginGroup
import me.tbsten.katachi.test.architecture.sample.sampleGroup
import me.tbsten.katachi.test.architecture.testing.testingGroup
import me.tbsten.katachi.test.architecture.tool.toolGroup

// Where each module keeps its production sources, below its own `src/main/kotlin`, and its test
// sources below `src/test/kotlin`. Written out rather than derived from the module name:
// `:katachi` is the root package itself and `:katachi-konsist` and `:katachi-konture` sit *inside* it, as
// `me.tbsten.katachi.konsist` and `me.tbsten.katachi.konture`, because the backend is one more package of the library rather than
// a library of its own.
//
// Test code lives one level deeper than production code, under `me.tbsten.katachi.test`, and that
// is load bearing rather than a convention -- see [projectArchitecture] for why.

/** Production package directory of `:katachi`. */
const val KATACHI_MAIN_PACKAGE: String = "me/tbsten/katachi"

/** Production package directory of `:katachi-konsist`. */
const val KONSIST_MAIN_PACKAGE: String = "me/tbsten/katachi/konsist"

/** Production package directory of `:katachi-konture`. */
const val KONTURE_MAIN_PACKAGE: String = "me/tbsten/katachi/konture"

/** Production package directory of `:tool:dokka`. */
const val DOKKA_MAIN_PACKAGE: String = "me/tbsten/katachi/dokka"

/** Test package directory of `:katachi`. */
const val KATACHI_TEST_PACKAGE: String = "me/tbsten/katachi/test"

/** Test package directory of `:katachi-konsist`. */
const val KONSIST_TEST_PACKAGE: String = "me/tbsten/katachi/test/konsist"

/** Test package directory of `:katachi-konture`. */
const val KONTURE_TEST_PACKAGE: String = "me/tbsten/katachi/test/konture"

/** Test package directory of `:tool:dokka`. */
const val DOKKA_TEST_PACKAGE: String = "me/tbsten/katachi/test/dokka"

/** Test package directory of `:architecture-test`, so this definition's own files are addressed the same way. */
const val ARCHITECTURE_TEST_PACKAGE: String = "me/tbsten/katachi/test/architecture"

/**
 * The architecture of katachi itself, described with katachi.
 *
 * The same recommended shape the three samples under `sample/` use, applied to the real
 * library: one independent JVM module, the definition split one declaration per file, and
 * `assert()` run from a test. katachi denies by default, so this is an allow list — every file
 * `files = gitTracked()` offers has to be covered by some role, and anything else fails
 * `ProjectArchitectureSpec`.
 *
 * ## How the definition is split
 *
 * The files are nested by group, the same shape the samples use. `<group>/<Name>Group.kt` holds
 * one group, which says what it is made of by calling the role functions in order, and
 * `<group>/<Name>Role.kt` beside it holds one role of that group. Both are extensions on
 * `DeclarationContainerScope` — the scope `architecture { }` and `"...".group { }` share — so
 * a role can be moved into another group without touching the role's own file. This file only
 * calls the nine group functions.
 *
 * None of those functions may be `inline`. An inlined frame reports the caller's file with a
 * line number past its end, and katachi captures the declaration site from the stack, so the
 * violation would point at a line nobody wrote. Written once here rather than repeated in
 * thirty files; `DeclarationSiteSpec` is what actually holds the line.
 *
 * Three helpers sit next to this file rather than under a group's directory: `LayerImports.kt`,
 * `InternalPackages.kt` and `KdocExamples.kt` are read by roles of more than one group, and a
 * group's directory is declared to hold nothing but `*Group.kt` and `*Role.kt`.
 *
 * ## Why this package is `me.tbsten.katachi.test.architecture`
 *
 * It may not be `me.tbsten.katachi.architecture`. `DeclarationSite.isKatachiFrame` skips every
 * stack frame that starts with `me.tbsten.katachi.` and does **not** start with
 * `me.tbsten.katachi.test.`, which is how katachi's own wrappers stay out of a user's report.
 * A definition written under the first prefix would have every frame of its own skipped, and
 * the `declaredAt` of every group, role, layout entry and constraint would point into the
 * kotest runner instead of at the line that wrote it — so violations would stop saying where
 * the rule lives.
 *
 * ## What the layer roles enforce that a layout cannot
 *
 * The eight roles of the `library` group are the package layers of `:katachi`, each carrying a
 * `ktFileContent { }` that forbids imports of the layers after it and pins its package to its
 * directory so that the first means something. That half used to be a hand-written spec
 * reading the sources as text; it is a rule about what a Kotlin file holds, which is what
 * `ktFileContent { }` writes. See `LayerImports.kt`. `backend/KonsistBackend` and
 * `backend/KontureBackend` carry the package line and no prohibited import.
 *
 * Two `konsist { }` on each of those roles — the same two on `backend/KonsistBackend` and
 * `backend/KontureBackend`, and one alone on `tool/DokkaPlugin` — ask the repository's KDoc
 * convention of every public declaration: that it has a KDoc, which shows a `## Example` on a
 * type and a top-level function (`KDOC_EXAMPLE_RULE`),
 * and that nothing sits after its block tags, where Dokka would stop rendering it
 * (`KDOC_TAG_ORDER_RULE`) — see `KdocExamples.kt`. It is deliberately not asked of `sample/`, of
 * this definition, or of test code. A third, `INTERNAL_PACKAGE_RULE`, keeps internal code in
 * `.internal` packages — see `InternalPackages.kt`. These three stay `konsist { }`:
 * `ktFileContent { }` reads no KDoc, and cannot tell an `.internal` file from its neighbour or
 * know which sealed types other files declare.
 */
val projectArchitecture: Architecture = architecture {
    libraryGroup()
    backendGroup()
    pluginGroup()
    idePluginGroup()
    testingGroup()
    docsGroup()
    sampleGroup()
    gradleGroup()
    toolGroup()
}
