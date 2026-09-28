package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateCapturePreview
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateFilePreview
import me.tbsten.katachi.template.TemplateSummary
import me.tbsten.katachi.template.internal.encodeTemplateDescriptionJson

/**
 * The JSON shape `internalTemplatesJson` writes for the katachi IDE plugin -- design draft
 * section 6. Fixed at the unit's boundary (`TemplateSummary`/`TemplateDetail`), not through a full
 * `DescribeTemplates` run, so it also compiles as documentation of the contract.
 */
class TemplateJsonEncoderSpec : FreeSpec({
    val summary = TemplateSummary(
        template = "data.Repository.repository",
        id = "repository",
        title = "Repository",
        roleName = "data.Repository",
        summary = null,
        parameterNames = listOf("name"),
        captures = emptyList(),
        conflict = false,
    )
    val detail = TemplateDetail(
        template = "data.Repository.repository",
        id = "repository",
        title = "Repository",
        roleName = "data.Repository",
        summary = null,
        parameters = emptyList(),
        files = listOf(
            TemplateFilePreview(
                pattern = "repository/\${name}Repository.kt",
                fileName = "\${name}Repository.kt",
                path = "repository/\${name}Repository.kt",
                captures = listOf("name"),
                parameters = listOf("name"),
                content = "interface \${name}Repository",
            ),
        ),
        branches = emptyList(),
        exampleCommand = "./gradlew katachiTemplate --arg template=data.Repository.repository --arg name=<name>",
        captures = listOf(TemplateCapturePreview("name", TemplateCaptureKind.PathCapture, "repository/*Repository.kt", 1, "\${name}Repository.kt")),
    )

    "一覧の要素に template / id / conflict がある" {
        val json = encodeTemplateDescriptionJson(listOf(summary), emptyList())
        json shouldContain "\"template\": \"data.Repository.repository\""
        json shouldContain "\"id\": \"repository\""
        json shouldContain "\"conflict\": false"
    }

    "旧 role 単位の fileCount は書かない" {
        val json = encodeTemplateDescriptionJson(listOf(summary), emptyList())
        json shouldNotContain "fileCount"
    }

    "詳細には pattern / captures / parameters / segment がある" {
        val json = encodeTemplateDescriptionJson(listOf(summary), listOf(detail))
        json shouldContain "\"pattern\": \"repository/\${name}Repository.kt\""
        json shouldContain "\"segment\": \"\${name}Repository.kt\""
        json shouldContain "\"parameters\": [\"name\"]"
    }

    "旧 unresolvedPatterns は書かない" {
        val json = encodeTemplateDescriptionJson(listOf(summary), listOf(detail))
        json shouldNotContain "unresolvedPatterns"
    }

    "id が無いテンプレートは null で出る（列は必ず書く）" {
        val withoutId = TemplateSummary(
            template = "domain.UseCase",
            id = null,
            title = "UseCase",
            roleName = "domain.UseCase",
            summary = null,
            parameterNames = emptyList(),
            captures = emptyList(),
            conflict = false,
        )
        val json = encodeTemplateDescriptionJson(listOf(withoutId), emptyList())
        json shouldContain "\"id\": null"
    }
})
