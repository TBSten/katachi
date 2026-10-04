package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.INTERNAL_PACKAGE_RULE
import me.tbsten.katachi.test.architecture.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.KDOC_TAG_ORDER_RULE
import me.tbsten.katachi.test.architecture.PACKAGE_MATCHES_PATH_RULE
import me.tbsten.katachi.test.architecture.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.showsExample
import me.tbsten.katachi.test.architecture.KONTURE_MAIN_PACKAGE

/**
 * The role of `:katachi-konture`, the second constraint backend and the Konsist backend's sibling.
 *
 * ## Why no layer rule here
 *
 * Same reason as `KonsistBackend`: `konture` shares the last entry of the layer table, so the
 * layers it may not import are none, and a rule with an empty forbidden list is green by
 * construction. The backend touching only katachi's *public* surface is enforced by the build
 * (`@ExperimentalKatachiApi` opted into, `@InternalKatachiApi` deliberately not). See
 * `katachi-konture/build.gradle.kts`.
 *
 * The other four rules apply for the reasons given on `KonsistBackend`: public declarations are
 * a surface a reader meets (`KDOC_EXAMPLE_RULE` / `KDOC_TAG_ORDER_RULE`), the machinery lives in
 * `konture.internal` (`INTERNAL_PACKAGE_RULE`), and the package follows the path
 * (`PACKAGE_MATCHES_PATH_RULE`).
 *
 * The rules themselves are written with Konsist, not Konture: this definition's own rules are
 * Konsist's job, and the two cannot share a classpath anyway.
 */
fun DeclarationContainerScope.kontureBackend() = "KontureBackend".role {
    title = "Konture バックエンド"
    summary = "konture { } を layout { } の中に書けるようにする。katachi の public な面だけに乗る"
    example("Konture.kt", "\"...\".konture { } の入口")
    example("KontureConstraintScope.kt", "Konture の規則の入口 files / classes / functions / properties")
    layout {
        "katachi-konture" {
            packageMatchesPath()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KONTURE_MAIN_PACKAGE / "*".ktFile()
            mainSourceSet / kotlin / KONTURE_MAIN_PACKAGE / "internal" / "*".ktFile()
        }
    }
}

// Written here rather than in a shared file: `konsist { }` captures the first frame outside
// katachi as its declaration site, so a shared wrapper would make every role that declares
// these three rules report the same line.
private fun LayoutScope.packageMatchesPath() =
    PACKAGE_MATCHES_PATH_RULE.konsist {
        packages.must { it.hasMatchingPath }
    }

private fun LayoutScope.publicDeclarationsShowExample() =
    KDOC_EXAMPLE_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::showsExample)
    }

private fun LayoutScope.publicDeclarationsKeepTagsLast() =
    KDOC_TAG_ORDER_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::keepsBlockTagsLast)
    }

private fun LayoutScope.internalDeclarationsInInternalPackage() =
    INTERNAL_PACKAGE_RULE.konsist {
        val sealedParents = classesAndInterfaces(includeNested = true)
            .filter { it.hasSealedModifier }
            .map { it.name }
            .toSet()
        files.flatMap { misplacedDeclarationsOf(it, sealedParents) }.mustBeEmpty()
    }
