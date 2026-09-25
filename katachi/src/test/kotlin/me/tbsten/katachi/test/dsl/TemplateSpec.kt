package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateException
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateFileException
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateParameterException
import me.tbsten.katachi.dsl.KatachiEmptyTemplateException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateFileNameException
import me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException
import me.tbsten.katachi.dsl.KatachiTemplateParameterReusedException
import me.tbsten.katachi.dsl.KatachiUnboundTemplateParameterException
import me.tbsten.katachi.dsl.TemplateScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.templateParameterNames

/** The role every template below is declared on, so the messages have a name to print. */
private const val ROLE: String = "UseCase"

private fun architectureWithTemplate(block: TemplateScope.() -> Unit): Architecture =
    architecture {
        "domain".group { ROLE { template(block) } }
    }

/** The rendered files of one template, as `file name -> content`. */
private fun Architecture.render(values: Map<String, String> = emptyMap()): Map<String, String> =
    evaluateTemplate(allRoles.single().templates.single(), ROLE, values)
        .files
        .associate { it.fileName to it.content }

private fun Architecture.parameterNames(): Set<String> =
    templateParameterNames(allRoles.single().templates.single(), ROLE)

class TemplateSpec : FreeSpec({
    "宣言を溜める" - {
        "template { } は評価されずに役割へ溜まる" {
            var evaluated = false
            val arch = architectureWithTemplate { evaluated = true }

            evaluated shouldBe false
            arch.allRoles.single().templates.size shouldBe 1
        }

        "宣言した順にファイルが並ぶ" {
            val arch = architectureWithTemplate {
                file("B.kt") { "b" }
                file("A.kt") { "a" }
            }

            arch.render().keys.toList() shouldContainExactly listOf("B.kt", "A.kt")
        }

        "ファイル名とパラメータが Kotlin の文字列テンプレートで組める" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                file("${name}UseCase.kt") { "interface ${name}UseCase" }
            }

            arch.render(mapOf("name" to "GetUser")) shouldBe
                mapOf("GetUserUseCase.kt" to "interface GetUserUseCase")
        }

        "役割に2つ目の template を書くと宣言時に落ちる" {
            val thrown = shouldThrow<KatachiDuplicateTemplateException> {
                architecture {
                    "domain".group {
                        ROLE {
                            template { file("A.kt") { "" } }
                            template { file("B.kt") { "" } }
                        }
                    }
                }
            }

            thrown.role shouldBe ROLE
            thrown.message.orEmpty() shouldContain "declares a second template"
        }
    }

    "パラメータの名前はプロパティ名から採る" - {
        "読まれないパラメータも名前として数えられる" {
            val arch = architectureWithTemplate {
                @Suppress("UNUSED_VARIABLE")
                val packageName by stringParameter()
                val name by stringParameter()
                file("${name}UseCase.kt") { "" }
            }

            arch.parameterNames() shouldBe setOf("packageName", "name")
        }

        "名前を数えるだけの replay では file { } の中身を評価しない" {
            var rendered = false
            val arch = architectureWithTemplate {
                val name by stringParameter()
                file("${name}UseCase.kt") {
                    rendered = true
                    ""
                }
            }

            arch.parameterNames() shouldBe setOf("name")
            rendered shouldBe false
        }

        "同じ名前を2回宣言すると落ちる" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                run {
                    @Suppress("UNUSED_VARIABLE")
                    val name by stringParameter(default = "other")
                }
                file("${name}UseCase.kt") { "" }
            }

            shouldThrow<KatachiDuplicateTemplateParameterException> { arch.render() }
                .name shouldBe "name"
        }

        "1つのパラメータを2つのプロパティに繋ぐと落ちる" {
            val arch = architectureWithTemplate {
                val shared = stringParameter()
                val name by shared
                val other by shared
                file("$name$other.kt") { "" }
            }

            val thrown = shouldThrow<KatachiTemplateParameterReusedException> { arch.render() }
            thrown.firstName shouldBe "name"
            thrown.secondName shouldBe "other"
        }
    }

    "値の束縛とデフォルト" - {
        "--arg の値が優先される" {
            val arch = architectureWithTemplate {
                val implBody by stringParameter(default = """TODO("not implemented")""")
                file("A.kt") { implBody }
            }

            arch.render(mapOf("implBody" to """println("hi")""")) shouldBe
                mapOf("A.kt" to """println("hi")""")
        }

        "値が無ければデフォルトが使われる" {
            val arch = architectureWithTemplate {
                val implBody by stringParameter(default = """TODO("not implemented")""")
                file("A.kt") { implBody }
            }

            arch.render() shouldBe mapOf("A.kt" to """TODO("not implemented")""")
        }

        "デフォルトは先に宣言したパラメータを読める" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                val implBody by stringParameter(default = """TODO("${name}UseCaseImpl")""")
                file("A.kt") { implBody }
            }

            arch.render(mapOf("name" to "GetUser")) shouldBe
                mapOf("A.kt" to """TODO("GetUserUseCaseImpl")""")
        }
    }

    "前方参照 -- by を書かなかったパラメータ" - {
        "名前を持たないパラメータがあると落ちる" {
            val arch = architectureWithTemplate {
                val name = stringParameter()
                file("${name}UseCase.kt") { "" }
            }

            val thrown = shouldThrow<KatachiUnboundTemplateParameterException> { arch.render() }
            thrown.role shouldBe ROLE
            thrown.parameterSites.size shouldBe 1
            thrown.message.orEmpty() shouldContain "val name by stringParameter()"
        }

        "名前を持たないパラメータは --arg で埋められないので、欠けた値より先に報告される" {
            val arch = architectureWithTemplate {
                val named by stringParameter()
                val unnamed = stringParameter()
                file("$named$unnamed.kt") { "" }
            }

            shouldThrow<KatachiUnboundTemplateParameterException> { arch.render() }
        }
    }

    "欠けたパラメータはまとめて報告される" - {
        "2つ欠けていれば1回の例外に2つとも出る" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                val packageName by stringParameter()
                file("${name}UseCase.kt") { "package $packageName" }
            }

            val thrown = shouldThrow<KatachiMissingTemplateParameterException> { arch.render() }
            thrown.names shouldContainExactly listOf("name", "packageName")
            thrown.message.orEmpty() shouldContain "--arg name=<value> --arg packageName=<value>"
        }

        "file { } の中でしか読まれないパラメータも拾われる" {
            val arch = architectureWithTemplate {
                val packageName by stringParameter()
                file("A.kt") { "package $packageName" }
            }

            shouldThrow<KatachiMissingTemplateParameterException> { arch.render() }
                .names shouldContainExactly listOf("packageName")
        }

        "デフォルトのあるパラメータは欠けていない" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                val implBody by stringParameter(default = "TODO()")
                file("${name}UseCase.kt") { implBody }
            }

            shouldThrow<KatachiMissingTemplateParameterException> { arch.render() }
                .names shouldContainExactly listOf("name")
        }
    }

    "file() はファイル名であってパスではない" - {
        "区切りを含む名前は落ちる" {
            val arch = architectureWithTemplate { file("useCase/GetUserUseCase.kt") { "" } }

            shouldThrow<KatachiInvalidTemplateFileNameException> { arch.render() }
                .fileName shouldBe "useCase/GetUserUseCase.kt"
        }

        "上の階層へ出る名前は落ちる" {
            val arch = architectureWithTemplate { file("..") { "" } }

            shouldThrow<KatachiInvalidTemplateFileNameException> { arch.render() }
        }

        "空の名前は落ちる" {
            val arch = architectureWithTemplate { file("  ") { "" } }

            shouldThrow<KatachiInvalidTemplateFileNameException> { arch.render() }
        }

        "同じ名前を2回宣言すると落ちる" {
            val arch = architectureWithTemplate {
                file("A.kt") { "first" }
                file("A.kt") { "second" }
            }

            shouldThrow<KatachiDuplicateTemplateFileException> { arch.render() }
                .fileName shouldBe "A.kt"
        }
    }

    "1つもファイルを作らない template は落ちる" {
        val arch = architectureWithTemplate { }

        shouldThrow<KatachiEmptyTemplateException> { arch.render() }.role shouldBe ROLE
    }
})
