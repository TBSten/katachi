package me.tbsten.katachi.test.architecture.library

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

fun DeclarationContainerScope.dsl() = "Dsl".role {
    title = "DSL"
    summary = "architecture { } / group { } / role { } / layout { } の受け皿と、宣言されたモデル。走査するファイル集合の選択と、その下のファイルシステム"
    description = """
        `dsl.gradle` と `dsl.kotlin` もこの層です。`layout { }` の上に context parameter で
        書かれた語彙で、種類としては別物ですが依存としては同じ層にいます。
        `dsl/LayoutScopeImpl.kt` が `dsl.kotlin.ktsFile` を import しているので
        （モジュールの宣言が `build.gradle.kts` を注入するため）、
        「コアは語彙を import しない」という規則は今日の時点で落ちます。

        `dsl.files` もこの層です。`files = gitTracked()` が選ぶファイルシステムの拡張点
        （`KatachiFileSystem` / `FsPath` / `ProjectRoot`）と、その実装（`dsl.files.internal`）
        です。`FileSelection` が実装を import し、実装が `FileSelection` の隣にある例外を
        import するので、互いに import し合います。
    """.trimIndent()
    example("LayoutScope.kt", "layout { } の受け皿。コアの語彙はこれで全部")
    example("Architecture.kt", "宣言し終わった1つの定義")
    example("GradleGroup.kt", "gradle() が宣言する Gradle のファイル群 — dsl.gradle")
    example("FileSelection.kt", "走査するファイル集合の選び方")
    example("KatachiFileSystem.kt", "走査が触る最小のファイルシステム抽象 — dsl.files の拡張点")
    layout {
        "katachi" {
            packageAndLayerImports()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "dsl" / "*".ktFile()
            mainSourceSet / kotlin / KATACHI_MAIN_PACKAGE / "dsl" / "**" / "*".ktFile()
        }
    }
}

private const val LAYER: String = "dsl"

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
