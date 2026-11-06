package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

fun DeclarationContainerScope.definitionSupport() = "DefinitionSupport".role {
    title = "定義の共通部品"
    summary = "複数の group の役割が読む、規則の名前・判定の関数・パッケージのパス。定義の入口の隣に置く"
    example("LayerImports.kt", "層ごとに import してはいけない、後ろの層のパッケージ")
    example("PackagePaths.kt", "layout で使うパッケージのパス")
    layout {
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE {
            // None holds a wildcard, so all of them are required: a new shared file is a decision
            // written here, and moving one under a group's package is reported.
            "LayerImports".ktFile()
            "InternalPackages".ktFile()
            "KdocExamples".ktFile()
            "PackagePaths".ktFile()
        }
    }
    supportHoldsConstantsAndFunctionsOnly()
}

@OptIn(ExperimentalKatachiApi::class)
private fun RoleScope.supportHoldsConstantsAndFunctionsOnly() =
    "共通部品は public な定数と関数だけを持ち、型を宣言しない".ktFileContent {
        `package`("me.tbsten.katachi.test.architecture")

        `import`("com.lemonappdev.konsist.**")

        allow(const, `val`, "*")
        allow(`fun`, "*")
        allow(private, anythingElse)
    }
