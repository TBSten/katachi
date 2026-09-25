package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class AllModulesFeaturedSpec : FreeSpec({
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
        val top by lazy { File(output, "index.html").readText() }

        "トップページでは、Featured の節が All modules: より前にある" {
            val featured = top.indexOf(">Featured<")
            withClue(headersOf(top)) {
                (featured in 0 until top.indexOf("All modules:")) shouldBe true
            }
        }

        "トップページの Featured は全モジュールの featured をモジュールの順に並べ、概要も出す" {
            featuredSectionOf(top).shouldNotBeNull().map { it.name to it.summary } shouldContainExactly listOf(
                "AlphaEntry" to "The way into alpha.",
                "BetaEntry" to "The way into beta.",
            )
        }

        "トップページの Featured のリンクは、束ねた出力に実在するページを指す" {
            featuredSectionOf(top).shouldNotBeNull().map { it.href } shouldContainExactly listOf(
                "alpha/alpha.api/-alpha-entry/index.html",
                "beta/beta.api/-beta-entry/index.html",
            )
            featuredSectionOf(top).shouldNotBeNull().forEach { File(output, it.href).isFile shouldBe true }
        }

        "各モジュールのページでも、Featured の節が Packages より前にあり、リンクは実在する" {
            val alpha = File(output, "alpha/index.html").readText()
            headersOf(alpha) shouldContainInOrder listOf("Featured", "Packages")
            featuredSectionOf(alpha).shouldNotBeNull().forEach { row ->
                File(output, "alpha/${row.href}").normalize().isFile shouldBe true
            }
        }

        "サイドバーの Featured も残っている" {
            File(output, "navigation.html").readText() shouldContain ">Featured<"
        }
    }

    "どのモジュールにも @featured が無ければ、トップページに節を出さない" {
        val output = generateMultiModule(File(workDirectory, "none"), mapOf("plain" to FeaturedSources.NONE))
        featuredSectionOf(File(output, "index.html").readText()).shouldBeNull()
    }
})
