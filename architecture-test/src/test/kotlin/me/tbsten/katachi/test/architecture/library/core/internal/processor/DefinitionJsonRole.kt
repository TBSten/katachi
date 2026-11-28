package me.tbsten.katachi.test.architecture.library.core.internal.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.konsist.INTERNAL_PACKAGE_RULE
import me.tbsten.katachi.test.architecture.konsist.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.konsist.KDOC_TAG_ORDER_RULE
import me.tbsten.katachi.test.architecture.laterLayerImportsOf
import me.tbsten.katachi.test.architecture.konsist.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.konsist.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.konsist.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.konsist.showsExample
import me.tbsten.katachi.test.architecture.KATACHI_MAIN_PACKAGE

fun DeclarationContainerScope.definitionJson() = "DefinitionJson".role {
    title = "definition.json の形"
    summary = "IDE プラグインが読む definition.json の形と、それを組み立てる処理"
    example("DefinitionJsonBuilder.kt", "走査とテンプレートから definition.json の形を組み立てる")
    layout {
        "katachi" {
            packageAndLayerImports()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "ide" / "internal" / "DefinitionJson*".ktFile()
        }
    }
}

private const val LAYER: String = "ide"

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
