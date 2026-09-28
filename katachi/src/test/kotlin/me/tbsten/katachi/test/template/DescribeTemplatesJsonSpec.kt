package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.DescribeTemplatesFormat
import me.tbsten.katachi.template.KatachiTemplateJsonOutputMissingException
import me.tbsten.katachi.template.KatachiTemplateJsonWithTemplateException
import me.tbsten.katachi.test.check.architectureOf

/** `DescribeTemplates.Args(format = Json)`, end to end: writes `output`, still prints the list. */
class DescribeTemplatesJsonSpec : FreeSpec({
    val arch = architectureOf {
        "domain".group {
            "UseCase" {
                layout {
                    "useCase" / "${capture("name")}UseCase.kt".file().template {
                        "class ${captureValue("name")}UseCase"
                    }
                }
            }
        }
    }

    "output に書く" {
        withTempProject { projectDir ->
            val output = projectDir.resolve("templateDescription.json")

            arch.process(
                DescribeTemplates,
                DescribeTemplates.Args(format = DescribeTemplatesFormat.Json, output = output.path),
            ).getOrThrow()

            output.exists() shouldBe true
            output.readText() shouldContain "\"template\": \"domain.UseCase\""
        }
    }

    "output を省くと KatachiTemplateJsonOutputMissingException" {
        shouldThrow<KatachiTemplateJsonOutputMissingException> {
            arch.process(DescribeTemplates, DescribeTemplates.Args(format = DescribeTemplatesFormat.Json)).getOrThrow()
        }
    }

    "template と format=json を両方渡すと KatachiTemplateJsonWithTemplateException" {
        val thrown = shouldThrow<KatachiTemplateJsonWithTemplateException> {
            arch.process(
                DescribeTemplates,
                DescribeTemplates.Args(template = "UseCase", format = DescribeTemplatesFormat.Json, output = "out.json"),
            ).getOrThrow()
        }
        thrown.template shouldBe "UseCase"
    }

    "JSON にはテンプレート単位の template / id / conflict / pattern / segment があり、fileCount / unresolvedPatterns は無い" {
        withTempProject { projectDir ->
            val output = projectDir.resolve("templateDescription.json")
            arch.process(
                DescribeTemplates,
                DescribeTemplates.Args(format = DescribeTemplatesFormat.Json, output = output.path),
            ).getOrThrow()

            val json = output.readText()
            json shouldContain "\"template\": \"domain.UseCase\""
            json shouldContain "\"conflict\": false"
            json shouldContain "\"pattern\":"
            json shouldContain "\"segment\":"
            json shouldNotContain "fileCount"
            json shouldNotContain "unresolvedPatterns"
        }
    }
})
