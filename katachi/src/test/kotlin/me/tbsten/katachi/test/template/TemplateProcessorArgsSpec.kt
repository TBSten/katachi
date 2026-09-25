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
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.KatachiUnknownProcessorArgException
import me.tbsten.katachi.processor.checkNoUnknownArgs
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.KatachiNoTemplateException
import me.tbsten.katachi.template.KatachiUnknownTemplateRoleException
import me.tbsten.katachi.test.fs.ForbiddenFileSystem

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
        "template が手を挙げていれば、その役割のパラメータが --arg として通る" {
            checkBoth(
                acceptsUndeclaredArgs = setOf("template"),
                values = mapOf(
                    "roleName" to "UseCase",
                    "name" to "GetItemList",
                    "implBody" to """println("hi")""",
                    "outputDir" to "docs/architecture",
                ),
            )
        }

        "docs 宛てのタイポは、template が手を挙げていても落ちる" {
            // (a)「未宣言の --arg を何でも受け取る」を選んでいたら、これが template に吸われて
            // 黙って通っていた。ここが (b) を選んだ理由そのもの。
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(
                    acceptsUndeclaredArgs = setOf("template"),
                    values = mapOf(
                        "roleName" to "UseCase",
                        "name" to "GetItemList",
                        "outputDirr" to "docs/architecture",
                    ),
                )
            }

            thrown.unknown shouldBe setOf("outputDirr")
        }

        "手を挙げていないと、template のパラメータは未知キーとして落ちる" {
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(values = mapOf("roleName" to "UseCase", "name" to "GetItemList"))
            }

            thrown.unknown shouldBe setOf("name")
            thrown.notAllowed shouldBe mapOf("template" to setOf("name"))
            thrown.message.shouldNotBeNull() shouldContain "acceptsUndeclaredArgs = true"
        }

        "roleName が無いと template は1つも答えられないので、パラメータは通らない" {
            // どの役割の template かが決まらない。契約どおり例外ではなく emptySet で答えるので、
            // 落ちるのは未知キーとしてになる。
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(
                    acceptsUndeclaredArgs = setOf("template"),
                    values = mapOf("name" to "GetItemList"),
                )
            }

            thrown.unknown shouldBe setOf("name")
        }

        "別の役割の template が宣言している名前は通らない" {
            // 答えるのは「この run が名指しした役割が宣言した名前」ちょうど。
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkBoth(
                    acceptsUndeclaredArgs = setOf("template"),
                    values = mapOf("roleName" to "UseCase", "screenTitle" to "Home"),
                )
            }

            thrown.unknown shouldBe setOf("screenTitle")
        }

        "docs と template が宣言しているキーは、手を挙げていなくても通る" {
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

    "roleName の誤字" - {
        "誤字は roleName のほうが報告され、パラメータの名前にすり替わらない" {
            val thrown = shouldThrow<KatachiUnknownTemplateRoleException> {
                checkBoth(
                    mapOf("roleName" to "Servce", "name" to "Greeting"),
                    acceptsUndeclaredArgs = setOf("template"),
                )
            }

            thrown.roleName shouldBe "Servce"
            withClue("候補が出ないと、利用者は自分の打ち間違いに辿り着けない") {
                thrown.message.shouldNotBeNull() shouldContain "UseCase"
            }
        }
    }
})

/** Two roles with a template and one without, so "which role answered" is visible in the result. */
private fun twoTemplatesArchitecture(): Architecture = architecture {
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
 * A context over [twoTemplatesArchitecture] whose tree refuses to be read.
 *
 * [ForbiddenFileSystem] is the assertion: `undeclaredArgNames` runs before any processor does, so
 * a walk started here would make deciding "is this `--arg` a typo" cost a scan of the project.
 */
private fun contextOf(values: Map<String, String>) = FakeArchitectureProcessContext(
    architecture = twoTemplatesArchitecture(),
    args = Unit,
    fileSystem = ForbiddenFileSystem,
    rawArgs = values,
)

/** Judges [values] the way one `--processor=docs,template` run does, before anything runs. */
private fun checkBoth(
    values: Map<String, String>,
    acceptsUndeclaredArgs: Set<String> = emptySet(),
) {
    val selected: List<Pair<String, ArchitectureProcessor<*, *>>> = listOf(
        "docs" to GenerateDocumentation,
        "template" to GenerateCodeFromTemplate,
    )
    checkNoUnknownArgs(
        selected = selected,
        acceptsUndeclaredArgs = acceptsUndeclaredArgs,
        context = contextOf(values),
        values = values,
    )
}
