package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.KatachiEmptyEnumTemplateParameterException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateParameterValueException
import me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException

/** Where [architectureWithTemplate] writes `template { }`, as every message below prints it. */
private const val TEMPLATE_SITE: String = "TemplateDslSpecSupport.kt:15"

class TemplateTypedParameterSpec : FreeSpec({
    "booleanParameter" - {
        "true / false を Boolean として読める" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter()
                file("A.kt") { "$withImpl:${withImpl::class.simpleName}" }
            }

            arch.render(mapOf("withImpl" to "true")) shouldBe mapOf("A.kt" to "true:Boolean")
            arch.render(mapOf("withImpl" to "false")) shouldBe mapOf("A.kt" to "false:Boolean")
        }

        "--arg withImpl=false なら if の中の file は生成されない" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter(default = true)
                file("Repository.kt") { "" }
                if (withImpl) file("RepositoryImpl.kt") { "" }
            }

            arch.render(mapOf("withImpl" to "false")).keys shouldBe setOf("Repository.kt")
            arch.render(mapOf("withImpl" to "true")).keys shouldBe
                setOf("Repository.kt", "RepositoryImpl.kt")
        }

        "渡さなければ default が使われる" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter(default = true)
                file("A.kt") { "$withImpl" }
            }

            arch.render() shouldBe mapOf("A.kt" to "true")
        }

        "True・TRUE・yes・1 は読めずに落ち、Accepted values: true, false が文面に出る" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter(default = true)
                file("A.kt") { "$withImpl" }
            }

            for (raw in listOf("True", "TRUE", "yes", "1")) {
                val thrown = shouldThrow<KatachiInvalidTemplateParameterValueException> {
                    arch.render(mapOf("withImpl" to raw))
                }
                thrown.names shouldBe listOf("withImpl")
                thrown.message.orEmpty() shouldContain "Accepted values: true, false."
            }
            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("withImpl" to "yes"))
            }.message shouldBe """
                Template of role "UseCase" declared at $TEMPLATE_SITE was given values it cannot read: withImpl.
                  withImpl="yes": withImpl is a booleanParameter() declared at TemplateTypedParameterSpec.kt:50. Accepted values: true, false.
                Each parameter is read as the type it was declared with, and a value that does not fit is refused rather than guessed at.
                Pass a value it accepts as --arg, on the command line or in processors { args("template") { } }, e.g. --arg withImpl=true.
            """.trimIndent()
        }

        "空文字は false ではなく読めない値として落ちる" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter()
                file("A.kt") { "$withImpl" }
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("withImpl" to ""))
            }.message.orEmpty() shouldContain "withImpl=\"\":"
        }

        "末尾に空白のある値は落ち、文面で値が引用符に囲まれて空白が見える" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter()
                file("A.kt") { "$withImpl" }
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("withImpl" to "true "))
            }.message.orEmpty() shouldContain "withImpl=\"true \":"
        }

        "default があっても、読めない値は黙って default にならない" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter(default = false)
                file("A.kt") { "$withImpl" }
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("withImpl" to "no"))
            }
        }

        "default が無く渡されもしなければ Missing の文面に true or false と出る" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                val withImpl by booleanParameter()
                file("$name.kt") { "$withImpl" }
            }

            val thrown = shouldThrow<KatachiMissingTemplateParameterException> { arch.render() }
            thrown.names shouldContainExactly listOf("name", "withImpl")
            thrown.message shouldBe """
                Template of role "UseCase" declared at $TEMPLATE_SITE was run without values for: name, withImpl.
                A parameter declared with no default has to be given a value on every run.
                Pass them as --arg, on the command line or in processors { args("template") { } }: --arg name=<value> --arg withImpl=<value>.
                withImpl takes true or false.
            """.trimIndent()
        }
    }

    "intParameter" - {
        "負の数と + 付きの数を読める" {
            val arch = architectureWithTemplate {
                val pageSize by intParameter()
                file("A.kt") { "${pageSize + 1}" }
            }

            arch.render(mapOf("pageSize" to "-1")) shouldBe mapOf("A.kt" to "0")
            arch.render(mapOf("pageSize" to "+3")) shouldBe mapOf("A.kt" to "4")
        }

        "小数・16進・アンダースコア付きは読めずに落ちる" {
            val arch = architectureWithTemplate {
                val pageSize by intParameter()
                file("A.kt") { "$pageSize" }
            }

            for (raw in listOf("3.5", "0x10", "1_000", "")) {
                shouldThrow<KatachiInvalidTemplateParameterValueException> {
                    arch.render(mapOf("pageSize" to raw))
                }.message.orEmpty() shouldContain "takes a whole number, such as 0."
            }
        }

        "Int の範囲外は範囲外だと言い分けて落ちる" {
            val arch = architectureWithTemplate {
                val pageSize by intParameter(default = 20)
                file("A.kt") { "$pageSize" }
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("pageSize" to "99999999999"))
            }.message shouldBe """
                Template of role "UseCase" declared at $TEMPLATE_SITE was given values it cannot read: pageSize.
                  pageSize="99999999999": pageSize is an intParameter() declared at TemplateTypedParameterSpec.kt:148, and that number is outside -2147483648..2147483647.
                Each parameter is read as the type it was declared with, and a value that does not fit is refused rather than guessed at.
                Pass a value it accepts as --arg, on the command line or in processors { args("template") { } }, e.g. --arg pageSize=20.
            """.trimIndent()
            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("pageSize" to "2147483648"))
            }.message.orEmpty() shouldContain "outside -2147483648..2147483647"
        }

        "全角数字の桁あふれも範囲外として言い分けられる" {
            val arch = architectureWithTemplate {
                val pageSize by intParameter()
                file("A.kt") { "$pageSize" }
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("pageSize" to "９９９９９９９９９９９"))
            }.message.orEmpty() shouldContain "that number is outside"
        }

        "Int だけが読めないときも e.g. に具体的な値が出る（default があればその値）" {
            val withDefault = architectureWithTemplate {
                val pageSize by intParameter(default = 20)
                file("A.kt") { "$pageSize" }
            }
            val withoutDefault = architectureWithTemplate {
                val pageSize by intParameter()
                file("A.kt") { "$pageSize" }
            }

            val withDefaultMessage = shouldThrow<KatachiInvalidTemplateParameterValueException> {
                withDefault.render(mapOf("pageSize" to "x"))
            }.message.orEmpty()
            withDefaultMessage shouldContain "takes a whole number, such as 20."
            withDefaultMessage shouldContain "e.g. --arg pageSize=20."
            val withoutDefaultMessage = shouldThrow<KatachiInvalidTemplateParameterValueException> {
                withoutDefault.render(mapOf("pageSize" to "x"))
            }.message.orEmpty()
            withoutDefaultMessage shouldContain "takes a whole number, such as 0."
            withoutDefaultMessage shouldContain "e.g. --arg pageSize=0."
        }

        "default は上で宣言したパラメータから計算できる" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                val withImpl by booleanParameter(default = name.endsWith("Admin"))
                val pageSize by intParameter(default = name.length)
                file("A.kt") { "$withImpl $pageSize" }
            }

            arch.render(mapOf("name" to "UserAdmin")) shouldBe mapOf("A.kt" to "true 9")
        }
    }

    "enumParameter" - {
        "エントリの名前で読める" {
            val arch = architectureWithTemplate {
                val visibility by enumParameter(Visibility.entries)
                file("A.kt") { "${visibility == Visibility.Internal}" }
            }

            arch.render(mapOf("visibility" to "Internal")) shouldBe mapOf("A.kt" to "true")
        }

        "大文字小文字が違うと読めず、Accepted values にエントリ名が並ぶ" {
            val arch = architectureWithTemplate {
                val visibility by enumParameter(default = Visibility.Public)
                file("A.kt") { "$visibility" }
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("visibility" to "internal"))
            }.message shouldBe """
                Template of role "UseCase" declared at $TEMPLATE_SITE was given values it cannot read: visibility.
                  visibility="internal": visibility is an enumParameter() of Visibility declared at TemplateTypedParameterSpec.kt:222 and takes an entry name spelled exactly as declared. Accepted values: Public, Internal.
                Each parameter is read as the type it was declared with, and a value that does not fit is refused rather than guessed at.
                Pass a value it accepts as --arg, on the command line or in processors { args("template") { } }, e.g. --arg visibility=Public.
            """.trimIndent()
        }

        "知らない名前は読めずに落ちる" {
            val arch = architectureWithTemplate {
                val visibility by enumParameter(Visibility.entries)
                file("A.kt") { "$visibility" }
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("visibility" to "Private"))
            }.names shouldBe listOf("visibility")
        }

        "文字列テンプレートに埋めるとエントリの名前になる" {
            val arch = architectureWithTemplate {
                val visibility by enumParameter(Visibility.entries)
                file("A.kt") { "$visibility" }
            }

            arch.render(mapOf("visibility" to "Public")) shouldBe mapOf("A.kt" to "Public")
        }

        "default つきの形と entries の形は同じように読める" {
            val required = architectureWithTemplate {
                val visibility by enumParameter(Visibility.entries)
                file("A.kt") { visibility.name }
            }
            val withDefault = architectureWithTemplate {
                val visibility by enumParameter(default = Visibility.Public)
                file("A.kt") { visibility.name }
            }

            for (raw in listOf("Public", "Internal")) {
                required.render(mapOf("visibility" to raw)) shouldBe
                    withDefault.render(mapOf("visibility" to raw))
            }
            withDefault.render() shouldBe mapOf("A.kt" to "Public")
        }

        "エントリ本体を持つ enum でも default つきの形でエントリを取れる" {
            val arch = architectureWithTemplate {
                val shape by enumParameter(default = Shape.Circle)
                file("A.kt") { shape.name }
            }

            arch.render(mapOf("shape" to "Square")) shouldBe mapOf("A.kt" to "Square")
            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("shape" to "Triangle"))
            }.message.orEmpty() shouldContain "enumParameter() of Shape declared at"
        }

        "エントリの無い enum は KatachiEmptyEnumTemplateParameterException で落ちる" {
            val arch = architectureWithTemplate {
                @Suppress("UNUSED_VARIABLE")
                val nothing by enumParameter(Nothingness.entries)
                file("A.kt") { "" }
            }

            val thrown = shouldThrow<KatachiEmptyEnumTemplateParameterException> { arch.render() }
            thrown.role shouldBe TEMPLATE_ROLE
            thrown.message shouldBe """
                enumParameter() of role "UseCase" declared at TemplateTypedParameterSpec.kt:288 was given an enum with no entries.
                No --arg value could ever be read as it, so every run of this template would fail.
                Give the enum at least one entry, or declare the parameter with stringParameter().
            """.trimIndent()
        }
    }
})
