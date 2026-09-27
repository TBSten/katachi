package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotStartWith
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.KatachiUnsupportedTemplateJsonValueException
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.template.internal.encodeTemplateDescriptionJson
import me.tbsten.katachi.template.internal.encodeToJson
import me.tbsten.katachi.template.internal.templateDetailOf
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

@Serializable
private enum class JsonSpecTone {
    @SerialName("loud")
    Loud,
    Quiet,
}

@Serializable
private class JsonSpecLeaf(val text: String, val count: Int?)

@Serializable
private class JsonSpecTree(
    val name: String,
    val note: String?,
    val flag: Boolean,
    val tone: JsonSpecTone,
    val tags: List<String>,
    val leaves: List<JsonSpecLeaf>,
    val empty: List<JsonSpecLeaf>,
)

@Serializable
private class JsonSpecText(val value: String)

@Serializable
private class JsonSpecDouble(val value: Double)

private fun jsonString(value: String): String = encodeToJson(JsonSpecText.serializer(), JsonSpecText(value))
    .lines()[1].trim().removePrefix("\"value\": ")

private enum class RepositoryVisibility { Public, Internal }

/** The same shape as the contract fixture of the IDE plugin, trimmed to what the JSON has to show. */
private fun contractArchitecture(): Architecture = architecture {
    "data".group {
        "Repository" {
            title = "リポジトリ"
            summary = "データの取得口"
            layout {
                ":data".module { "src/main/kotlin/com/example/data" / "*.kt".file() }
            }
            template {
                val name by stringParameter()
                val withImpl by booleanParameter(default = true)
                val visibility by enumParameter(default = RepositoryVisibility.Public)
                file("${name}Repository.kt") { "package com.example.data\n\n${visibility.name.lowercase()} interface ${name}Repository\n" }
                if (withImpl) {
                    val implSuffix by stringParameter(default = "Impl")
                    file("${name}Repository$implSuffix.kt") { "class ${name}Repository$implSuffix : ${name}Repository\n" }
                }
            }
        }
    }
    "misc".group {
        "Broken" {
            layout { "*.kt".file() }
            template {
                val name by stringParameter()
                if (name.startsWith("$")) throw IllegalStateException("no preview for $name")
                file("$name.kt") { "" }
            }
        }
    }
}

/**
 * The JSON the `internalTemplatesJson` task writes, and the encoder under it.
 *
 * The whole document of the contract architecture is written out below rather than probed with
 * `shouldContain`: the IDE plugin parses exactly this, so a key gained, lost, renamed or moved is
 * a change to the contract whether or not a single assertion would notice it.
 */
class TemplateJsonEncoderSpec : FreeSpec({
    "文字列のエスケープ" - {
        "\" と \\ はバックスラッシュを付ける" {
            jsonString("a\"b\\c") shouldBe "\"a\\\"b\\\\c\""
        }

        "改行・復帰・タブ・バックスペース・改ページは短い形で書く" {
            jsonString("\n\r\t\b\u000C") shouldBe "\"\\n\\r\\t\\b\\f\""
        }

        "そのほかの制御文字は \\u00XX で書く" {
            jsonString("\u0000\u0001\u001F") shouldBe "\"\\u0000\\u0001\\u001f\""
        }

        "日本語と / と \$ はそのまま書く" {
            jsonString("リポジトリ/\${name}") shouldBe "\"リポジトリ/\${name}\""
        }
    }

    "値の形" - {
        "null・空のリスト・入れ子・enum の SerialName・宣言順のキーを、2スペースの字下げで書く" {
            val tree = JsonSpecTree(
                name = "root",
                note = null,
                flag = true,
                tone = JsonSpecTone.Loud,
                tags = listOf("a", "b"),
                leaves = listOf(JsonSpecLeaf("x", 1), JsonSpecLeaf("y", null)),
                empty = emptyList(),
            )

            encodeToJson(JsonSpecTree.serializer(), tree) shouldBe """
                {
                  "name": "root",
                  "note": null,
                  "flag": true,
                  "tone": "loud",
                  "tags": ["a", "b"],
                  "leaves": [
                    {
                      "text": "x",
                      "count": 1
                    },
                    {
                      "text": "y",
                      "count": null
                    }
                  ],
                  "empty": []
                }
            """.trimIndent() + "\n"
        }

        "SerialName の無い enum はエントリ名で書く" {
            encodeToJson(JsonSpecTone.serializer(), JsonSpecTone.Quiet) shouldBe "\"Quiet\"\n"
        }

        "末尾に改行を1つだけ付け、BOM を付けない" {
            val json = encodeToJson(JsonSpecLeaf.serializer(), JsonSpecLeaf("x", 1))

            json shouldEndWith "}\n"
            json.endsWith("\n\n") shouldBe false
            json shouldNotStartWith "﻿"
        }
    }

    "書けない値" - {
        "Map は KatachiUnsupportedTemplateJsonValueException で落ちる" {
            val failure = shouldThrow<KatachiUnsupportedTemplateJsonValueException> {
                encodeToJson(MapSerializer(String.serializer(), String.serializer()), mapOf("a" to "b"))
            }
            failure.kind shouldBe "MAP"
        }

        "小数は KatachiUnsupportedTemplateJsonValueException で落ちる" {
            val failure = shouldThrow<KatachiUnsupportedTemplateJsonValueException> {
                encodeToJson(JsonSpecDouble.serializer(), JsonSpecDouble(1.5))
            }
            failure.kind shouldBe "Double"
        }
    }

    "templateDescription.json の全体" {
        val arch = contractArchitecture()
        val context = FakeArchitectureProcessContext(
            architecture = arch,
            args = DescribeTemplates.Args(),
            fileSystem = ForbiddenFileSystem,
        )
        val list = DescribeTemplates.process(context).getOrThrow() as? TemplateList
            ?: throw AssertionError("DescribeTemplates did not answer a TemplateList")
        val repository = arch.allRoles.single { it.name == "Repository" }
        val detail = templateDetailOf(repository, context.declaredEntries)

        encodeTemplateDescriptionJson(list.templates, listOf(detail)) shouldBe CONTRACT_JSON
    }
})

