package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.test.check.architectureOf

/**
 * A run's values missing a directory-level (non-module) capture the chosen template needs.
 */
class TemplateCapturePlacementSpec : FreeSpec({
    "値が無い capture は KatachiMissingTemplateCaptureException" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
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
                "Screen" {
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
                "ViewModel" {
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
})
