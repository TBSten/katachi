@file:OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.GroupScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.definition.KatachiDefinitionConfig
import me.tbsten.katachi.konsist.definition.KatachiDefinitionRoleConfig
import me.tbsten.katachi.konsist.definition.KatachiInvalidDefinitionArgumentException
import me.tbsten.katachi.konsist.definition.katachiDefinition

/**
 * `katachiDefinition()` end to end: a project laid out the recommended way reports nothing, and
 * each shape it is there to keep reports the file that broke it.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.konsist.test` にしてはいけない（FixtureProject.kt と同じ理由）。
 */
class KatachiDefinitionSpec : FreeSpec({
    "推奨の配置なら、違反は 1 件も出ない" {
        definitionRun(*RECOMMENDED_FILES) shouldBe emptyList()
    }

    "どの役割の制約も評価される（評価されない制約は UncheckedFileConstraint で報告される）" {
        // Without this, an empty answer above could also be the answer of a run that evaluated nothing.
        val violations = definitionRun(*RECOMMENDED_FILES, withFileConstraintCheck = false)
        violations.map { it.label }.distinct() shouldBe listOf("UncheckedFileConstraint")
        violations.size shouldBe 4
    }

    "入口に group の関数を書くと、入口のファイルが違反になる" {
        val violations = definitionRun(
            *RECOMMENDED_FILES,
            "$SOURCE/ProjectArchitecture.kt" to ENTRY.replace(
                "val projectArchitecture",
                "fun me.tbsten.katachi.dsl.DeclarationContainerScope.misplacedGroup() = Unit\n\nval projectArchitecture",
            ),
        )
        violations.unsatisfied() shouldBe listOf("$SOURCE/ProjectArchitecture.kt")
    }

    "group のファイルの関数名がファイル名と違うと違反になる" {
        val violations = definitionRun(
            *RECOMMENDED_FILES,
            "$SOURCE/api/ApiGroup.kt" to API_GROUP.replace("apiGroup", "api"),
        )
        violations.unsatisfied() shouldBe listOf("$SOURCE/api/ApiGroup.kt")
    }

    "group のファイルが宣言の package とディレクトリが違うと違反になる" {
        val violations = definitionRun(
            *RECOMMENDED_FILES,
            "$SOURCE/api/ApiGroup.kt" to API_GROUP.replace("package com.example.api", "package com.example.data"),
        )
        violations.unsatisfied() shouldBe listOf("$SOURCE/api/ApiGroup.kt")
    }

    "role のファイルに private でない宣言を足すと違反になる" {
        val violations = definitionRun(
            *RECOMMENDED_FILES,
            "$SOURCE/api/ControllerRole.kt" to CONTROLLER_ROLE + "\nclass Helper\n",
        )
        violations.unsatisfied() shouldBe listOf("$SOURCE/api/ControllerRole.kt")
    }

    "どの役割にも当てはまらないファイルは UnexpectedFile になる" {
        val violations = definitionRun(*RECOMMENDED_FILES, "$SOURCE/api/Notes.kt" to "package com.example.api\n")
        violations.filterIsInstance<UnexpectedFile>().map { it.path } shouldBe listOf("$SOURCE/api/Notes.kt")
    }

    "group の import は自由で、content { } で禁止を足すと違反になる" {
        val importsOther = "$SOURCE/api/ApiGroup.kt" to API_GROUP.replace("import ", "import com.other.Thing\nimport ")

        definitionRun(*RECOMMENDED_FILES, importsOther) shouldBe emptyList()
        definitionRun(*RECOMMENDED_FILES, importsOther, group = { content { not.`import`("com.other.**") } })
            .unsatisfied() shouldBe listOf("$SOURCE/api/ApiGroup.kt")
    }

    "recommendedContent = false にすると、その役割の推奨の制約が外れる" {
        val wrongName = "$SOURCE/api/ApiGroup.kt" to API_GROUP.replace("apiGroup", "api")

        definitionRun(*RECOMMENDED_FILES, wrongName, group = { recommendedContent = false }) shouldBe emptyList()
    }

    "enabled = false にすると、その役割のファイルはどの役割にも当てはまらない" {
        val violations = definitionRun(*RECOMMENDED_FILES, architectureTest = { enabled = false })

        violations.filterIsInstance<UnexpectedFile>().map { it.path } shouldBe listOf("$SOURCE/ProjectArchitectureTest.kt")
    }

    "役割名とファイル名を変えると、新しい名前のファイルが対象になる" {
        val files = RECOMMENDED_FILES.map { (path, text) ->
            if (path.endsWith("ApiGroup.kt")) "$SOURCE/api/ApiSection.kt" to text.replace("apiGroup", "apiSection") else path to text
        }.toTypedArray()

        definitionRun(*files, group = { name = "SectionDefinition"; fileName = "*Section" }) shouldBe emptyList()
    }

    "additional の中では設定を読めて、残りのファイルの役割を足せる" {
        val violations = definitionRun(
            *RECOMMENDED_FILES,
            "$SOURCE/Support.kt" to "package com.example\n",
            additional = { config ->
                "DefinitionSupport".role {
                    layout { "architecture-test/src/test/kotlin/${config.rootPackage.replace('.', '/')}/Support.kt".file() }
                }
            },
        )

        violations shouldBe emptyList()
    }

    "additional で四つの役割と同じ名前を宣言すると、重複として報告される" {
        shouldThrow<me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException> {
            definitionRun(*RECOMMENDED_FILES, additional = { "GroupDefinition".role { } })
        }
    }

    "rootPackage と moduleDir がその形でなければ、呼び出しの位置つきで止まる" {
        val invalidPackage = shouldThrow<KatachiInvalidDefinitionArgumentException> {
            definitionRun(*RECOMMENDED_FILES, rootPackage = "com/example")
        }
        invalidPackage.argument shouldBe "rootPackage"
        invalidPackage.value shouldBe "com/example"
        invalidPackage.declaredAt.fileName shouldBe "KatachiDefinitionSpec.kt"

        shouldThrow<KatachiInvalidDefinitionArgumentException> {
            definitionRun(*RECOMMENDED_FILES, moduleDir = "../outside")
        }.argument shouldBe "moduleDir"

        shouldThrow<KatachiInvalidDefinitionArgumentException> {
            definitionRun(*RECOMMENDED_FILES, group = { fileName = "Group.kt" })
        }.argument shouldBe "group.fileName"
    }
})

