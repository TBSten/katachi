package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist
import me.tbsten.katachi.test.architecture.INTERNAL_PACKAGE_RULE
import me.tbsten.katachi.test.architecture.KDOC_EXAMPLE_RULE
import me.tbsten.katachi.test.architecture.PACKAGE_MATCHES_PATH_RULE
import me.tbsten.katachi.test.architecture.importsLaterLayerThan
import me.tbsten.katachi.test.architecture.laterLayersOf
import me.tbsten.katachi.test.architecture.mainPackage
import me.tbsten.katachi.test.architecture.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.showsExample

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
    example("GenerateDocumentation.kt", "--processor=docs の入口。組み立てた結果をディスクに置く唯一の場所")
    example("RoleReference.kt", "定義1つから、パスと中身の対応を組み立てる入口")
    example("RolePage.kt", "役割1つのページ。節ごとに関数が分かれている")
    example("Placements.kt", "layout のエントリを「配置場所」の表の行に変える")
    example("DirectoryTree.kt", "同じ行を転置して、group の README に出す配置ツリーにする")
    example("LinkCheck.kt", "組み立てた最後に、相対リンクが実在するページを指しているか確かめる")
    layout {
        ":katachi".module {
            importsOnlyEarlierLayers()
            packageMatchesPath()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            mainSourceSet / kotlin / mainPackage / "docs" / "*".ktFile()
            mainSourceSet / kotlin / mainPackage / "docs" / "**" / "*".ktFile()
        }
    }
}

/** The layer this role is, named once so the rule and its wording cannot drift apart. */
private const val LAYER: String = "docs"

// Written here rather than in a shared file: `konsist { }` captures the first frame outside
// katachi as its declaration site, so a shared wrapper would make every layer role report the
// same line.
private fun LayoutScope.importsOnlyEarlierLayers() =
    "${laterLayersOf(LAYER)} を import しないこと".konsist {
        files.mustNot(importsLaterLayerThan(LAYER))
    }

private fun LayoutScope.packageMatchesPath() =
    PACKAGE_MATCHES_PATH_RULE.konsist {
        packages.must { it.hasMatchingPath }
    }

private fun LayoutScope.publicDeclarationsShowExample() =
    KDOC_EXAMPLE_RULE.konsist {
        files.flatMap(::publicDeclarationsOf).must(::showsExample)
    }

private fun LayoutScope.internalDeclarationsInInternalPackage() =
    INTERNAL_PACKAGE_RULE.konsist {
        val sealedParents = classesAndInterfaces(includeNested = true)
            .filter { it.hasSealedModifier }
            .map { it.name }
            .toSet()
        files.flatMap { misplacedDeclarationsOf(it, sealedParents) }.mustBeEmpty()
    }
