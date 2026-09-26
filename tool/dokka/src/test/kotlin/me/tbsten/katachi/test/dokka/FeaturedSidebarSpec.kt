package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FeaturedSidebarSpec : FreeSpec({
    val run by lazy { DokkaRunner.run(FeaturedSources.ALL_KINDS, DokkaRunner.configuration(moduleName = "sample-module")) }
    val module by lazy { sidebarOf(run.files["navigation.html"].shouldNotBeNull()).single() }

    "単一モジュールの navigation.html" - {
        "モジュールの直下の先頭に Featured があり、モジュールのページを指す" {
            module.name shouldBe "sample-module"
            val featured = module.children.first()
            featured.name shouldBe "⭐️ Featured"
            featured.href shouldBe "index.html"
        }

        "Featured の子は Dokka のサイドバーと同じ名前順で、関数には () が付く" {
            module.children.first().children.map { it.name } shouldContainExactly listOf(
                "Architecture",
                "architecture()",
                "Architecture.assert()",
                "DeclarationScope",
                "DeclarationScope.Defaults.name",
                "Definition",
                "formatVersion",
                "Strictness.STRICT",
            )
        }

        "Featured の子には種類ごとの Dokka のアイコンが付き、見出しには付かない" {
            val featured = module.children.first()
            featured.icon shouldBe emptySet()
            val icons = featured.children.associate { it.name to it.icon }
            icons["Architecture"] shouldBe setOf("class-kt")
            icons["architecture()"] shouldBe setOf("function")
            icons["DeclarationScope"] shouldBe setOf("interface-kt")
            icons["Definition"] shouldBe setOf("typealias-kt")
            icons["formatVersion"] shouldBe setOf("val")
        }

        "Featured の子のリンクはどれも同じ run が書いたページを指す" {
            val hrefs = module.children.first().children.map { it.href.substringBefore('#') }
            withClue("href: $hrefs\nfiles: ${run.files.keys.sorted()}") {
                hrefs.filterNot { it in run.files }.shouldContainExactly(emptyList())
            }
        }

        "Featured の後ろには、名前の階層に並べ直したパッケージが続く" {
            module.children.drop(1).map { it.name } shouldContainExactly listOf("sample")
            module.children.drop(1).single().children.first().name shouldBe "dsl"
        }
    }

    "featuredTitle を変えるとサイドバーの見出しも変わる" {
        val custom = DokkaRunner.run(
            FeaturedSources.ALL_KINDS,
            DokkaRunner.configuration(pluginJson = """{ "featuredTitle": "Start here" }"""),
        )
        sidebarOf(custom.files["navigation.html"].shouldNotBeNull()).single().children.first().name shouldBe "⭐️ Start here"
    }

    "@featured が無いモジュールのサイドバーは Dokka のまま" {
        val plain = DokkaRunner.run(FeaturedSources.NONE)
        sidebarOf(plain.files["navigation.html"].shouldNotBeNull()).single().children.map { it.name } shouldNotContain
            "⭐️ Featured"
    }
})
