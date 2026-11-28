package me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturetest

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

fun DeclarationContainerScope.katachiDefinition() = "KatachiDefinition".role {
    title = "katachi 自身の定義"
    summary = "architecture { } で group ごとの拡張関数を呼ぶだけの入口と、この定義でリポジトリ全体が宣言しきれていることを確かめるスペック。定義はここから読み始める"
    example("ProjectArchitecture.kt", "定義の入口")
    example("ProjectArchitectureSpec.kt", "この定義でリポジトリ全体が宣言しきれていることを確かめる")
    layout {
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE {
            entryHoldsOnlyTheArchitecture()
            "ProjectArchitecture".ktFile()
        }
        // The one `*Spec.kt` of this module that is not a `Spec`: it runs the definition itself.
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE {
            "ProjectArchitectureSpec".ktFile()
        }
    }
}

@OptIn(ExperimentalKatachiApi::class)
private fun LayoutScope.entryHoldsOnlyTheArchitecture() =
    ktFileContent {
        `package`("me.tbsten.katachi.test.architecture")

        `import`("me.tbsten.katachi.dsl.**")                  // architecture { } itself
        `import`("me.tbsten.katachi.test.architecture.**")    // the group functions it calls

        required(`val`, "projectArchitecture").type("Architecture")
    }
