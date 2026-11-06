package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

fun DeclarationContainerScope.definitionEntryPoint() = "DefinitionEntryPoint".role {
    title = "定義のエントリーポイント"
    summary = "Gradle プラグインが生成するエントリーポイントの手書きの代わり。katachiDiff がクラス名で読み込む"
    example("ProjectArchitectureEntryPoint.kt", "定義と processor を katachiDiff に渡す object")
    layout {
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "ProjectArchitectureEntryPoint".ktFile()
    }
    entryPointIsOneObject()
}

@OptIn(ExperimentalKatachiApi::class)
private fun RoleScope.entryPointIsOneObject() =
    "エントリーポイントは KatachiEntryPoint を実装する object 1つだけ".ktFileContent {
        `package`("me.tbsten.katachi.test.architecture")

        `import`("me.tbsten.katachi.**")

        required(`object`, fileName).superclass("KatachiEntryPoint") {
            allow(override, `val`, "*")
        }
    }
