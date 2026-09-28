package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.processor.KatachiDuplicateProcessorArgException
import me.tbsten.katachi.processor.decodeFromStringMap
import me.tbsten.katachi.processor.internal.parseProcessorCommandLine
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.OnExisting

/**
 * `GenerateCodeFromTemplate.Args` / `DescribeTemplates.Args`: `--arg template=` decoded, and the
 * command line's own rules around it -- design draft section 2.
 */
class TemplateProcessorArgsSpec : FreeSpec({
    "GenerateCodeFromTemplate.Args" - {
        "template は List なので --arg template=a,b が , で割れる" {
            decodeFromStringMap(GenerateCodeFromTemplate.Args.serializer(), mapOf("template" to "a,b")) shouldBe
                GenerateCodeFromTemplate.Args(template = listOf("a", "b"))
        }

        "1つだけなら1要素の List" {
            decodeFromStringMap(GenerateCodeFromTemplate.Args.serializer(), mapOf("template" to "UseCase")) shouldBe
                GenerateCodeFromTemplate.Args(template = listOf("UseCase"))
        }

        "--arg template= （空文字）は空の List になる" {
            decodeFromStringMap(GenerateCodeFromTemplate.Args.serializer(), mapOf("template" to "")) shouldBe
                GenerateCodeFromTemplate.Args(template = emptyList())
        }

        "onExisting を省くと Fail" {
            decodeFromStringMap(GenerateCodeFromTemplate.Args.serializer(), mapOf("template" to "UseCase")).onExisting shouldBe
                OnExisting.Fail
        }

        "onExisting=skip / overwrite を読める" {
            decodeFromStringMap(
                GenerateCodeFromTemplate.Args.serializer(),
                mapOf("template" to "UseCase", "onExisting" to "skip"),
            ).onExisting shouldBe OnExisting.Skip
            decodeFromStringMap(
                GenerateCodeFromTemplate.Args.serializer(),
                mapOf("template" to "UseCase", "onExisting" to "overwrite"),
            ).onExisting shouldBe OnExisting.Overwrite
        }

        "同じ --arg template= を2回書くと KatachiDuplicateProcessorArgException" {
            val thrown = shouldThrow<KatachiDuplicateProcessorArgException> {
                parseProcessorCommandLine(
                    arrayOf(
                        "--entry-point=com.example.GeneratedKatachiEntryPoint",
                        "--processor=template",
                        "--arg=template=a",
                        "--arg=template=b",
                    ),
                )
            }
            thrown.key shouldBe "template"
        }
    }

    "DescribeTemplates.Args" - {
        "template は String? なので1つの指定をそのまま読む" {
            decodeFromStringMap(DescribeTemplates.Args.serializer(), mapOf("template" to "data.Repository.repository")) shouldBe
                DescribeTemplates.Args(template = "data.Repository.repository")
        }

        "省略すると null（一覧）" {
            decodeFromStringMap(DescribeTemplates.Args.serializer(), emptyMap()) shouldBe DescribeTemplates.Args()
        }

        "format / output も読める" {
            val args = decodeFromStringMap(
                DescribeTemplates.Args.serializer(),
                mapOf("format" to "json", "output" to "out.json"),
            )
            args.format shouldBe me.tbsten.katachi.template.DescribeTemplatesFormat.Json
            args.output shouldBe "out.json"
        }
    }
})