private const val D: String = "$"

private val CONTRACT_JSON: String = """
    {
      "templates": [
        {
          "roleName": "data/Repository",
          "title": "リポジトリ",
          "summary": "データの取得口",
          "parameterNames": ["name", "withImpl", "visibility", "implSuffix"],
          "fileCount": 2
        },
        {
          "roleName": "misc/Broken",
          "title": null,
          "summary": null,
          "parameterNames": ["name"],
          "fileCount": null
        }
      ],
      "details": [
        {
          "roleName": "data/Repository",
          "title": "リポジトリ",
          "summary": "データの取得口",
          "parameters": [
            {
              "name": "name",
              "kind": "StringParameter",
              "typeName": "String",
              "default": null,
              "acceptedValues": [],
              "isRequired": true,
              "previewValue": "$D{name}",
              "previewValueSource": "Placeholder"
            },
            {
              "name": "withImpl",
              "kind": "BooleanParameter",
              "typeName": "Boolean",
              "default": "true",
              "acceptedValues": ["true", "false"],
              "isRequired": false,
              "previewValue": "true",
              "previewValueSource": "Default"
            },
            {
              "name": "visibility",
              "kind": "EnumParameter",
              "typeName": "RepositoryVisibility",
              "default": "Public",
              "acceptedValues": ["Public", "Internal"],
              "isRequired": false,
              "previewValue": "Public",
              "previewValueSource": "Default"
            },
            {
              "name": "implSuffix",
              "kind": "StringParameter",
              "typeName": "String",
              "default": "Impl",
              "acceptedValues": [],
              "isRequired": false,
              "previewValue": "$D{implSuffix}",
              "previewValueSource": "Placeholder"
            }
          ],
          "files": [
            {
              "fileName": "$D{name}Repository.kt",
              "path": "data/src/main/kotlin/com/example/data/$D{name}Repository.kt",
              "unresolvedPatterns": [],
              "content": "package com.example.data\n\npublic interface $D{name}Repository\n"
            },
            {
              "fileName": "$D{name}Repository$D{implSuffix}.kt",
              "path": "data/src/main/kotlin/com/example/data/$D{name}Repository$D{implSuffix}.kt",
              "unresolvedPatterns": [],
              "content": "class $D{name}Repository$D{implSuffix} : $D{name}Repository\n"
            }
          ],
          "branches": [
            {
              "parameterName": "withImpl",
              "value": "false",
              "addedFiles": [],
              "removedFiles": ["$D{name}Repository$D{implSuffix}.kt"],
              "addedParameters": [],
              "removedParameters": ["implSuffix"]
            }
          ],
          "exampleCommand": "./gradlew katachiTemplate --arg roleName=data/Repository --arg name=Name"
        }
      ]
    }
""".trimIndent() + "\n"
