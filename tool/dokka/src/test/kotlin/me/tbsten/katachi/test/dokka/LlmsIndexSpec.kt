package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File

class LlmsIndexSpec : FreeSpec({
    val workDirectory = temporaryDirectory()
    afterSpec { workDirectory.deleteRecursively() }

    val modules = mapOf(
        "alpha" to FeaturedSources.module("alpha.api", "AlphaEntry", "The way into alpha."),
        "beta" to FeaturedSources.module("beta.api", "BetaEntry", "The way into `beta`."),
    )

    "2つのモジュールを束ねた出力" - {
        val output by lazy { generateMultiModule(File(workDirectory, "relative"), modules) }
        val index by lazy { File(output, "llms.txt").readText() }

        "ルートの llms.txt はプロジェクト名のタイトルで始まる" {
            index shouldStartWith "# project API reference\n"
        }

        "Featured に全モジュールの featured が、モジュールの順に並ぶ" {
            index.section("Featured").lines() shouldContainExactly listOf(
                "- [AlphaEntry](alpha/alpha.api/-alpha-entry/index.html.md): The way into alpha.",
                "- [BetaEntry](beta/beta.api/-beta-entry/index.html.md): The way into `beta`.",
            )
        }

        "Modules に各モジュールの llms.txt と llms-full.txt へのリンクがある" {
            LINK.findAll(index.section("Modules")).map { it.groupValues[1] }.toList() shouldContainExactly listOf(
                "alpha/llms.txt",
                "alpha/llms-full.txt",
                "beta/llms.txt",
                "beta/llms-full.txt",
            )
        }

        "リンク先はすべて束ねた出力に実在する (モジュールの llms はコピーされている)" {
            withClue(output.walkTopDown().map { it.relativeTo(output).path }.sorted().toList()) {
                LINK.findAll(index).map { it.groupValues[1] }.toList()
                    .forEach { File(output, it).isFile shouldBe true }
            }
        }

        "トップページの Markdown 版に、Featured と各モジュールのページの Markdown 版へのリンクが並ぶ" {
            val top = File(output, "index.html.md").readText()
            top shouldStartWith "# project API reference\n"
            top.section("Modules").lines() shouldContainExactly listOf("- [alpha](alpha/index.html.md)", "- [beta](beta/index.html.md)")
            top.section("Featured") shouldContain "- [AlphaEntry](alpha/alpha.api/-alpha-entry/index.html.md): The way into alpha."
            LINK.findAll(top).map { it.groupValues[1] }.toList().forEach { File(output, it).isFile shouldBe true }
        }

        "コピーされたモジュールの llms.txt のリンクは、そのファイルからの相対で実在するページを指す" {
            val alpha = File(output, "alpha/llms.txt").readText()
            alpha shouldContain "- [AlphaEntry](alpha.api/-alpha-entry/index.html.md): The way into alpha."
            LINK.findAll(alpha).map { it.groupValues[1] }.toList()
                .forEach { File(output, "alpha/$it").isFile shouldBe true }
        }
    }

    "束ねる run に baseUrl と projectSummary を渡すと、目次に反映される" {
        val output = generateMultiModule(
            File(workDirectory, "absolute"),
            modules,
            aggregatedPluginJson = """{ "baseUrl": "https://example.com/api-docs/", "projectSummary": "Two modules." }""",
        )
        val index = File(output, "llms.txt").readText()
        index shouldStartWith "# project API reference\n\n> Two modules.\n"
        index shouldContain "- [alpha](https://example.com/api-docs/alpha/llms.txt)"
        index shouldContain "- [AlphaEntry](https://example.com/api-docs/alpha/alpha.api/-alpha-entry/index.html.md)"
        // The modules' own files are finished by the same run, with the same base.
        File(output, "alpha/llms.txt").readText() shouldContain
            "- [AlphaEntry](https://example.com/api-docs/alpha/alpha.api/-alpha-entry/index.html.md): The way into alpha."
    }
})

private val LINK = Regex("""\]\(([^)]+)\)""")
