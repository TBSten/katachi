package me.tbsten.katachi.test.architecture.library.integrations

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
import me.tbsten.katachi.test.architecture.konsist.keepsBlockTagsLast
import me.tbsten.katachi.test.architecture.konsist.misplacedDeclarationsOf
import me.tbsten.katachi.test.architecture.konsist.publicDeclarationsOf
import me.tbsten.katachi.test.architecture.konsist.showsExample

private const val SCRIPT_MAIN_PACKAGE: String = "me/tbsten/katachi/script"

fun DeclarationContainerScope.cliModule() = "Cli".role {
    title = "katachi コマンド (katachi-script)"
    summary = "katachi コマンドの中身と、Homebrew の formula が展開する配布物"
    description = """
        `katachi.main.kts` が `@file:DependsOn` で引く artifact です。サブコマンドはすべてここに Kotlin で書いてあります。
    """.trimIndent()
    example("Main.kt", "katachi.main.kts が呼ぶ main(args)")
    example("lint.kt", "スクリプトの入口 katachi.lint。定義を読んで、怪しい Role や group を挙げる")
    example("scaffold.kt", "スクリプトの入口 katachi.scaffold。ビルダーの型は command パッケージ")
    example("command/ScaffoldCommand.kt", "スクリプトから katachi scaffold を組み立てる DSL。メソッド名が CLI のフラグ名")
    example("katachi.main.kts", "@file:DependsOn で katachi-script を引いて main(args) を呼ぶ")
    example("bin/katachi", "KATACHI_HOME を決めて kotlin katachi.main.kts を exec する")
    example("bin/katachi.cmd", "Windows 用。kotlin.bat を呼ぶ（CRLF のまま保つ）")
    layout {
        "katachi-script" {
            packageMatchesDirectory()
            internalDeclarationsInInternalPackage()
            publicDeclarationsShowExample()
            publicDeclarationsKeepTagsLast()
            mainSourceSet / kotlin / SCRIPT_MAIN_PACKAGE / "*".ktFile()
            mainSourceSet / kotlin / SCRIPT_MAIN_PACKAGE / "command" / "*".ktFile()
            mainSourceSet / kotlin / SCRIPT_MAIN_PACKAGE / "error" / "*".ktFile()
            mainSourceSet / kotlin / SCRIPT_MAIN_PACKAGE / "option" / "*".ktFile()
            mainSourceSet / kotlin / SCRIPT_MAIN_PACKAGE / "internal" / "*".ktFile()
            // What `katachi --version` prints. Written as `version=${katachiVersion}` and expanded by `processResources`.
            mainSourceSet / "resources" / "META-INF" / "katachi-script" / "version.properties".file()
            // Empty; its name tells `kotlin -cp` and the IDE which class is the `*.katachi.kts` script definition.
            mainSourceSet / "resources" / "META-INF" / "kotlin" / "script" / "templates" / "me.tbsten.katachi.script.KatachiScript.classname".file()
        }

        "katachi-script" {
            // `${katachiVersion}` inside is a template `cliDist` expands, so the checked-in file is not runnable as is.
            "cli" / "katachi.main.kts".file()
            "cli" / "bin" / "katachi".file()
            "cli" / "bin" / "katachi.cmd".file()
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
