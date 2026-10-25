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
import me.tbsten.katachi.test.architecture.*
import me.tbsten.katachi.test.architecture.KATACHI_MAIN_PACKAGE

/**
 * The role of the general-purpose helpers that know nothing about architecture definitions.
 *
 * Second in the layer table, right after the root package: what lives here is written against
 * the standard library alone, so every layer above may use it and it may use none of them. A
 * helper that needs a role, a layout or a file system is not general-purpose, and belongs to the
 * layer whose words it speaks.
 */
fun DeclarationContainerScope.util() = "Util".role {
    title = "汎用の道具"
    summary = "アーキテクチャ定義を知らない、標準ライブラリだけで書かれた道具"
    example("RunCatchingScoped.kt", "失敗を記録しながら最後まで進める runCatchingScoped")
    layout {
        "katachi" {
            packageAndLayerImports()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "util" / "*".ktFile()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "util" / "internal" / "*".ktFile()
        }
    }
}

/** The layer this role is: the entry of the layer table its import lines are built from. */
private const val LAYER: String = "util"

// Written here rather than in a shared file: katachi captures the first frame outside itself as
// the declaration site of a `konsist { }`, of a `ktFileContent { }` and of each line in it, so a
// shared wrapper would make every layer role report the same line.
@OptIn(ExperimentalKatachiApi::class)
private fun LayoutScope.packageAndLayerImports() =
    ktFileContent {
        `package`(matchingDirectory)
        `import`("**")
        laterLayerImportsOf(LAYER).forEach { not.`import`(it) }
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
