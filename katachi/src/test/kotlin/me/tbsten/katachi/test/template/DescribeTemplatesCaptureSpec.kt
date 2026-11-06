@file:Suppress("DEPRECATION_ERROR")

package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.test.check.architectureOf

/** [me.tbsten.katachi.template.TemplateCapturePreview]: name, kind, pattern, position, segment. */
class DescribeTemplatesCaptureSpec : FreeSpec({
    "ディレクトリの capture（丸ごと1セグメント）" {
        val arch = architectureOf {
            "feature".group {
                "ViewModel".role {
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
                "Screen".role {
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
                "BuildFile".role {
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

    "同じ名前の2か所は position ごとに一覧に出て、一覧の captures と exampleCommand は元の名前だけ" {
        val arch = architectureOf {
            "feature".group {
                "Component".role {
                    layout {
                        "feature" / capture("feature") / "src" / capture("feature") / "${capture("name")}.kt".file().template { "" }
                    }
                }
            }
        }
        val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "feature.Component"))
            .getOrThrow() as TemplateDetail

        detail.captures.map { it.name to it.position } shouldBe listOf("feature" to 1, "feature" to 3, "name" to 4)
        detail.captures.map { it.transform } shouldBe listOf(null, null, null)
        detail.exampleCommand shouldContain "--arg feature=<feature> --arg name=<name>"
        detail.exampleCommand.split("--arg feature=").size shouldBe 2
        val list = arch.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList
        list.templates.single().captures.map { it.name }.distinct() shouldBe listOf("feature", "name")
    }

    "同じ区切りの2か所も wildcardIndex で2行になる" {
        val arch = architectureOf {
            "feature".group {
                "Pair".role { layout { "${capture("a")}-${capture("a")}.kt".file().template { "" } } }
            }
        }
        val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "feature.Pair"))
            .getOrThrow() as TemplateDetail

        detail.captures.map { it.wildcardIndex } shouldBe listOf(0, 1)
        detail.captures.map { it.segment }.distinct() shouldBe listOf("\${a}-\${a}.kt")
    }

    "派生は transform を持ち name は元の名前、segment と files の pattern は <name.transform> で出す" {
        val arch = architectureOf {
            "feature".group {
                "Screen".role {
                    layout {
                        "feature" / capture("feature") / "${capture("feature").pascalCase}Screen.kt".file().template { "" }
                    }
                }
            }
        }
        val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "feature.Screen"))
            .getOrThrow() as TemplateDetail

        detail.captures.map { Triple(it.name, it.transform, it.segment) } shouldBe listOf(
            Triple("feature", null, "\${feature}"),
            Triple("feature", "pascalCase", "<feature.pascalCase>Screen.kt"),
        )
        detail.files.single().pattern shouldBe "feature/\${feature}/<feature.pascalCase>Screen.kt"
        detail.files.single().captures shouldBe listOf("feature")
        detail.exampleCommand shouldContain "--arg feature=<feature>"
        detail.exampleCommand shouldNotContain "pascalCase"
    }
})
