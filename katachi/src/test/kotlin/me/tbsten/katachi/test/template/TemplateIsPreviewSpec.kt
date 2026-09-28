package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.test.check.architectureOf

/**
 * `TemplateScope.isPreview`: `true` only while [DescribeTemplates] previews a template, `false`
 * for an actual run -- unchanged from before the per-file rewrite, just re-declared as
 * `LayoutFile.template { }`.
 */
class TemplateIsPreviewSpec : FreeSpec({
    "DescribeTemplates（プレビュー）では true、GenerateCodeFromTemplate（実行）では false" {
        val seen = mutableListOf<Boolean>()
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "useCase" / "GetUserUseCase.kt".file().template {
                            seen += isPreview
                            "// preview=$isPreview"
                        }
                    }
                }
            }
        }

        arch.process(DescribeTemplates, DescribeTemplates.Args(template = "UseCase")).getOrThrow()
        arch.generated("UseCase")

        seen.first() shouldBe true
        seen.last() shouldBe false
    }

    "captureValue はプレビュー中だけ \${name} のプレースホルダを返す" {
        var previewed: String? = null
        var real: String? = null
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
                    layout {
                        "feature" / capture("feature") / "ViewModel.kt".file().template {
                            val value = captureValue("feature")
                            if (isPreview) previewed = value else real = value
                            "// $value"
                        }
                    }
                }
            }
        }

        arch.process(DescribeTemplates, DescribeTemplates.Args(template = "feature.ViewModel")).getOrThrow()
        arch.generated("feature.ViewModel", mapOf("feature" to "home"))

        previewed shouldBe "\${feature}"
        real shouldBe "home"
    }

    "isPreview を使うと、プレースホルダ値に対する自前の検査をプレビュー時だけ黙らせられる" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
                    layout {
                        "feature" / capture("resource") / "ViewModel.kt".file().template {
                            val resource = captureValue("resource")
                            require(isPreview || resource.all { it.isLetterOrDigit() }) {
                                "resource must be alphanumeric, was $resource"
                            }
                            "package feature.$resource"
                        }
                    }
                }
            }
        }

        // プレビューは "${resource}" というプレースホルダを渡すので、isPreview を見ずに検査したら
        // 毎回落ちてしまう。
        val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "feature.ViewModel"))
            .getOrThrow() as TemplateDetail
        detail.files.single().content shouldBe "package feature.\${resource}"

        // 実行では isPreview が false なので、値を素通りさせない。
        arch.generated("feature.ViewModel", mapOf("resource" to "home"))
    }
})
