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
import me.tbsten.katachi.test.architecture.laterLayerImportsOf
import me.tbsten.katachi.test.architecture.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.showsExample
import me.tbsten.katachi.test.architecture.KATACHI_MAIN_PACKAGE

/**
 * The role of the layer that turns a definition into Markdown.
 *
 * A sibling of `Check` rather than a part of it: both read the same declarations and produce
 * something from them, and neither is more privileged than the other. It sits after `Check` in
 * the layer table only because nothing imports it yet, which makes it the cheapest place to put.
 */
fun DeclarationContainerScope.docs() = "Docs" {
    title = "ドキュメント生成"
    summary = "宣言から役割リファレンスの Markdown を組み立て、書き出す層"
    example("GenerateDocumentation.kt", "katachiDocs の入口。組み立てた結果をディスクに置く唯一の場所")
    example("RoleReference.kt", "定義1つから、パスと中身の対応を組み立てる入口")
    example("RolePage.kt", "役割1つのページ。節ごとに関数が分かれている")
    example("Placements.kt", "layout のエントリを「配置場所」の表の行に変える")
    example("DirectoryTree.kt", "同じ行を転置して、group の README に出す配置ツリーにする")
    example("LinkCheck.kt", "組み立てた最後に、相対リンクが実在するページを指しているか確かめる")
    layout {
        "katachi" {
            packageAndLayerImports()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "docs" / "*".ktFile()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "docs" / "**" / "*".ktFile()
        }
    }
}

/** The layer this role is: the entry of the layer table its import lines are built from. */
private const val LAYER: String = "docs"

// Written here rather than in a shared file: katachi captures the first frame outside itself as
// the declaration site of a `konsist { }`, of a `ktFileContent { }` and of each line in it, so a
// shared wrapper would make every layer role report the same line.
@OptIn(ExperimentalKatachiApi::class)
private fun LayoutScope.packageAndLayerImports() =
    ktFileContent {
        `package`(matchingDirectory)
        `import`("**")
        laterLayerImportsOf(LAYER).forEach { not.`import`(it) }
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
