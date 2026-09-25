package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class LlmsCrossModuleSpec : FreeSpec({
    val workDirectory = temporaryDirectory()
    afterSpec { workDirectory.deleteRecursively() }

    val modules = mapOf(
        "alpha" to FeaturedSources.module("alpha.api", "AlphaEntry", "The way into alpha."),
        "beta" to BETA,
    )
    val libraries = mapOf("lib" to LIB)
    val dependencies = mapOf("beta" to listOf("alpha", "lib"))

    "他のモジュールの宣言へのリンクを持つモジュールを束ねた出力" - {
        val output by lazy { generateMultiModule(File(workDirectory, "relative"), modules, dependencies = dependencies, libraries = libraries) }
        val full by lazy { File(output, "beta/llms-full.txt").readText() }

        "他のモジュールへのリンクは、HTML と同じく package-list で解決され、ファイルからの相対で実在するページを指す" {
            full shouldContain "Starts from [AlphaEntry](../alpha/alpha.api/-alpha-entry/index.html.md)"
            File(output, "beta/../alpha/alpha.api/-alpha-entry/index.html.md").isFile shouldBe true
        }

        "HTML でも同じページへのリンクになっている" {
            File(output, "beta/beta.api/-beta-user/index.html").readText() shouldContain
                "href=\"../../../alpha/alpha.api/-alpha-entry/index.html\""
        }

        "どのモジュールにもページが無い宣言へのリンクは、文字だけになる" {
            full shouldContain "unlike LibThing."
        }

        "llms.txt でも同じく解決される" {
            File(output, "beta/llms.txt").readText() shouldContain
                "Starts from [AlphaEntry](../alpha/alpha.api/-alpha-entry/index.html.md)"
        }

        "ページの Markdown 版でも、他のモジュールへのリンクはその Markdown 版を、ファイルからの相対で指す" {
            File(output, "beta/beta.api/-beta-user/index.html.md").readText() shouldContain
                "Starts from [AlphaEntry](../../../alpha/alpha.api/-alpha-entry/index.html.md), unlike LibThing."
        }

        "束ねた出力の HTML ページには、どれも Markdown 版がある" {
            output.walkTopDown().filter { it.isFile && it.extension == "html" && it.name != "navigation.html" }
                .filterNot { File(it.path + ".md").isFile }
                .map { it.relativeTo(output).path }
                .toList() shouldContainExactly emptyList()
        }

        "束ねた出力の Markdown 版と llms ファイルのリンクは、どれもそのファイルからの相対で実在するファイルを指す" {
            val broken = llmsFilesOf(output).flatMap { file ->
                LINK.findAll(file.readText()).map { it.groupValues[1] }
                    .filterNot { it.startsWith("https://") }
                    .filterNot { File(file.parentFile, it.substringBefore('#')).normalize().isFile }
                    .map { "${file.relativeTo(output)} -> $it" }
            }
            broken shouldContainExactly emptyList()
        }

        "束ねた出力の llms ファイルには、モジュールの run が残した仮のリンクが残らない" {
            val leftovers = llmsFilesOf(output)
                .filter { it.readText().contains("katachi-dokka") }
                .map { it.relativeTo(output).path }
                .toList()
            leftovers shouldContainExactly emptyList()
        }
    }

    "モジュールの run と束ねる run の両方に baseUrl (サイトのルート) を渡すと" - {
        val output by lazy {
            val json = """{ "baseUrl": "https://example.com/api-docs/" }"""
            generateMultiModule(
                File(workDirectory, "absolute"),
                modules,
                aggregatedPluginJson = json,
                modulePluginJson = json,
                dependencies = dependencies,
                libraries = libraries,
            )
        }
        val full by lazy { File(output, "beta/llms-full.txt").readText() }

        "自分のモジュールへのリンクは、モジュールの置き場所を1回だけ含む絶対 URL になる" {
            full shouldContain "Page: [BetaUser](https://example.com/api-docs/beta/beta.api/-beta-user/index.html.md)"
            full shouldNotContain "api-docs/beta/beta/"
        }

        "他のモジュールへのリンクも、束ねたサイトの絶対 URL になる" {
            full shouldContain "[AlphaEntry](https://example.com/api-docs/alpha/alpha.api/-alpha-entry/index.html.md)"
        }

        "すべてのリンクは baseUrl を外すと束ねた出力に実在する" {
            val links = LINK.findAll(full).map { it.groupValues[1] }.toList()
            withClue(links) {
                links.isNotEmpty() shouldBe true
                links.forEach { link ->
                    link shouldContain "https://example.com/api-docs/"
                    File(output, link.removePrefix("https://example.com/api-docs/").substringBefore('#')).isFile shouldBe true
                }
            }
        }
    }
})

private val LINK = Regex("""\]\(([^)]+)\)""")

/** Every llms file and Markdown page of [output]. */
private fun llmsFilesOf(output: File): List<File> = output.walkTopDown()
    .filter { it.isFile && (it.name.startsWith("llms") || it.name.endsWith(".md")) }
    .toList()

/** A module whose KDoc links to a class of `alpha`, and to a class of a library no module documents. */
private val BETA: String = """
    |/src/main/kotlin/beta/api/BetaUser.kt
    |package beta.api
    |
    |import alpha.api.AlphaEntry
    |import lib.LibThing
    |
    |/**
    | * Starts from [AlphaEntry], unlike [LibThing].
    | *
    | * @featured
    | */
    |public class BetaUser
""".trimMargin()

/** A library `beta` uses, which is not one of the documented modules. */
private val LIB: String = """
    |/src/main/kotlin/lib/LibThing.kt
    |package lib
    |
    |public class LibThing
""".trimMargin()
