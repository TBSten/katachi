package me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturedefinition

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

fun DeclarationContainerScope.constants() = "Constants".role {
    title = "定義の定数"
    summary = "複数の role が読む、層ごとの import の禁止とパッケージのパス"
    example("LayerImports.kt", "層ごとに import してはいけない、後ろの層のパッケージ")
    example("PackagePaths.kt", "layout で使うパッケージのパス")
    layout {
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE {
            // None holds a wildcard, so all of them are required: a new shared file is a decision
            // written here, and moving one under a group's package is reported.
            "LayerImports".ktFile()
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
