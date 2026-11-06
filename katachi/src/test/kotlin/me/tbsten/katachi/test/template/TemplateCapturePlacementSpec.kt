package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException
import me.tbsten.katachi.dsl.flatCase
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf

/**
 * A run's values missing a directory-level (non-module) capture the chosen template needs.
 */
class TemplateCapturePlacementSpec : FreeSpec({
    "値が無い capture は KatachiMissingTemplateCaptureException" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel".role {
                    layout {
                        "feature" / capture("feature") / "ViewModel.kt".file().template {
                            "class ${captureValue("feature")}ViewModel"
                        }
                    }
                }
            }
        }

        val thrown = shouldThrow<KatachiMissingTemplateCaptureException> { arch.generated("feature.ViewModel") }
        thrown.names shouldContainExactly listOf("feature")
        thrown.role shouldBe "feature.ViewModel"
    }

    "複数の capture のうち一部だけ渡すと、残りだけが名指しされる" {
        val arch = architectureOf {
            "feature".group {
                "Screen".role {
                    layout {
                        "feature" / capture("feature") / "component" /
                            "${capture("fileName")}Screen.kt".file().template { "// screen" }
                    }
                }
            }
        }

        val thrown = shouldThrow<KatachiMissingTemplateCaptureException> {
            arch.generated("feature.Screen", mapOf("feature" to "home"))
        }
        thrown.names shouldContainExactly listOf("fileName")
    }

    "captureValue で読むだけの capture も、無ければ落ちる" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel".role {
                    layout {
                        "feature" / capture("feature") / "ViewModel.kt".file().template {
                            val label = captureValue("feature")
                            "// $label"
                        }
                    }
                }
            }
        }

        shouldThrow<KatachiMissingTemplateCaptureException> { arch.generated("feature.ViewModel") }
    }

    "同じ名前の2か所は1つの --arg で両方埋まる" {
        val arch = architectureOf {
            "feature".group {
                "Component".role {
                    layout {
                        "feature" / capture("feature") / "src" / capture("feature") / "${capture("name")}.kt".file()
                            .template { "// ${captureValue("feature")}" }
                    }
                }
            }
        }

        arch.generated("feature.Component", mapOf("feature" to "home", "name" to "Card")) shouldBe
            mapOf("feature/home/src/home/Card.kt" to "// home")
    }

    "派生の階層は元の --arg を変換して埋める" {
        val arch = architectureOf {
            "feature".group {
                "Screen".role {
                    layout {
                        "feature" / capture("feature") / "${capture("feature").pascalCase}Screen.kt".file()
                            .template { "// ${captureValue("feature")}" }
                    }
                }
            }
        }

        arch.generated("feature.Screen", mapOf("feature" to "my-page")) shouldBe
            mapOf("feature/my-page/MyPageScreen.kt" to "// my-page")
    }

    "文字と混ざった capture でも --arg の空は今のまま拒む" {
        val arch = architectureOf {
            "feature".group {
                "Screen".role {
                    layout { "feature" / "${capture("name")}Screen.kt".file().template { "" } }
                }
            }
        }

        val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> {
            arch.generated("feature.Screen", mapOf("name" to ""))
        }
        thrown.problem shouldBe KatachiInvalidTemplateCaptureValueException.Problem.Empty
    }

    "派生は --arg を取らないので、元の名前だけが足りないと言われる" {
        val arch = architectureOf {
            "feature".group {
                "Screen".role {
                    layout {
                        "feature" / capture("feature") / "${capture("feature").pascalCase}Screen.kt".file().template { "" }
                    }
                }
            }
        }

        shouldThrow<KatachiMissingTemplateCaptureException> { arch.generated("feature.Screen") }
            .names shouldContainExactly listOf("feature")
    }

    "変換後の値が空になるときは KatachiInvalidTemplateCaptureValueException（Empty）" {
        val arch = architectureOf {
            "feature".group {
                "Screen".role {
                    layout {
                        "feature" / capture("feature") / "${capture("feature").flatCase}.kt".file().template { "" }
                    }
                }
            }
        }

        val thrown = shouldThrow<KatachiInvalidTemplateCaptureValueException> {
            arch.generated("feature.Screen", mapOf("feature" to "--"))
        }
        thrown.problem shouldBe KatachiInvalidTemplateCaptureValueException.Problem.Empty
        thrown.name shouldBe "feature"
    }

    "派生だけが残るパスは名前の無い * とは見なさない" {
        val arch = architectureOf {
            "feature".group {
                "Screen".role {
                    layout {
                        "feature/${capture("f")}" { "${capture("f").pascalCase}Screen.kt".file().template { "" } }
                    }
                }
            }
        }

        shouldNotThrow<KatachiTemplateOnWildcardException> { arch.flattenLayout() }
    }

    "足りない capture の候補は元の capture の階層のディレクトリから読み、派生の階層は見ない" {
        val tree = repositoryOf { "feature" { "home" { "A.kt"() }; "settings" { "B.kt"() } } }
        val fromPlain = architectureOf {
            "feature".group {
                "Screen".role {
                    layout { "feature" / capture("f") / "${capture("f").pascalCase}Screen.kt".file().template { "" } }
                }
            }
        }
        val fromDerived = architectureOf {
            "feature".group {
                "Screen".role {
                    layout { capture("f").pascalCase / "x" / capture("f") / "A.kt".file().template { "" } }
                }
            }
        }

        val plain = shouldThrow<KatachiMissingTemplateCaptureException> {
            fromPlain.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("feature.Screen")), tree)
                .getOrThrow()
        }
        plain.existingValues["f"] shouldBe listOf("home", "settings")

        val derived = shouldThrow<KatachiMissingTemplateCaptureException> {
            fromDerived.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(template = listOf("feature.Screen")), tree)
                .getOrThrow()
        }
        derived.existingValues["f"] shouldBe emptyList()
    }
})
