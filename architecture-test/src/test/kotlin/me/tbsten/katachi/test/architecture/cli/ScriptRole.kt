package me.tbsten.katachi.test.architecture.cli

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
import me.tbsten.katachi.test.architecture.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.showsExample

private const val SCRIPT_MAIN_PACKAGE: String = "me/tbsten/katachi/script"

fun DeclarationContainerScope.script() = "Script".role {
    title = "katachi-script"
    summary = "katachi コマンドの中身。公開するのは main と、スクリプトからコマンドを組み立てる DSL。サブコマンドの実装は internal"
    description = """
        `katachi.main.kts` が `@file:DependsOn` で引く artifact です。サブコマンドはすべてここに Kotlin で書いてあり、init が置く手順書とテンプレートも artifact に同梱します（ビルド時に docs/public/install から写すので、ソースには置きません）。
    """.trimIndent()
    example("Main.kt", "katachi.main.kts が呼ぶ main(args)")
    example("DataCommand.kt", "data get / set / merge。チェックリストとレポートの JSON を読み書きする")
    example("scaffold.kt", "スクリプトの入口 katachi.scaffold。ビルダーの型は command パッケージ")
    example("command/ScaffoldCommand.kt", "スクリプトから katachi scaffold を組み立てる DSL。メソッド名が CLI のフラグ名")
    layout {
        "katachi-script" {
            packageMatchesDirectory()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / SCRIPT_MAIN_PACKAGE / "*".ktFile()
            mainSourceSet / kotlin / SCRIPT_MAIN_PACKAGE / "command" / "*".ktFile()
            mainSourceSet / kotlin / SCRIPT_MAIN_PACKAGE / "internal" / "*".ktFile()
            // What `katachi --version` prints. Written as `version=${katachiVersion}` and expanded by `processResources`.
            mainSourceSet / "resources" / "META-INF" / "katachi-script" / "version.properties".file()
            // Empty; its name tells `kotlin -cp` and the IDE which class is the `*.katachi.kts` script definition.
            mainSourceSet / "resources" / "META-INF" / "kotlin" / "script" / "templates" / "me.tbsten.katachi.script.KatachiScript.classname".file()
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
        // The last layer: it may reach every package of katachi, and the kotlinx.serialization JSON it reads with.
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
