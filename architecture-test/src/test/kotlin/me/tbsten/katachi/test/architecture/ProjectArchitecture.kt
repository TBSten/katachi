package me.tbsten.katachi.test.architecture

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.ModulePackage
import me.tbsten.katachi.test.architecture.docs.docsRoles
import me.tbsten.katachi.test.architecture.gradle.gradleRoles
import me.tbsten.katachi.test.architecture.library.backendRoles
import me.tbsten.katachi.test.architecture.library.libraryRoles
import me.tbsten.katachi.test.architecture.sample.sampleRoles
import me.tbsten.katachi.test.architecture.testing.testingRoles
import me.tbsten.katachi.test.architecture.tool.toolRoles

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
 * library: one independent JVM module, the definition split by meaning into one package per
 * concern, and `assert()` run from a test. katachi denies by default, so this is an allow
 * list — every file `files = gitTracked()` offers has to be covered by some role, and
 * anything else fails `ProjectArchitectureSpec`.
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
 * The seven roles of the `library` group are the package layers of `:katachi`, each carrying a
 * `konsist { }` forbidding imports of the layers after it, and a second one pinning its package
 * to its directory so that the first means something. That half used to be a hand-written spec
 * reading the sources as text; it is a rule about imports, which is Konsist's job.
 * See `library/LayerImports.kt`.
 *
 * A third `konsist { }` on each of those roles, and on `backend/KonsistBackend`, asks the
 * repository's KDoc convention of every public declaration — see `library/KdocExamples.kt`.
 * It is deliberately not asked of `sample/`, of this definition, or of test code.
 */
val projectArchitecture: Architecture = architecture {
    libraryRoles()
    backendRoles()
    testingRoles()
    docsRoles()
    sampleRoles()
    gradleRoles()
    toolRoles()
}
