package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.PACKAGE_MATCHES_PATH_RULE
import me.tbsten.katachi.test.architecture.mainPackage
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.showsExample

/**
 * The role of `:katachi-konsist`, today's only constraint backend.
 *
 * ## Why no layer rule here
 *
 * `konsist` is the last entry of the layer table, so the layers it may not import are none,
 * and a rule with an empty forbidden list can never reject anything. Writing it would add a
 * constraint that is green by construction and says nothing — worse than absent, because it
 * reads as a rule that is being enforced. That is why this file carries two helpers where the
 * six `library` roles carry three.
 *
 * What would actually be worth stating here — that the backend touches only katachi's
 * *public* surface — is already enforced by the build: `:katachi-konsist` opts into
 * `@ExperimentalKatachiApi` and deliberately not into `@InternalKatachiApi`, so an internal
 * reach fails to compile. See `katachi-konsist/build.gradle.kts`.
 *
 * The other two rules do apply. This module is published, so its public declarations are a
 * surface a reader meets, and `KDOC_EXAMPLE_RULE` is the same sentence the six library roles
 * declare. `PACKAGE_MATCHES_PATH_RULE` applies for the same reason the layer table lists
 * `konsist` at all: the entry only means something while the files under
 * `me/tbsten/katachi/konsist/` are the ones declaring `package me.tbsten.katachi.konsist`.
 */
fun DeclarationContainerScope.konsistBackend() = "KonsistBackend" {
    title = "Konsist バックエンド"
    summary = "konsist { } を layout { } の中に書けるようにする。katachi の public な面だけに乗る"
    example("Konsist.kt", "\"...\".konsist { } の入口")
    example("KonsistScope.kt", "Konsist の問い合わせ語彙 + must / mustNot / mustBeEmpty")
    layout {
        ":katachi-konsist".module {
            packageMatchesPath()
            publicDeclarationsShowExample()
            mainSourceSet / kotlin / mainPackage / "*".ktFile()
        }
    }
}

// Written here rather than in a shared file: `konsist { }` captures the first frame outside
// katachi as its declaration site, so a shared wrapper would make every role that declares
// these two rules report the same line.
private fun LayoutScope.packageMatchesPath() =
    PACKAGE_MATCHES_PATH_RULE.konsist {
        packages.must { it.hasMatchingPath }
    }

private fun LayoutScope.publicDeclarationsShowExample() =
    KDOC_EXAMPLE_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::showsExample)
    }
