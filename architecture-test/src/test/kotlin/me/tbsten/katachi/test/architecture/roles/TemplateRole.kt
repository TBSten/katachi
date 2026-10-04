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
 * The role of the layer that turns a role's `template { }` into files in the repository.
 *
 * A sibling of `Docs`: both read the declarations and produce something from them, and both keep
 * the same split — a pure function builds a map of path to contents, and one thin shell puts it on
 * a disk. It sits after `Docs` in the layer table only because nothing imports it.
 */
fun DeclarationContainerScope.template() = "Template".role {
    title = "テンプレート生成"
    summary = "役割の template { } を replay して、layout が示す場所へファイルを書き出す層"
    example("GenerateCodeFromTemplate.kt", "katachiTemplate の入口。--arg を受け取り、書き出しを起動する")
    example("TemplateGeneration.kt", "宣言と値から「パスと中身」の対応を組み立てる純粋関数")
    example("TemplatePlacement.kt", "生成したファイル名を layout のパターンと突き合わせ、置き場所を1つに決める")
    example("TemplateOutput.kt", "組み立てた結果をディスクに置く唯一の場所。全部書くか1つも書かないか")
    layout {
        "katachi" {
            packageAndLayerImports()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "template" / "*".ktFile()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "template" / "**" / "*".ktFile()
        }
    }
}

/** The layer this role is: the entry of the layer table its import lines are built from. */
private const val LAYER: String = "template"

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
