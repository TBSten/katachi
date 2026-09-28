package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.template.internal.templateDetailLines
import me.tbsten.katachi.template.internal.templateListLines
import me.tbsten.katachi.test.check.architectureOf

/**
 * The text `katachiTemplates` prints, one line per printed line so a prefix survives -- pinned so
 * a change to it is deliberate. See `templateListLines` / `templateDetailLines`.
 */
class TemplateCommandLineOutputSpec : FreeSpec({
    val arch = architectureOf {
        "domain".group {
            "UseCase" {
                summary = "A single app-specific behavior"
                layout {
                    "useCase" / "${capture("name")}UseCase.kt".file().template(id = "useCase", title = "Use case") {
                        val implBody by stringParameter(default = "TODO()")
                        "class ${captureValue("name")}UseCase { $implBody }"
                    }
                }
            }
        }
    }

    "一覧: テンプレートが無ければそう言う" {
        val empty = architectureOf { "domain".group { "UseCase" { layout { "useCase" / "UseCase.kt".file() } } } }
        val list = empty.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList
        templateListLines(list) shouldBe listOf("No declaration attaches a template { }.")
    }

    "一覧: 件数・specifier・title・parameters・captures を出す" {
        val list = arch.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList
        val lines = templateListLines(list)
        lines.first() shouldBe "1 template:"
        lines shouldContain "- domain.UseCase.useCase (Use case)"
        lines.any { it.contains("parameters: implBody") } shouldBe true
        lines.any { it.contains("captures: name") } shouldBe true
    }

    "詳細: role・パラメータ・capture・ファイル・生成コマンドを出す" {
        val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "domain.UseCase.useCase"))
            .getOrThrow() as TemplateDetail
        val lines = templateDetailLines(detail)
        lines.first() shouldBe "Template domain.UseCase.useCase (Use case)"
        lines shouldContain "  role: domain.UseCase"
        lines.any { it.contains("implBody: String, default \"TODO()\"") } shouldBe true
        lines.any { it.contains("name: level 2 of") } shouldBe true
        lines.last() shouldBe "  ${detail.exampleCommand}"
        detail.exampleCommand shouldContain "--arg template=domain.UseCase.useCase"
        detail.exampleCommand shouldContain "--arg name=<name>"
    }
})
