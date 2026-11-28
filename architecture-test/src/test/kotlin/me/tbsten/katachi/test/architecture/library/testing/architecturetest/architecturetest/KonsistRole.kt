package me.tbsten.katachi.test.architecture.library.testing.architecturetest.architecturetest

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.test.architecture.ARCHITECTURE_TEST_PACKAGE

fun DeclarationContainerScope.konsist() = "Konsist".role {
    title = "Konsist の規則"
    summary = "層の role が konsist { } で使う、KDoc の例と internal パッケージの規則"
    example("KdocExamples.kt", "公開する宣言の KDoc に例が付いていることを確かめる規則")
    example("InternalPackages.kt", "internal の宣言が internal パッケージにあることを確かめる規則")
    layout {
        "architecture-test" / testSourceSet / kotlin / ARCHITECTURE_TEST_PACKAGE / "konsist" {
            // None holds a wildcard, so all of them are required: a new shared file is a decision
            // written here, and moving one under a group's package is reported.
            "InternalPackages".ktFile()
            "KdocExamples".ktFile()
        }
    }
    supportHoldsConstantsAndFunctionsOnly()
}

@OptIn(ExperimentalKatachiApi::class)
private fun RoleScope.supportHoldsConstantsAndFunctionsOnly() =
    "共通部品は public な定数と関数だけを持ち、型を宣言しない".ktFileContent {
        `package`(matchingDirectory)

        `import`("com.lemonappdev.konsist.**")

        allow(const, `val`, "*")
        allow(`fun`, "*")
        allow(private, anythingElse)
    }
