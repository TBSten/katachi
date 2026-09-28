package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.test.check.architectureOf

/** [me.tbsten.katachi.template.TemplateCapturePreview]: name, kind, pattern, position, segment. */
class DescribeTemplatesCaptureSpec : FreeSpec({
    "ディレクトリの capture（丸ごと1セグメント）" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel" {
                    layout { "feature" / capture("feature") / "ViewModel.kt".file().template { "" } }
                }
            }
        }
        val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "feature.ViewModel"))
            .getOrThrow() as TemplateDetail
        val capture = detail.captures.single()
        capture.name shouldBe "feature"
        capture.kind shouldBe TemplateCaptureKind.PathCapture
        capture.pattern shouldBe "feature/*/ViewModel.kt"
        capture.position shouldBe 1
        capture.segment shouldBe "\${feature}"
    }

    "ファイル名の部分一致の capture は segment にリテラルも残す" {
        val arch = architectureOf {
            "feature".group {
                "Screen" {
                    layout { "feature" / "${capture("fileName")}Screen.kt".file().template { "" } }
                }
            }
        }
        val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "feature.Screen"))
            .getOrThrow() as TemplateDetail
        detail.captures.single().segment shouldBe "\${fileName}Screen.kt"
    }

    "モジュールの capture は module key 全体を segment に持つ" {
        val arch = architectureOf {
            "feature".group {
                "BuildFile" {
                    layout { ":feature:${capture("feature")}".module { "module.txt".file().template { "" } } }
                }
            }
        }
        val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "feature.BuildFile"))
            .getOrThrow() as TemplateDetail
        val capture = detail.captures.single()
        capture.kind shouldBe TemplateCaptureKind.ModuleCapture
        capture.pattern shouldBe ":feature:*"
        capture.position shouldBe 0
        capture.segment shouldBe ":feature:\${feature}"
    }
})
