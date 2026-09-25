package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ModulePageFeaturedSpec : FreeSpec({
    val run by lazy { DokkaRunner.run(FeaturedSources.ALL_KINDS, DokkaRunner.configuration(moduleName = "sample-module")) }
    val page by lazy { run.files["index.html"].shouldNotBeNull() }
    val rows by lazy { featuredSectionOf(page).shouldNotBeNull() }

    "モジュールページの本文" - {
        "Featured の節が Packages の節より前にある" {
            headersOf(page) shouldContainInOrder listOf("Featured", "Packages")
        }

        "@featured の宣言を、サイドバーと同じ名前で1行ずつ並べる" {
            rows.map { it.name } shouldContainExactly FeaturedSources.ALL_KINDS_NAMES
        }

        "リンクはどれも同じ run が書いたページを指す" {
            withClue(run.files.keys.sorted()) {
                rows.map { it.href.substringBefore('#') }.filterNot { it in run.files } shouldContainExactly emptyList()
            }
        }

        "概要は @featured の文、無ければ KDoc の最初の文で、2段落目は出ない" {
            val summaries = rows.associate { it.name to it.summary }
            summaries["Architecture.assert"] shouldBe "Runs every check and throws AssertionError on violations."
            summaries["architecture"] shouldBe "Declares the architecture of a project."
        }
    }

    "@featured が無いモジュールのページには節を出さない" {
        featuredSectionOf(DokkaRunner.run(FeaturedSources.NONE).files["index.html"].shouldNotBeNull()).shouldBeNull()
    }

    "featuredTitle を変えると節の見出しも変わる" {
        val custom = DokkaRunner.run(
            FeaturedSources.ALL_KINDS,
            DokkaRunner.configuration(pluginJson = """{ "featuredTitle": "Start here" }"""),
        )
        featuredSectionOf(custom.files["index.html"].shouldNotBeNull(), title = "Start here").shouldNotBeNull()
    }
})
