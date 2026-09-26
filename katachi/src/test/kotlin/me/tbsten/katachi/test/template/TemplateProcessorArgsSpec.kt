// NOTE: the package is deliberately `me.tbsten.katachi.test.template` and not
// `me.tbsten.katachi.template` -- `captureDeclarationSite()` skips frames of the library's own
// packages, so a `template { }` written under the latter would be reported at the wrong line.
package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.docs.GenerateDocumentation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiInvalidTemplateParameterValueException
import me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.KatachiUnknownProcessorArgException
import me.tbsten.katachi.processor.internal.checkNoUnknownArgs
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.KatachiNoTemplateException
import me.tbsten.katachi.template.KatachiUnknownTemplateRoleException
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

/**
 * What `--processor=docs,template` accepts and what it refuses.
 *
 * The one place the whole `--arg` decision is pinned end to end: `template` needs names no
 * `@Serializable` class can declare, `docs` declares all of its own, and both are judged against
 * one union. The hole the alternative design would have left -- a `docs` typo swallowed by a
 * `template` that takes anything -- is asserted as a failure here so that reopening it turns this
 * file red.
 */
class TemplateProcessorArgsSpec : FreeSpec({
    "--processor=docs,template" - {
        "表明なしで、その役割のパラメータが --arg として通る" {
            checkBoth(
                values = mapOf(
                    "roleName" to "UseCase",
                    "name" to "GetItemList",
                    "implBody" to """println("hi")""",
                    "outputDir" to "docs/architecture",
                ),
            )
        }

        "docs 宛てのタイポは、template のパラメータが通っていても落ちる" {
            // 「未宣言の --arg を何でも受け取る」だったら、これが template に吸われて
            // 黙って通っていたはず。ここがそうならないことの確認。
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(
                    values = mapOf(
                        "roleName" to "UseCase",
                        "name" to "GetItemList",
                        "outputDirr" to "docs/architecture",
                    ),
                )
            }

            thrown.unknown shouldBe setOf("outputDirr")
        }

        "roleName が無いと template は1つも答えられないので、パラメータは通らない" {
            // どの役割の template かが決まらない。契約どおり例外ではなく emptySet で答えるので、
            // 落ちるのは未知キーとしてになる。
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(values = mapOf("name" to "GetItemList"))
            }

            thrown.unknown shouldBe setOf("name")
        }

        "別の役割の template が宣言している名前は通らない" {
            // 答えるのは「この run が名指しした役割が宣言した名前」ちょうど。
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(values = mapOf("roleName" to "UseCase", "screenTitle" to "Home"))
            }

            thrown.unknown shouldBe setOf("screenTitle")
        }

        "docs と template が宣言しているキーは、両方同時に選んでも通る" {
            checkBoth(values = mapOf("roleName" to "UseCase", "mode" to "check"))
        }
    }

    "undeclaredArgNames" - {
        "template は roleName / onExisting を答えない -- どちらも Args が宣言している側" {
            GenerateCodeFromTemplate.undeclaredArgNames(contextOf(mapOf("roleName" to "UseCase"))) shouldBe
                setOf("name", "implBody")
        }

        "値を読まなかったパラメータも名前として数えられる" {
            GenerateCodeFromTemplate.undeclaredArgNames(contextOf(mapOf("roleName" to "UseCase")))
                .shouldContain("implBody")
        }

        "知らない役割を名指しされたら、その場で落ちる" {
            // 以前はここで emptySet を返していた。すると template のパラメータが「知らない
            // 引数」になり、`--arg roleName=Servce --arg name=Greeting` は roleName ではなく
            // name のほうを誤字として報告した。最初に間違っているものを言う。
            shouldThrow<KatachiUnknownTemplateRoleException> {
                GenerateCodeFromTemplate.undeclaredArgNames(contextOf(mapOf("roleName" to "Nope")))
            }.roleName shouldBe "Nope"
        }

        "template を持たない役割も、その場で落ちる" {
            shouldThrow<KatachiNoTemplateException> {
                GenerateCodeFromTemplate.undeclaredArgNames(contextOf(mapOf("roleName" to "Entity")))
            }
        }

        "roleName を渡していなければ何も答えない" {
            withClue("roleName が無い run は template を選んでいないか、Args 側で落ちる") {
                GenerateCodeFromTemplate.undeclaredArgNames(contextOf(emptyMap())) shouldBe emptySet()
            }
        }
    }

    "型付きのパラメータ" - {
        "型付きのパラメータの名前も --arg の名前として受け付けられる" {
            GenerateCodeFromTemplate.undeclaredArgNames(
                contextOf(mapOf("roleName" to "Repository", "withImpl" to "false")),
            ) shouldBe setOf("name", "withImpl")
        }

        "--arg withImpl=true で開いた分岐の中のパラメータは未知の引数にならない" {
            // 名前を集める replay に実際の値を渡しているから通る。空の値で replay すると
            // 分岐が閉じたまま名前を集め、implName が未知の引数として落ちる。
            checkBoth(
                mapOf(
                    "roleName" to "Repository",
                    "name" to "User",
                    "withImpl" to "true",
                    "implName" to "UserRepositoryImpl",
                ),
            )
        }

        "--arg withImpl=false のとき、閉じた分岐の中のパラメータを渡すと未知の引数として落ちる" {
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(
                    mapOf(
                        "roleName" to "Repository",
                        "name" to "User",
                        "withImpl" to "false",
                        "implName" to "UserRepositoryImpl",
                    ),
                )
            }

            thrown.unknown shouldBe setOf("implName")
            withClue("綴りは合っているので、分岐を開く値が要ることまで言わないと直し方に辿り着けない") {
                thrown.knownDependsOnValues shouldBe true
                thrown.message.orEmpty() shouldContain
                    "If the key is spelled as declared, pass the value that opens its branch."
            }
        }

        "--arg withImpl=True と閉じた分岐のパラメータを同時に渡すと、未知の引数ではなく withImpl の読めない値として、どの processor よりも先に落ちる" {
            val values = mapOf(
                "roleName" to "Repository",
                "name" to "User",
                "withImpl" to "True",
                "implName" to "UserRepositoryImpl",
            )

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                checkBoth(values)
            }.names shouldBe listOf("withImpl")
        }

        "default の無い booleanParameter を渡さずに分岐の中のパラメータを渡すと、未知の引数ではなく Missing として、どの processor よりも先に落ちる" {
            val values = mapOf(
                "roleName" to "Repository",
                "name" to "User",
                "implName" to "UserRepositoryImpl",
            )

            shouldThrow<KatachiMissingTemplateParameterException> {
                checkBoth(values)
            }.names shouldBe listOf("withImpl")
        }

        "template の値が読めない実行でも、docs 宛てのタイポは素通りしない" {
            // Accepting every key whenever the template's names are in doubt would make this
            // `mdoe` known to the whole run, and docs would run on its default mode.
            val values = mapOf(
                "roleName" to "Repository",
                "name" to "User",
                "withImpl" to "True",
                "mdoe" to "check",
            )

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                checkBoth(values)
            }.names shouldBe listOf("withImpl")
            shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(values + ("withImpl" to "true") + ("implName" to "UserRepositoryImpl"))
            }.unknown shouldBe setOf("mdoe")
        }

        "String の必須パラメータの名前を打ち間違えると、今どおり未知の引数として落ちる" {
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(mapOf("roleName" to "UseCase", "nmae" to "GetUser"))
            }

            thrown.unknown shouldBe setOf("nmae")
        }

        "パラメータ名の大文字小文字違いは未知の引数として落ちる" {
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(
                    mapOf(
                        "roleName" to "Repository",
                        "name" to "User",
                        "withImpl" to "false",
                        "WithImpl" to "true",
                    ),
                )
            }

            thrown.unknown shouldBe setOf("WithImpl")
        }
    }

    "roleName の誤字" - {
        "誤字は roleName のほうが報告され、パラメータの名前にすり替わらない" {
            val thrown = shouldThrow<KatachiUnknownTemplateRoleException> {
                checkBoth(mapOf("roleName" to "Servce", "name" to "Greeting"))
            }

            thrown.roleName shouldBe "Servce"
            withClue("候補が出ないと、利用者は自分の打ち間違いに辿り着けない") {
                thrown.message.shouldNotBeNull() shouldContain "UseCase"
            }
        }
    }
})

