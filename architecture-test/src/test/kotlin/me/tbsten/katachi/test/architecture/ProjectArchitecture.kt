package me.tbsten.katachi.test.architecture

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.test.architecture.groups.backendGroup
import me.tbsten.katachi.test.architecture.groups.buildGroup
import me.tbsten.katachi.test.architecture.groups.docsGroup
import me.tbsten.katachi.test.architecture.groups.libraryGroup
import me.tbsten.katachi.test.architecture.groups.sampleGroup
import me.tbsten.katachi.test.architecture.groups.testingGroup
import me.tbsten.katachi.test.architecture.groups.toolGroup

/**
 * Where each module keeps its production sources, below its own `src/main/kotlin`.
 *
 * Not derivable from the module name: `:katachi` is the root package itself and
 * `:katachi-konsist` sits *inside* it, as `me.tbsten.katachi.konsist`, because the backend is
 * one more package of the library rather than a library of its own. So the mapping is written
 * out instead of being computed by [me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage].
 *
 * A module this does not know answers with the empty string, which katachi turns into
 * `KatachiModulePackageException` at the line that used it — a louder failure than silently
 * deriving a plausible-looking directory that nothing lives in.
 */
val mainPackage: ModulePackage = ModulePackage { modulePath ->
    // The KDoc of `packageDirectoryOf` says the leading `:` may be absent, so it is never
    // matched on.
    when (modulePath.removePrefix(":")) {
        "katachi" -> "me/tbsten/katachi"
        "katachi-konsist" -> "me/tbsten/katachi/konsist"
        else -> ""
    }
}

/**
 * Where each module keeps its test sources, below its own `src/test/kotlin`.
 *
 * Test code lives one level deeper than production code, under `me.tbsten.katachi.test`, and
 * that is load bearing rather than a convention — see [projectArchitecture] for why.
 *
 * `:architecture-test` is in the table too, so this definition's own files are addressed the
 * same way as everything else it describes.
 */
val testPackage: ModulePackage = ModulePackage { modulePath ->
    when (modulePath.removePrefix(":")) {
        "katachi" -> "me/tbsten/katachi/test"
        "katachi-konsist" -> "me/tbsten/katachi/test/konsist"
        "architecture-test" -> "me/tbsten/katachi/test/architecture"
        else -> ""
    }
}

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
 * `roles/<Name>Role.kt` holds one role and `groups/<Name>Group.kt` holds one group, which says
 * what it is made of by calling the role functions in order. Both are extensions on
 * `DeclarationContainerScope` — the scope `architecture { }` and `"...".group { }` share — so
 * a role can be moved into another group without touching the role's own file. This file only
 * calls the seven group functions.
 *
 * None of those functions may be `inline`. An inlined frame reports the caller's file with a
 * line number past its end, and katachi captures the declaration site from the stack, so the
 * violation would point at a line nobody wrote. Written once here rather than repeated in
 * twenty-eight files; `DeclarationSiteSpec` is what actually holds the line.
 *
 * Two helpers sit next to this file rather than under `roles/`: `LayerImports.kt` and
 * `KdocExamples.kt` are read by roles of more than one group, and `roles/` is declared to hold
 * nothing but `*Role.kt`.
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
 * The six roles of the `library` group are the package layers of `:katachi`, each carrying a
 * `konsist { }` forbidding imports of the layers after it, and a second one pinning its package
 * to its directory so that the first means something. That half used to be a hand-written spec
 * reading the sources as text; it is a rule about imports, which is Konsist's job.
 * See `LayerImports.kt`.
 *
 * A third `konsist { }` on each of those roles, and two on `backend/KonsistBackend`, ask the
 * repository's KDoc convention of every public declaration — see `KdocExamples.kt`. It is
 * deliberately not asked of `sample/`, of this definition, or of test code.
 */
val projectArchitecture: Architecture = architecture {
    libraryGroup()
    backendGroup()
    testingGroup()
    docsGroup()
    sampleGroup()
    buildGroup()
    toolGroup()
}
