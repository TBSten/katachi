package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateParameterException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateParameterValueException
import me.tbsten.katachi.dsl.KatachiTemplateParameterReusedException
import me.tbsten.katachi.dsl.KatachiUnboundTemplateParameterException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.template

/** Where [architectureWithTemplate] writes `.template { }`, as every message below prints it. */
private const val TEMPLATE_SITE: String = "TemplateDslSpecSupport.kt:20"

/**
 * When a typed parameter's value is judged, where it says it was declared, and how the checks
 * every parameter shares treat the typed ones. How each type reads a value is
 * [TemplateTypedParameterSpec]'s.
 */
class TemplateTypedParameterCheckSpec : FreeSpec({
    "宣言位置" - {
        "booleanParameter・intParameter・enumParameter の宣言位置の行番号が、書いた行を指す" {
            val sites = mutableListOf<DeclarationSite>()
            val arch = architectureWithTemplate {
                sites += booleanParameter().declaredAt
                sites += intParameter().declaredAt
                sites += enumParameter(Visibility.entries).declaredAt
                ""
            }

            arch.namesReplay()
            sites shouldBe listOf(
                DeclarationSite("TemplateTypedParameterCheckSpec.kt", 35),
                DeclarationSite("TemplateTypedParameterCheckSpec.kt", 36),
                DeclarationSite("TemplateTypedParameterCheckSpec.kt", 37),
            )
        }

        "enumParameter(default = ...) の宣言位置の行番号がファイルの末尾より後ろにならない" {
            var site: DeclarationSite? = null
            val arch = architectureWithTemplate {
                site = enumParameter(default = Visibility.Public).declaredAt
                ""
            }

            arch.namesReplay()
            site shouldBe DeclarationSite("TemplateTypedParameterCheckSpec.kt", 52)
            site?.lineNumber?.shouldBeLessThanOrEqual(LINES_OF_THIS_FILE)
        }
    }

    "値を検査する時機" - {
        "一度も読まれないパラメータに渡した誤った値も落ちる" {
            val arch = architectureWithTemplate {
                @Suppress("UNUSED_VARIABLE")
                val withImpl by booleanParameter(default = true)
                ""
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("withImpl" to "yes"))
            }.names shouldBe listOf("withImpl")
        }

        "読めない値が 2 つあれば 1 回の例外に 2 つとも宣言順で出る" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter()
                val pageSize by intParameter()
                "$withImpl $pageSize"
            }

            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("pageSize" to "x", "withImpl" to "yes"))
            }.names shouldBe listOf("withImpl", "pageSize")
        }

        "読めない値と足りない値が両方あれば、読めない値の例外の missing に足りない名前が入る" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                val withImpl by booleanParameter(default = true)
                val pageSize by intParameter()
                val visibility by enumParameter(Visibility.entries)
                "$name $withImpl $pageSize $visibility"
            }

            val thrown = shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("withImpl" to "yes", "pageSize" to "3.5"))
            }
            thrown.names shouldBe listOf("withImpl", "pageSize")
            thrown.missing shouldBe listOf("name", "visibility")
            thrown.message shouldBe """
                Template of role "UseCase" declared at $TEMPLATE_SITE was given values it cannot read: withImpl, pageSize.
                  withImpl="yes": withImpl is a booleanParameter() declared at TemplateTypedParameterCheckSpec.kt:90. Accepted values: true, false.
                  pageSize="3.5": pageSize is an intParameter() declared at TemplateTypedParameterCheckSpec.kt:91 and takes a whole number, such as 0.
                Each parameter is read as the type it was declared with, and a value that does not fit is refused rather than guessed at.
                Pass a value it accepts as --arg, on the command line or in processors { args("template") { } }, e.g. --arg withImpl=true.
                The run was also short of values for: name, visibility. Pass --arg name=<value> --arg visibility=<value>. visibility takes Public or Internal.
            """.trimIndent()
        }

        "利用者のコードが投げた失敗は cause に残る" {
            val arch = architectureWithTemplate {
                val pageSize by intParameter()
                if (pageSize == 0) throw IllegalStateException("page size of zero") else ""
            }

            val thrown = shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.render(mapOf("pageSize" to "zero"))
            }
            thrown.cause.shouldBeInstanceOf<IllegalStateException>().message shouldBe
                "page size of zero"
        }

        "Names の replay は読めない値があっても投げず、後ろのパラメータまで名前を集め終える" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter()
                val pageSize by intParameter(default = 20)
                "$withImpl $pageSize"
            }

            val names = shouldNotThrowAny { arch.namesReplay(mapOf("withImpl" to "yes")) }
            names.declared shouldBe setOf("withImpl", "pageSize")
            names.isUnreliable shouldBe true
        }

        "Names の replay は読めない値があると信用できないと答える" {
            val arch = architectureWithTemplate {
                val withImpl by booleanParameter(default = true)
                if (withImpl) {
                    @Suppress("UNUSED_VARIABLE")
                    val implName by stringParameter()
                }
                ""
            }

            arch.namesReplay(mapOf("withImpl" to "yes")).isUnreliable shouldBe true
            arch.namesReplay(mapOf("withImpl" to "true")).isUnreliable shouldBe false
            arch.namesReplay(mapOf("withImpl" to "true")).declared shouldBe setOf("withImpl", "implName")
        }

        "Names の replay は String の必須パラメータが足りないだけなら信用できると答える" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                "${name}UseCase"
            }

            val names = arch.namesReplay()
            names.isUnreliable shouldBe false
            names.declared shouldBe setOf("name")
        }
    }

    "既存の例外は型を問わず働く" - {
        "by を書かなかった booleanParameter は未束縛として落ち、文面に booleanParameter() と出る" {
            val arch = architectureWithTemplate {
                val withImpl = booleanParameter()
                "$withImpl"
            }

            val message = shouldThrow<KatachiUnboundTemplateParameterException> { arch.render() }
                .message.orEmpty()
            message shouldContain "Write it as `val name by booleanParameter()`."
            message shouldNotContain "stringParameter()"
        }

        "1 つの intParameter を 2 つのプロパティで使うと、文面に intParameter() と出る" {
            val arch = architectureWithTemplate {
                val shared = intParameter(default = 1)
                val first by shared
                val second by shared
                "$first$second"
            }

            shouldThrow<KatachiTemplateParameterReusedException> { arch.render() }
                .message.orEmpty() shouldContain "Call intParameter() once per parameter."
        }

        "同じ名前を string と boolean で宣言すると重複として落ちる" {
            val arch = architectureWithTemplate {
                val name by stringParameter()
                run {
                    @Suppress("UNUSED_VARIABLE")
                    val name by booleanParameter(default = true)
                }
                name
            }

            shouldThrow<KatachiDuplicateTemplateParameterException> { arch.render() }
                .name shouldBe "name"
        }
    }

    "型を組み合わせる" - {
        "TemplateScope の KDoc の Example 2 がそのとおりに動く（id で選んだ側だけを評価する）" {
            val arch = architecture {
                "data".group {
                    "Repository" {
                        layout {
                            "repository" / "${capture("name")}Repository.kt".file().template(id = "repository") {
                                val name = captureValue("name")
                                val pageSize by intParameter(default = 20)
                                val visibility by enumParameter(default = Visibility.Public)
                                "${visibility.name.lowercase()} interface ${name}Repository { val pageSize: Int get() = $pageSize }"
                            }
                            "repository" / "${capture("name")}RepositoryImpl.kt".file().template(id = "repositoryImpl") {
                                val name = captureValue("name")
                                val visibility by enumParameter(default = Visibility.Public)
                                "${visibility.name.lowercase()} class ${name}RepositoryImpl : ${name}Repository"
                            }
                        }
                    }
                }
            }
            arch.allRoles.single().name shouldBe "Repository"

            val templates = arch.flattenLayout().mapNotNull { it[Template] }.distinct()
            templates.map { it.id } shouldBe listOf("repository", "repositoryImpl")

            val repository = templates.single { it.id == "repository" }
            val content = evaluateTemplate(
                repository,
                "Repository",
                mapOf("name" to "User", "pageSize" to "50", "visibility" to "Internal"),
                captureNames = setOf("name"),
            )
            content shouldBe "internal interface UserRepository { val pageSize: Int get() = 50 }"
        }
    }
})

/** How many lines this file has, for the site that must not point past its end. */
private const val LINES_OF_THIS_FILE: Int = 234
