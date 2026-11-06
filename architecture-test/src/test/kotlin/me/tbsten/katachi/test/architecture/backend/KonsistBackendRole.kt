package me.tbsten.katachi.test.architecture.backend

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
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

fun DeclarationContainerScope.konsistBackend() = "KonsistBackend".role {
    title = "Konsist バックエンド"
    summary = "konsist { } を layout { } の中に書けるようにする。katachi の public な面だけに乗る"
    description = """
        `katachi-konsist/build.gradle.kts` で opt-in しているのは `@ExperimentalKatachiApi` だけです。
        IDE プラグインに判定の経緯を渡す `declarations/internal` の 2 ファイルだけが、`@file:OptIn(InternalKatachiApi::class)` で katachi の `dsl.internal` を使っています。
    """.trimIndent()
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
        // No layer comes after this one, and what of katachi it may reach is the build's opt-in (see `katachi-konsist/build.gradle.kts`).
        `import`("**")
        // What a declaration may be is left to the `konsist { }` rules below, which read the KDoc and the `.internal` line.
        allow(anythingElse)
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