/**
 * Three roles with a template and one without, so "which role answered" is visible in the result.
 * `Repository` declares a parameter inside a branch its required `withImpl` decides.
 */
private fun templatesArchitecture(): Architecture = architecture {
    "domain".group {
        "UseCase" {
            layout { "useCase" / "*UseCase.kt".file() }
            template {
                val name by stringParameter()
                val implBody by stringParameter(default = """TODO("not implemented")""")

                file("${name}UseCase.kt") { "interface ${name}UseCase { /* $implBody */ }" }
            }
        }
        "Entity" { layout { "entity" / "*.kt".file() } }
    }
    "data".group {
        "Repository" {
            layout { "repository" / "*.kt".file() }
            template {
                val name by stringParameter()
                val withImpl by booleanParameter()

                file("${name}Repository.kt") { "interface ${name}Repository" }
                if (withImpl) {
                    val implName by stringParameter()
                    file("$implName.kt") { "class $implName : ${name}Repository" }
                }
            }
        }
    }
    "ui".group {
        "Screen" {
            layout { "ui" / "*Screen.kt".file() }
            template {
                val screenTitle by stringParameter()

                file("${screenTitle}Screen.kt") { "// $screenTitle" }
            }
        }
    }
}

/**
 * A context over [templatesArchitecture] whose tree refuses to be read.
 *
 * [ForbiddenFileSystem] is the assertion: `undeclaredArgNames` runs before any processor does, so
 * a walk started here would make deciding "is this `--arg` a typo" cost a scan of the project.
 */
private fun contextOf(values: Map<String, String>) = FakeArchitectureProcessContext(
    architecture = templatesArchitecture(),
    args = Unit,
    fileSystem = ForbiddenFileSystem,
    rawArgs = values,
)

/** Judges [values] the way one `--processor=docs,template` run does, before anything runs. */
private fun checkBoth(values: Map<String, String>) {
    val selected: List<Pair<String, ArchitectureProcessor<*, *>>> = listOf(
        "docs" to GenerateDocumentation,
        "template" to GenerateCodeFromTemplate,
    )
    checkNoUnknownArgs(
        selected = selected,
        context = contextOf(values),
        values = values,
    )
}
