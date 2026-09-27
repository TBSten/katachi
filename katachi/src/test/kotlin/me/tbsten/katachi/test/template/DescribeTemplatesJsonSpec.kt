package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.internal.runProcessors
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.DescribeTemplatesFormat
import me.tbsten.katachi.template.KatachiTemplateJsonIoException
import me.tbsten.katachi.template.KatachiTemplateJsonOutputMissingException
import me.tbsten.katachi.template.KatachiTemplateJsonWithRoleNameException
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

/** One template that previews, and one whose block cannot run with a placeholder. */
private fun jsonArchitecture(): Architecture = architecture {
    "domain".group {
        "UseCase" {
            title = "ユースケース"
            layout { "useCase" / "*UseCase.kt".file() }
            template {
                val name by stringParameter()
                file("${name}UseCase.kt") { "interface ${name}UseCase" }
            }
        }
        "Broken" {
            layout { "*.kt".file() }
            template {
                val name by stringParameter()
                // A placeholder is not a real name, and a template may well refuse it.
                if (name.startsWith("$")) throw IllegalStateException("no preview for $name")
                file("$name.kt") { "// $name" }
            }
        }
    }
}

private fun Architecture.describeJson(
    output: String?,
    roleName: String? = null,
): Pair<TemplateList, List<String>> {
    val context = FakeArchitectureProcessContext(
        architecture = this,
        args = DescribeTemplates.Args(roleName = roleName, format = DescribeTemplatesFormat.Json, output = output),
        fileSystem = ForbiddenFileSystem,
    )
    return DescribeTemplates.process(context).getOrThrow().shouldBeInstanceOf<TemplateList>() to context.logs
}

/**
 * `--arg format=json`: the file the katachi IDE plugin reads.
 *
 * What the JSON looks like, key by key, is [TemplateJsonEncoderSpec]'s. This is about when it is
 * written, where, and what goes into it.
 */
class DescribeTemplatesJsonSpec : FreeSpec({
    "format=json" - {
        "output に JSON を書き、一覧のログの後に書いたファイルを file:/// で出す" {
            withTempProject { root ->
                val output = File(root, "build/katachi/internalTemplatesJson/templateDescription.json")

                val (list, logs) = jsonArchitecture().describeJson(output.path)

                list.templates.map { it.roleName } shouldContainExactly listOf("domain/UseCase", "domain/Broken")
                output.readText() shouldContain "\"roleName\": \"domain/UseCase\""
                logs.first() shouldBe "2 templates:"
                logs.last() shouldBe "Wrote file://${output.invariantSeparatorsPath}"
            }
        }

        "プレビューに失敗した役割は details に入らず、templates では fileCount が null" {
            withTempProject { root ->
                val output = File(root, "templateDescription.json")

                val (list, _) = jsonArchitecture().describeJson(output.path)

                list.templates.single { it.roleName == "domain/Broken" }.fileCount.shouldBeNull()
                val details = output.readText().substringAfter("\"details\": [")
                details shouldContain "\"roleName\": \"domain/UseCase\""
                details shouldNotContain "domain/Broken"
            }
        }

        "既にあるファイルは置き換え、作業用のファイルを残さない" {
            withTempProject { root ->
                val output = File(root, "out/templateDescription.json")
                output.parentFile.mkdirs()
                output.writeText("stale")

                jsonArchitecture().describeJson(output.path)

                output.readText() shouldContain "\"templates\""
                output.parentFile.list().orEmpty().toList() shouldContainExactly listOf("templateDescription.json")
            }
        }

        "output が無ければ KatachiTemplateJsonOutputMissingException で失敗する" {
            val failure = shouldThrow<KatachiTemplateJsonOutputMissingException> {
                jsonArchitecture().describeJson(output = null)
            }
            failure.message.orEmpty() shouldContain "--arg output="
        }

        "roleName と一緒に渡すと KatachiTemplateJsonWithRoleNameException で失敗し、何も書かない" {
            withTempProject { root ->
                val output = File(root, "templateDescription.json")

                val failure = shouldThrow<KatachiTemplateJsonWithRoleNameException> {
                    jsonArchitecture().describeJson(output.path, roleName = "UseCase")
                }

                failure.roleName shouldBe "UseCase"
                output.exists() shouldBe false
            }
        }

        "書き先がディレクトリなら KatachiTemplateJsonIoException で失敗する" {
            withTempProject { root ->
                val output = File(root, "taken")
                output.mkdirs()

                val failure = shouldThrow<KatachiTemplateJsonIoException> {
                    jsonArchitecture().describeJson(output.path)
                }

                failure.output shouldBe output.path
                output.isDirectory shouldBe true
            }
        }
    }

    "コマンドラインから" - {
        "--arg format=json --arg output= で書き、[OK] で終わる" {
            withTempProject { root ->
                val output = File(root, "templateDescription.json")
                val out = mutableListOf<String>()

                val summary = runProcessors(
                    architecture = jsonArchitecture(),
                    registry = mapOf("internalTemplatesJson" to DescribeTemplates::class.java),
                    processorKeys = listOf("internalTemplatesJson"),
                    rawArgs = mapOf("format" to "json", "output" to output.path),
                    fileSystem = ForbiddenFileSystem,
                    out = out::add,
                )

                summary.failed shouldBe 0
                out shouldContain "  [internalTemplatesJson] Wrote file://${output.invariantSeparatorsPath}"
                output.exists() shouldBe true
            }
        }

        "format を省けば output を渡しても何も書かない" {
            withTempProject { root ->
                val output = File(root, "templateDescription.json")

                val summary = runProcessors(
                    architecture = jsonArchitecture(),
                    registry = mapOf("templates" to DescribeTemplates::class.java),
                    processorKeys = listOf("templates"),
                    rawArgs = mapOf("output" to output.path),
                    fileSystem = ForbiddenFileSystem,
                    out = { },
                )

                summary.failed shouldBe 0
                output.exists() shouldBe false
            }
        }
    }
})
