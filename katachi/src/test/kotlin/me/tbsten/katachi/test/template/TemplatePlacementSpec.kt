package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.template.KatachiAmbiguousTemplatePlacementException
import me.tbsten.katachi.template.KatachiAmbiguousTemplateRoleException
import me.tbsten.katachi.template.KatachiNoTemplateException
import me.tbsten.katachi.template.KatachiNoTemplatePlacementException
import me.tbsten.katachi.template.KatachiTemplatePathOutsideProjectException
import me.tbsten.katachi.template.KatachiUnknownTemplateRoleException
import me.tbsten.katachi.template.KatachiUnsafeTemplateFileNameException
import me.tbsten.katachi.template.KatachiWildcardTemplatePlacementException

/**
 * Where a generated file lands, decided from the declarations alone.
 *
 * Writing it is [TemplateOutputSpec]'s.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.template` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class TemplatePlacementSpec : FreeSpec({
    "ディレクトリは layout から導く" - {
        "宣言されたパターンに一致するファイル名が、そのディレクトリへ落ちる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" / "*UseCase.kt".file() }
                        template {
                            val name by stringParameter()
                            file("${name}UseCase.kt") { "interface ${name}UseCase" }
                        }
                    }
                }
            }

            arch.generated("UseCase", mapOf("name" to "GetUser")) shouldBe
                mapOf("useCase/GetUserUseCase.kt" to "interface GetUserUseCase\n")
        }

        "本文は改行で終わる" {
            val arch = architecture {
                "UseCase" {
                    layout { "useCase" / "*UseCase.kt".file() }
                    template {
                        // `""".trimIndent()` stops at the last character the author typed, which
                        // is what a block like this one usually ends with.
                        file("GetUserUseCase.kt") { "interface GetUserUseCase" }
                    }
                }
            }

            withClue("最終行に改行が無いファイルは、利用者の formatter が最初に指摘する") {
                arch.generated("UseCase").values.single() shouldBe "interface GetUserUseCase\n"
            }
        }

        "すでに改行で終わっていれば足さない" {
            val arch = architecture {
                "UseCase" {
                    layout { "useCase" / "*UseCase.kt".file() }
                    template {
                        file("GetUserUseCase.kt") { "interface GetUserUseCase\n" }
                    }
                }
            }

            arch.generated("UseCase").values.single() shouldBe "interface GetUserUseCase\n"
        }

        "1つの template が複数のファイルを別々のディレクトリへ置ける" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "api" / "*UseCase.kt".file() }
                        layout { "impl" / "*UseCaseImpl.kt".file() }
                        template {
                            file("GetUserUseCase.kt") { "api" }
                            file("GetUserUseCaseImpl.kt") { "impl" }
                        }
                    }
                }
            }

            arch.generatedPaths("UseCase") shouldContainExactly listOf(
                "api/GetUserUseCase.kt",
                "impl/GetUserUseCaseImpl.kt",
            )
        }

        "宣言の順にファイルが並ぶ" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" / "*.kt".file() }
                        template {
                            file("B.kt") { "b" }
                            file("A.kt") { "a" }
                        }
                    }
                }
            }

            arch.generated("UseCase").keys.toList() shouldContainExactly
                listOf("useCase/B.kt", "useCase/A.kt")
        }

        "同じディレクトリを2つのパターンが受けても曖昧にはならない" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout {
                            "useCase" / "*.kt".file()
                            "useCase" / "*UseCase.kt".file()
                        }
                        template { file("GetUserUseCase.kt") { "" } }
                    }
                }
            }

            arch.generatedPaths("UseCase") shouldContainExactly listOf("useCase/GetUserUseCase.kt")
        }

        "プロジェクトルート直下のパターンにも落ちる" {
            val arch = architecture {
                "Doc" {
                    layout { "*.md".file() }
                    template { file("NOTES.md") { "notes" } }
                }
            }

            arch.generatedPaths("Doc") shouldContainExactly listOf("NOTES.md")
        }
    }

    "置き場所が決まらないものは例外" - {
        "どのパターンにも一致しないファイル名で落ちる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" / "*UseCase.kt".file() }
                        template { file("GetUser.kt") { "" } }
                    }
                }
            }

            val thrown = shouldThrow<KatachiNoTemplatePlacementException> { arch.generated("UseCase") }
            thrown.fileName shouldBe "GetUser.kt"
            thrown.declaredPatterns shouldContainExactly listOf("useCase/*UseCase.kt")
            thrown.message.orEmpty() shouldContain "has no place in role"
        }

        "候補が2つ以上あると落ちる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "api" / "*UseCase.kt".file() }
                        layout { "impl" / "*UseCase.kt".file() }
                        template { file("GetUserUseCase.kt") { "" } }
                    }
                }
            }

            val thrown =
                shouldThrow<KatachiAmbiguousTemplatePlacementException> { arch.generated("UseCase") }
            thrown.candidates shouldContainExactly
                listOf("api/GetUserUseCase.kt", "impl/GetUserUseCase.kt")
        }

        "ディレクトリ側にワイルドカードが残っていると落ちる" {
            val arch = architecture {
                "feature".group {
                    "Screen" {
                        layout { "feature" / "*" / "*Screen.kt".file() }
                        template { file("HomeScreen.kt") { "" } }
                    }
                }
            }

            val thrown =
                shouldThrow<KatachiWildcardTemplatePlacementException> { arch.generated("Screen") }
            thrown.patterns shouldContainExactly listOf("feature/*/*Screen.kt")
            thrown.message.orEmpty() shouldContain "no single directory"
        }

        "ワイルドカードのモジュールキーでも落ちる" {
            val arch = architecture {
                "feature".group {
                    "Screen" {
                        layout { ":feature:*".module { "*Screen.kt".file() } }
                        template { file("HomeScreen.kt") { "" } }
                    }
                }
            }

            val thrown =
                shouldThrow<KatachiWildcardTemplatePlacementException> { arch.generated("Screen") }
            thrown.patterns.single() shouldContain "*"
        }

        "ディレクトリの宣言だけでは置き場所にならない" {
            val arch = architecture {
                "build".group {
                    "Generated" {
                        layout { "generated" { anyFile() } }
                        template { file("Thing.kt") { "" } }
                    }
                }
            }

            shouldThrow<KatachiNoTemplatePlacementException> { arch.generated("Generated") }
        }

        "プロジェクトルートの外へ出るパスは落ちる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { ".." / "*UseCase.kt".file() }
                        template { file("GetUserUseCase.kt") { "" } }
                    }
                }
            }

            val thrown =
                shouldThrow<KatachiTemplatePathOutsideProjectException> { arch.generated("UseCase") }
            thrown.path shouldBe "../GetUserUseCase.kt"
        }
    }

    "作れないファイル名は落ちる" - {
        "glob のメタ文字を含む名前で落ちる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" / "*UseCase.kt".file() }
                        template {
                            val name by stringParameter()
                            file("${name}UseCase.kt") { "" }
                        }
                    }
                }
            }

            val thrown = shouldThrow<KatachiUnsafeTemplateFileNameException> {
                arch.generated("UseCase", mapOf("name" to "Get*"))
            }
            thrown.characters shouldContainExactly listOf("*")
        }

        "Windows で使えない文字を含む名前で落ちる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" / "*UseCase.kt".file() }
                        template {
                            val name by stringParameter()
                            file("${name}UseCase.kt") { "" }
                        }
                    }
                }
            }

            val thrown = shouldThrow<KatachiUnsafeTemplateFileNameException> {
                arch.generated("UseCase", mapOf("name" to "Get:User"))
            }
            thrown.characters shouldContainExactly listOf(":")
        }
    }

    "役割の選び方" - {
        "修飾名でも素の名前でも同じ役割に届く" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "useCase" / "*.kt".file() }
                        template { file("A.kt") { "a" } }
                    }
                }
            }

            arch.generatedPaths("UseCase") shouldBe arch.generatedPaths("domain/UseCase")
        }

        "素の名前が2つの group で衝突していると落ちる" {
            val arch = architecture {
                "domain".group {
                    "UseCase" {
                        layout { "domain" / "*.kt".file() }
                        template { file("A.kt") { "" } }
                    }
                }
                "feature".group {
                    "UseCase" {
                        layout { "feature" / "*.kt".file() }
                        template { file("A.kt") { "" } }
                    }
                }
            }

            val thrown = shouldThrow<KatachiAmbiguousTemplateRoleException> { arch.generated("UseCase") }
            thrown.candidates shouldContainExactly listOf("domain/UseCase", "feature/UseCase")
        }

        "知らない役割名は宣言済みの一覧つきで落ちる" {
            val arch = architecture { "domain".group { "UseCase" { } } }

            val thrown = shouldThrow<KatachiUnknownTemplateRoleException> { arch.generated("UseCse") }
            thrown.declaredRoles shouldContainExactly listOf("domain/UseCase")
        }

        "template を持たない役割は落ちる" {
            val arch = architecture {
                "domain".group { "UseCase" { layout { "useCase" / "*.kt".file() } } }
            }

            shouldThrow<KatachiNoTemplateException> { arch.generated("UseCase") }.role shouldBe
                "domain/UseCase"
        }
    }
})