private const val SOURCE = "architecture-test/src/test/kotlin/com/example"

private const val ENTRY = """
package com.example

import com.example.api.apiGroup
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture

@OptIn(ExperimentalKatachiApi::class)
val projectArchitecture: Architecture = architecture { apiGroup() }
"""

private const val API_GROUP = """
package com.example.api

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.apiGroup() = "Api".group { controller() }
"""

private const val CONTROLLER_ROLE = """
package com.example.api

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.controller() = "Controller".role { }

private fun helper() = Unit
"""

private val RECOMMENDED_FILES: Array<Pair<String, String>> = arrayOf(
    "$SOURCE/ProjectArchitecture.kt" to ENTRY,
    "$SOURCE/ProjectArchitectureTest.kt" to "package com.example\n\nclass ProjectArchitectureTest\n",
    "$SOURCE/api/ApiGroup.kt" to API_GROUP,
    "$SOURCE/api/ControllerRole.kt" to CONTROLLER_ROLE,
)

private fun List<Violation>.unsatisfied(): List<String> = filterIsInstance<UnsatisfiedFileConstraint>().map { it.path }.distinct()

/**
 * One run of the check over a throwaway project holding [sources] (a later pair of the same path
 * replaces an earlier one), with `katachiDefinition()` as the whole definition.
 *
 * The call is written here, not in a shared wrapper: its declaration site is the first frame outside
 * katachi, and [KatachiInvalidDefinitionArgumentException] reports it.
 */
private fun definitionRun(
    vararg sources: Pair<String, String>,
    rootPackage: String = "com.example",
    moduleDir: String = "architecture-test",
    entry: KatachiDefinitionRoleConfig.Scope.() -> Unit = {},
    group: KatachiDefinitionRoleConfig.Scope.() -> Unit = {},
    role: KatachiDefinitionRoleConfig.Scope.() -> Unit = {},
    architectureTest: KatachiDefinitionRoleConfig.Scope.() -> Unit = {},
    additional: GroupScope.(KatachiDefinitionConfig) -> Unit = {},
    withFileConstraintCheck: Boolean = true,
): List<Violation> = fixtureProject(*sources.toMap().toList().toTypedArray()) { root ->
    val projectArchitecture = architecture {
        files = wholeTree()
        "Fixture".role { layout { "gradlew".file() } }
        katachiDefinition(
            rootPackage = rootPackage,
            moduleDir = moduleDir,
            entry = entry,
            group = group,
            role = role,
            architectureTest = architectureTest,
            additional = { additional(contextOf<KatachiDefinitionConfig>()) },
        )
    }
    if (withFileConstraintCheck) {
        projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
    } else {
        projectArchitecture.validate(RealFileSystem(root))
    }
}
