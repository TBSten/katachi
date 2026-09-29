package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateException
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateIdException
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateParameterException
import me.tbsten.katachi.dsl.KatachiEmptyEnumTemplateParameterException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateParameterValueException
import me.tbsten.katachi.dsl.KatachiMissingTemplateIdException
import me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException
import me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException
import me.tbsten.katachi.dsl.KatachiTemplateParameterReusedException
import me.tbsten.katachi.dsl.KatachiUnboundTemplateParameterException
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

private fun Architecture.only() = flattenLayout().mapNotNull { it[Template] }.single()

private fun Architecture.run(role: String, values: Map<String, String>, captures: Set<String> = emptySet()) =
    evaluateTemplate(only(), role, values, captureNames = captures)

/**
 * The `.template { }` examples in the KDoc of `TemplateScope`, `TemplateParameter`, `LayoutFile.template`
 * and the template exceptions, copied here to check they do what the KDoc says.
 */
class TemplateKdocExamplesSpec : FreeSpec({
    "TemplateScope" - {
        "Example 1 は名前付き capture で宣言でき、既定値つきの引数を書く" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "${capture("name")}UseCase.kt".file()
                                .template {
                                    val name = captureValue("name")
                                    val implBody by stringParameter(default = """TODO("not implemented")""")
                                    """
                                    interface ${name}UseCase {
                                        suspend operator fun invoke()
                                    }

                                    class ${name}UseCaseImpl : ${name}UseCase {
                                        override suspend fun invoke() {
                                            $implBody
                                        }
                                    }
                                    """.trimIndent()
                                }
                        }
                    }
                }
            }
            arch.run("UseCase", mapOf("name" to "GetUser"), setOf("name")) shouldBe
                "interface GetUserUseCase {\n    suspend operator fun invoke()\n}\n\n" +
                "class GetUserUseCaseImpl : GetUserUseCase {\n    override suspend fun invoke() {\n" +
                "        TODO(\"not implemented\")\n    }\n}"
        }

        "stringParameter の例" {
            val arch = architecture {
                "UseCase" {
                    layout {
                        "useCase" / "${capture("name")}UseCase.kt".file()
                            .template {
                                val implBody by stringParameter(default = """TODO("not implemented")""")
                                val comment by stringParameter()
                                "// $comment: ${captureValue("name")}UseCase { $implBody }"
                            }
                    }
                }
            }
            arch.run("UseCase", mapOf("name" to "GetUser", "comment" to "hello"), setOf("name")) shouldBe
                "// hello: GetUserUseCase { TODO(\"not implemented\") }"
        }

        "booleanParameter・intParameter・enumParameter の例" {
            val bool = architecture {
                "Repository" {
                    layout {
                        "repository" / "${capture("name")}Repository.kt".file()
                            .template {
                                val name = captureValue("name")
                                val suspending by booleanParameter(default = true)
                                val modifier = if (suspending) "suspend " else ""
                                "interface ${name}Repository { ${modifier}fun all(): List<$name> }"
                            }
                    }
                }
            }
            bool.run("Repository", mapOf("name" to "User", "suspending" to "false"), setOf("name")) shouldBe
                "interface UserRepository { fun all(): List<User> }"

            val int = architecture {
                "Pager" {
                    layout {
                        "pager" / "${capture("name")}Pager.kt".file()
                            .template {
                                val name = captureValue("name")
                                val pageSize by intParameter(default = 20)
                                "const val ${name}_PAGE_SIZE: Int = $pageSize"
                            }
                    }
                }
            }
            int.run("Pager", mapOf("name" to "User", "pageSize" to "50"), setOf("name")) shouldBe
                "const val User_PAGE_SIZE: Int = 50"

            val entries = architecture {
                "Any" {
                    layout {
                        "any" / "${capture("name")}.kt".file()
                            .template {
                                val name = captureValue("name")
                                val visibility by enumParameter(Visibility.entries)
                                "${visibility.name.lowercase()} class $name"
                            }
                    }
                }
            }
            entries.run("Any", mapOf("name" to "User", "visibility" to "Internal"), setOf("name")) shouldBe
                "internal class User"

            val default = architecture {
                "Any" {
                    layout {
                        "any" / "${capture("name")}.kt".file()
                            .template {
                                val name = captureValue("name")
                                val visibility by enumParameter(default = Visibility.Public)
                                "${visibility.name.lowercase()} class $name"
                            }
                    }
                }
            }
            default.run("Any", mapOf("name" to "User"), setOf("name")) shouldBe "public class User"
        }

        "captureValue の例（module の capture と file の capture）" {
            val arch = architecture {
                "Screen" {
                    layout {
                        "feature" / capture("feature") / "${capture("name")}Screen.kt".file()
                            .template {
                                val name = captureValue("name")
                                val feature = captureValue("feature")
                                "package com.example.feature.$feature\n\nfun ${name}Screen() {}"
                            }
                    }
                }
            }
            arch.run("Screen", mapOf("feature" to "home", "name" to "Home"), setOf("feature", "name")) shouldBe
                "package com.example.feature.home\n\nfun HomeScreen() {}"
        }

        "isPreview の例" {
            val arch = architecture {
                "Any" {
                    layout {
                        "any" / "${capture("resource")}.kt".file()
                            .template {
                                val resource = captureValue("resource")
                                require(isPreview || resource.all { it.isLetterOrDigit() }) {
                                    "resource must be alphanumeric, was $resource"
                                }
                                "// $resource"
                            }
                    }
                }
            }
            val template = arch.only()
            evaluateTemplate(template, "Any", mapOf("resource" to "\${resource}"), captureNames = setOf("resource"), isPreview = true) shouldBe
                "// \${resource}"
            shouldThrow<IllegalArgumentException> {
                evaluateTemplate(template, "Any", mapOf("resource" to "a-b"), captureNames = setOf("resource"))
            }
        }
    }

    "LayoutFile.template の例" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    layout {
                        "useCase" / "${capture("name")}UseCase.kt".file()
                            .template {
                                val name = captureValue("name")
                                "interface ${name}UseCase"
                            }
                    }
                }
            }
        }
        arch.run("UseCase", mapOf("name" to "GetUser"), setOf("name")) shouldBe "interface GetUserUseCase"
    }

    "TemplateParameter の例" - {
        "宣言に使う引数だけを持つ template が動く" {
            val arch = architecture {
                "UseCase" {
                    layout {
                        "useCase" / "${capture("name")}UseCase.kt".file()
                            .template {
                                val name = captureValue("name")
                                val implBody by stringParameter(default = """TODO("not implemented")""")
                                "// ${name}UseCase: $implBody"
                            }
                    }
                }
            }
            arch.run("UseCase", mapOf("name" to "A"), setOf("name")) shouldBe "// AUseCase: TODO(\"not implemented\")"
        }

        "provideDelegate の例: 読まない引数も受け付ける名前になる" {
            val arch = architecture {
                "UseCase" {
                    layout {
                        "useCase" / "${capture("name")}UseCase.kt".file()
                            .template {
                                @Suppress("UNUSED_VARIABLE")
                                val packageName by stringParameter(default = "com.example")
                                val name = captureValue("name")

                                "interface ${name}UseCase"
                            }
                    }
                }
            }
            arch.run("UseCase", mapOf("name" to "A"), setOf("name")) shouldBe "interface AUseCase"
        }
    }

    "例外の例" - {
        "KatachiUnboundTemplateParameterException" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "UseCase.kt".file()
                                .template {
                                    val name = stringParameter()
                                    "interface ${name}UseCase"
                                }
                        }
                    }
                }
            }
            arch.allRoles.single().name shouldBe "UseCase"
            shouldThrow<KatachiUnboundTemplateParameterException> { arch.run("UseCase", emptyMap()) }
        }

        "KatachiDuplicateTemplateParameterException" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "UseCase.kt".file()
                                .template {
                                    val name by stringParameter()
                                    run { val name by stringParameter(default = "other") }
                                    "interface ${name}UseCase"
                                }
                        }
                    }
                }
            }
            shouldThrow<KatachiDuplicateTemplateParameterException> { arch.run("UseCase", mapOf("name" to "A")) }
        }

        "KatachiTemplateParameterReusedException" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "UseCase.kt".file()
                                .template {
                                    val shared = stringParameter()
                                    val name by shared
                                    val other by shared
                                    "interface ${name}${other}UseCase"
                                }
                        }
                    }
                }
            }
            shouldThrow<KatachiTemplateParameterReusedException> { arch.run("UseCase", mapOf("name" to "A")) }
        }

        "KatachiMissingTemplateParameterException" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "UseCase.kt".file()
                                .template {
                                    val name by stringParameter()
                                    "interface ${name}UseCase"
                                }
                        }
                    }
                }
            }
            shouldThrow<KatachiMissingTemplateParameterException> { arch.run("UseCase", emptyMap()) }.names shouldBe
                listOf("name")
        }

        "KatachiInvalidTemplateParameterValueException" {
            val arch = architecture {
                "data".group {
                    "Repository" {
                        layout {
                            "repository" / "Repository.kt".file()
                                .template {
                                    val name by stringParameter()
                                    val withImpl by booleanParameter(default = true)
                                    "interface ${name}Repository // withImpl=$withImpl"
                                }
                        }
                    }
                }
            }
            shouldThrow<KatachiInvalidTemplateParameterValueException> {
                arch.run("Repository", mapOf("name" to "User", "withImpl" to "yes"))
            }.names shouldBe listOf("withImpl")
        }

        "KatachiEmptyEnumTemplateParameterException の例は空でない enum なので投げない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "GetUserUseCase.kt".file()
                                .template {
                                    val visibility by enumParameter(Visibility.entries)
                                    "${visibility.name.lowercase()} class GetUserUseCase"
                                }
                        }
                    }
                }
            }
            arch.allRoles.single().name shouldBe "UseCase"
            arch.run("UseCase", mapOf("visibility" to "Public")) shouldBe "public class GetUserUseCase"
            KatachiEmptyEnumTemplateParameterException::class.simpleName shouldBe
                "KatachiEmptyEnumTemplateParameterException"
        }

        "KatachiDuplicateTemplateException" {
            val thrown = shouldThrow<KatachiDuplicateTemplateException> {
                architecture {
                    "domain".group {
                        "UseCase" {
                            layout {
                                "useCase" / "${capture("name")}UseCase.kt".file()
                                    .template { "a" }
                                    .template { "b" }
                            }
                        }
                    }
                }.flattenLayout()
            }
            thrown.role shouldBe "domain.UseCase"
        }

        "KatachiDuplicateTemplateIdException" {
            val thrown = shouldThrow<KatachiDuplicateTemplateIdException> {
                architecture {
                    "data".group {
                        "Repository" {
                            layout {
                                "repository" / "${capture("name")}Repository.kt".file()
                                    .template(id = "repository") { "" }
                                "repository" / "${capture("name")}RepositoryImpl.kt".file()
                                    .template(id = "repository") { "" }
                            }
                        }
                    }
                }.flattenLayout()
            }
            thrown.id shouldBe "repository"
        }

        "KatachiMissingTemplateIdException" {
            val thrown = shouldThrow<KatachiMissingTemplateIdException> {
                architecture {
                    "data".group {
                        "Repository" {
                            layout {
                                "repository" / "${capture("name")}Repository.kt".file()
                                    .template { "" }
                                "repository" / "${capture("name")}RepositoryImpl.kt".file()
                                    .template(id = "impl") { "" }
                            }
                        }
                    }
                }.flattenLayout()
            }
            thrown.role shouldBe "data.Repository"
        }

        "KatachiTemplateOnWildcardException" {
            val thrown = shouldThrow<KatachiTemplateOnWildcardException> {
                architecture {
                    "data".group {
                        "Repository" {
                            layout {
                                "repository" / "*Repository.kt".file()
                                    .template { "" }
                            }
                        }
                    }
                }.flattenLayout()
            }
            thrown.role shouldBe "data.Repository"
        }
    }
})
