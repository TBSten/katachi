package me.tbsten.katachi.test.template

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.DescribeTemplatesFormat
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

private fun captureArchitecture(): Architecture = architecture {
    "feature".group {
        "ViewModel" {
            layout { "feature" / capture("feature") / "src" / "*ViewModel.kt".file() }
            template {
                val name by stringParameter()
                file("${name}ViewModel.kt") { "package com.example.${captureValue("feature")}" }
            }
        }
        "Screen" {
            layout { ":feature:*".module(capture = "module") { "*Screen.kt".file() } }
            template {
                val name by stringParameter()
                file("${name}Screen.kt") { "" }
            }
        }
        "Plain" {
            layout { "plain" / "*.kt".file() }
            template { file("Plain.kt") { "" } }
        }
    }
}

private fun Architecture.describe(roleName: String? = null): Pair<Any, List<String>> {
    val context = FakeArchitectureProcessContext(
        architecture = this,
        args = DescribeTemplates.Args(roleName = roleName),
        fileSystem = ForbiddenFileSystem,
    )
    return DescribeTemplates.process(context).getOrThrow() to context.logs
}

private fun Architecture.detail(roleName: String): Pair<TemplateDetail, List<String>> {
    val (description, logs) = describe(roleName)
    return description.shouldBeInstanceOf<TemplateDetail>() to logs
}

/**
 * The captures `DescribeTemplates` lists next to the parameters, from the declarations alone.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.template` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class DescribeTemplatesCaptureSpec : FreeSpec({
    "詳細に capture が名前・種類・パターン・位置つきで出る" {
        val (viewModel, _) = captureArchitecture().detail("ViewModel")
        val (screen, _) = captureArchitecture().detail("Screen")

        viewModel.captures.map { listOf(it.name, it.kind, it.pattern, it.position) } shouldBe listOf(
            listOf("feature", TemplateCaptureKind.PathCapture, "feature/*/src/*ViewModel.kt", 1),
        )
        screen.captures.map { listOf(it.name, it.kind, it.pattern, it.position) } shouldBe listOf(
            listOf("module", TemplateCaptureKind.ModuleCapture, ":feature:*", 0),
        )
    }

    "capture は parameters に混ざらない" {
        captureArchitecture().detail("ViewModel").first.parameters.map { it.name } shouldContainExactly listOf("name")
    }

    "一覧にも capture が出る" {
        val (description, logs) = captureArchitecture().describe()

        val list = description.shouldBeInstanceOf<TemplateList>()
        list.templates.map { summary -> summary.captures.map { it.name } } shouldBe
            listOf(listOf("feature"), listOf("module"), emptyList())
        list.templates.first().parameterNames shouldContainExactly listOf("name")
        logs shouldContain "    captures: feature"
    }

    "capture の無い役割の captures は空" {
        captureArchitecture().detail("Plain").first.captures.shouldBeEmpty()
    }

    "exampleCommand に capture の --arg が入る" {
        captureArchitecture().detail("ViewModel").first.exampleCommand shouldBe
            "./gradlew katachiTemplate --arg roleName=feature/ViewModel --arg feature=<feature> --arg name=Name"
    }

    "パスの capture のプレビューは \${name} でパスを出し、中身でも同じ値を読む" {
        val file = captureArchitecture().detail("ViewModel").first.files.single()

        file.path shouldBe "feature/\${feature}/src/\${name}ViewModel.kt"
        file.content shouldBe "package com.example.\${feature}"
    }

    "モジュールの capture もモジュールキーの慣習のディレクトリで \${name} と埋めてパスを出す" {
        val (detail, logs) = captureArchitecture().detail("Screen")
        val file = detail.files.single()

        file.path shouldBe "feature/\${module}/\${name}Screen.kt"
        file.unresolvedPatterns.shouldBeEmpty()
        withClue("生成できる役割なので、生成のコマンドを出す") {
            logs shouldContain "  ${detail.exampleCommand}"
        }
    }

    "名前の無い * が残って本当に置けない役割は、コマンドの代わりに理由と直し方を出す" {
        val arch = architecture {
            "Screen" {
                layout { ":feature:*".module { "*Screen.kt".file() } }
                template { file("HomeScreen.kt") { "" } }
            }
        }
        val (detail, logs) = arch.detail("Screen")

        detail.files.single().path.shouldBeNull()
        logs.none { it.startsWith("Generate it with") } shouldBe true
        logs shouldContain "It cannot be generated as the layout is declared: HomeScreen.kt has no single directory (above)."
        logs.any { it.startsWith("Name every * left in the directory with capture(") } shouldBe true
    }

    "** が残る役割は ** の無いパスを別に書くよう案内する" {
        val arch = architecture {
            "Screen" {
                layout { "app" / "**" / "*Screen.kt".file() }
                template { file("HomeScreen.kt") { "" } }
            }
        }
        val (_, logs) = arch.detail("Screen")

        logs.any { it.startsWith("A ** cannot be named: declare the directory") } shouldBe true
    }

    "Text 出力に Captures の節が出る" {
        val (_, logs) = captureArchitecture().detail("ViewModel")

        logs shouldContain "Captures (--arg values that choose the directory):"
        logs shouldContain "  feature: level 2 of feature/*/src/*ViewModel.kt"
        val (_, screenLogs) = captureArchitecture().detail("Screen")
        screenLogs shouldContain "  module: wildcard 1 of module :feature:*, an existing module"
    }

    "JSON に captures キーが足される" {
        withTempProject { root ->
            val output = File(root, "templateDescription.json")
            DescribeTemplates.process(
                FakeArchitectureProcessContext(
                    architecture = captureArchitecture(),
                    args = DescribeTemplates.Args(format = DescribeTemplatesFormat.Json, output = output.path),
                    fileSystem = ForbiddenFileSystem,
                ),
            ).getOrThrow()

            val json = output.readText()
            json shouldContain "\"kind\": \"PathCapture\""
            json shouldContain "\"kind\": \"ModuleCapture\""
            json shouldContain "\"pattern\": \":feature:*\""
            json shouldContain "\"parameterNames\": [\"name\"]"
        }
    }
})
