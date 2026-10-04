package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.INTERNAL_PACKAGE_RULE
import me.tbsten.katachi.test.architecture.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.KDOC_TAG_ORDER_RULE
import me.tbsten.katachi.test.architecture.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.showsExample
import me.tbsten.katachi.test.architecture.KONSIST_MAIN_PACKAGE

/**
 * The role of `:katachi-konsist`, one of the two constraint backends (the other is
 * `:katachi-konture`).
 *
 * ## Why no layer rule here
 *
 * `konsist` is the last entry of the layer table, so the layers it may not import are none,
 * and a rule with an empty forbidden list can never reject anything. Writing it would add a
 * constraint that is green by construction and says nothing — worse than absent, because it
 * reads as a rule that is being enforced. That is why the `ktFileContent { }` here has the
 * package line the `library` roles write and none of their import lines.
 *
 * What would actually be worth stating here — that the backend touches only katachi's
 * *public* surface — is already enforced by the build: `:katachi-konsist` opts into
 * `@ExperimentalKatachiApi` and deliberately not into `@InternalKatachiApi`, so an internal
 * reach fails to compile. See `katachi-konsist/build.gradle.kts`.
 *
 * The other rules do apply. This module is published, so its public declarations are a
 * surface a reader meets, and `KDOC_EXAMPLE_RULE` / `KDOC_TAG_ORDER_RULE` are the same two
 * sentences the `library` roles declare. `INTERNAL_PACKAGE_RULE` holds for the same reason:
 * `KonsistScopeImpl` and the rest of the backend's machinery live in `konsist.internal`, and a
 * reader of the public package should meet only what `konsist { }` offers.
 * `` `package`(matchingDirectory) `` applies for the same reason the layer table lists
 * `konsist` at all: the entry only means something while the files under
 * `me/tbsten/katachi/konsist/` are the ones declaring `package me.tbsten.katachi.konsist`.
 */
fun DeclarationContainerScope.konsistBackend() = "KonsistBackend".role {
    title = "Konsist バックエンド"
    summary = "konsist { } を layout { } の中に書けるようにする。katachi の public な面だけに乗る"
    example("Konsist.kt", "\"...\".konsist { } の入口")
    example("KonsistScope.kt", "Konsist の問い合わせ語彙 + must / mustNot / mustBeEmpty")
    layout {
        "katachi-konsist" {
            packageMatchesDirectory()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KONSIST_MAIN_PACKAGE / "*".ktFile()
            mainSourceSet / kotlin / KONSIST_MAIN_PACKAGE / "internal" / "*".ktFile()
            // `ktFileContent { }`: the same kind of file, Kotlin sources of this backend, so the same
            // four rules hold for them rather than a role of their own.
            mainSourceSet / kotlin / KONSIST_MAIN_PACKAGE / "declarations" / "*".ktFile()
            mainSourceSet / kotlin / KONSIST_MAIN_PACKAGE / "declarations" / "internal" / "*".ktFile()
        }
    }
}

// Written here rather than in a shared file: katachi captures the first frame outside itself as
// the declaration site of a `konsist { }`, of a `ktFileContent { }` and of each line in it, so a
// shared wrapper would make every role that declares these rules report the same line.
@OptIn(ExperimentalKatachiApi::class)
private fun LayoutScope.packageMatchesDirectory() =
    ktFileContent {
        `package`(matchingDirectory)
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
