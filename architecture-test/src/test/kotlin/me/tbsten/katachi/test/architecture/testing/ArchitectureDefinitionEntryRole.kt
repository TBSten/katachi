package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

fun DeclarationContainerScope.architectureDefinitionEntry() = "ArchitectureDefinitionEntry".role {
    title = "アーキテクチャ定義の入口"
    summary = "architecture { } で group ごとの拡張関数を呼ぶだけの1ファイル。定義はここから読み始める"
    example("ProjectArchitecture.kt", "定義の入口")
    layout {
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "ProjectArchitecture".ktFile()
    }
    entryHoldsOnlyTheArchitecture()
}

@OptIn(ExperimentalKatachiApi::class)
private fun RoleScope.entryHoldsOnlyTheArchitecture() =
    "入口は val projectArchitecture だけを持ち、group や役割を宣言しない".ktFileContent {
        `package`("me.tbsten.katachi.test.architecture")

        `import`("me.tbsten.katachi.dsl.**")                  // architecture { } itself
        `import`("me.tbsten.katachi.test.architecture.*.*")   // the group functions it calls

        required(`val`, "projectArchitecture").type("Architecture")
    }
