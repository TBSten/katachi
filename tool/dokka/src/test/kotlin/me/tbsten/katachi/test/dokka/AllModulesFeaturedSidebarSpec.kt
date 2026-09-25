package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.fragment.FRAGMENT_FILE
import java.io.File

class AllModulesFeaturedSidebarSpec : FreeSpec({
    val workDirectory = temporaryDirectory()
    afterSpec { workDirectory.deleteRecursively() }

    "2つのモジュールを束ねた出力" - {
        val output by lazy {
            generateMultiModule(
                workDirectory,
                mapOf(
                    "alpha" to FeaturedSources.module("alpha.api", "AlphaEntry", "The way into alpha."),
                    "beta" to FeaturedSources.module("beta.api", "BetaEntry", "The way into `beta`."),
                ),
            )
        }
        val sidebar by lazy { sidebarOf(File(output, "navigation.html").readText()) }

        "束ねたサイドバーでも、各モジュールの直下の先頭に Featured がある" {
            sidebar.map { it.name } shouldContainExactly listOf("alpha", "beta")
            sidebar.map { module -> module.children.first().name } shouldContainExactly listOf("Featured", "Featured")
            sidebar.named("alpha").children.first().children.map { it.name } shouldContainExactly listOf("AlphaEntry")
            sidebar.named("beta").children.first().children.map { it.name } shouldContainExactly listOf("BetaEntry")
        }

        "リンクは出力のルートからの相対で、各モジュールの出力にある実在のページを指す" {
            val hrefs = sidebar.flatMap { module -> module.children.first().let { listOf(it) + it.children } }.map { it.href }
            hrefs shouldContainExactly listOf(
                "alpha/index.html",
                "alpha/alpha.api/-alpha-entry/index.html",
                "beta/index.html",
                "beta/beta.api/-beta-entry/index.html",
            )
            hrefs.forEach { href -> withClue(href) { File(output, href).isFile shouldBe true } }
        }

        "どのページも、pathToRoot の先にある Featured 入りの navigation.html を読む" {
            val pages = output.walkTopDown()
                .filter { it.isFile && it.extension == "html" && it.name != "navigation.html" }
                .toList()
            // The top page, both module pages, and a package and a class page of each.
            pages.size shouldBeGreaterThanOrEqual 7
            pages.forEach { page ->
                val pathToRoot = PATH_TO_ROOT.find(page.readText())?.groupValues?.get(1)
                    ?: error("${page.relativeTo(output)} does not set pathToRoot")
                val navigation = File(page.parentFile, "${pathToRoot}navigation.html").normalize()
                withClue(page.relativeTo(output)) {
                    navigation shouldBe File(output, "navigation.html").normalize()
                }
            }
        }

        "断片のファイルは束ねた出力に残らない" {
            output.walkTopDown().filter { it.name == FRAGMENT_FILE }.toList() shouldContainExactly emptyList()
        }
    }

    "どのモジュールにも @featured が無ければ、サイドバーに Featured を足さない" {
        val output = generateMultiModule(File(workDirectory, "none"), mapOf("plain" to FeaturedSources.NONE))
        sidebarOf(File(output, "navigation.html").readText()).single().children.map { it.name } shouldNotContain "Featured"
    }
})

private val PATH_TO_ROOT = Regex("""var pathToRoot = "([^"]*)";""")
