package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

class LlmsTxtSpec : FreeSpec({
    val run by lazy {
        DokkaRunner.run(
            LlmsSources.SHOP,
            DokkaRunner.configuration(moduleName = "shop", includes = listOf(LlmsSources.INCLUDES)),
        )
    }
    val llms by lazy { run.files["llms.txt"].shouldNotBeNull() }

    "単一モジュールの llms.txt" - {
        "出力ルートに、index.html と並べて出る" {
            withClue(run.files.keys.sorted()) {
                listOf("llms.txt", "index.html").forEach { it shouldBeIn run.files.keys }
            }
        }

        "タイトルとモジュールの説明の1段落目で始まる" {
            llms shouldStartWith "# shop\n\n> Everything a shop needs.\n"
        }

        "見出しは Featured → Packages → Types → Functions → Properties → Optional の順" {
            HEADING.findAll(llms).map { it.groupValues[1] }.toList() shouldContainExactly
                listOf("Featured", "Packages", "Types", "Functions", "Properties", "Optional")
        }

        "Featured のリンクは同じ run が書いたクラスのページの Markdown 版を指し、概要は @featured の文になる" {
            val featured = llms.section("Featured")
            featured.lines().first() shouldBe "- [Cart](shop/shop.api/-cart/index.html.md): Start here to build an order."
            withClue(run.files.keys.sorted()) {
                LINK.findAll(featured).map { it.groupValues[1].substringBefore('#') }.toList()
                    .forEach { it shouldBeIn run.files.keys }
            }
        }

        "型の概要には KDoc の1段落目が入り、宣言へのリンクは解決される" {
            llms.section("Types") shouldContain
                "- [Item](shop/shop.api/-item/index.html.md): One thing to buy."
            llms.section("Types") shouldContain
                "checked out with [checkout](shop/shop.api/checkout.html.md)."
        }

        "optionalPackagePatterns に当たるパッケージとその型は Optional にだけ出る" {
            llms.section("Optional") shouldContain "[shop.internal]"
            llms.section("Optional") shouldContain "[Ledger]"
            llms.section("Types") shouldNotContain "Ledger"
            llms.section("Packages") shouldNotContain "shop.internal"
        }

        "オーバーロードは同じページなので1行にまとめ、Deprecated な関数には印が付く" {
            llms.section("Functions").lines() shouldContainExactly listOf(
                "- [checkout](shop/shop.api/checkout.html.md): Pays for [cart](shop/shop.api/checkout.html.md).",
                "- [pay](shop/shop.api/pay.html.md): **Deprecated** Pays for nothing.",
            )
        }

        "@featured のタグ自体はどこにも出ない" {
            llms shouldNotContain "@featured"
        }
    }

    "baseUrl を渡すと、リンクはその URL を前に付けた絶対 URL になる" {
        val llms = DokkaRunner.run(
            LlmsSources.SHOP,
            DokkaRunner.configuration(moduleName = "shop", pluginJson = """{ "baseUrl": "https://example.com/api" }"""),
        ).files["llms.txt"].shouldNotBeNull()
        llms shouldContain "- [Cart](https://example.com/api/shop/shop.api/-cart/index.html.md)"
        LINK.findAll(llms).map { it.groupValues[1] }.toList().forEach { it shouldStartWith "https://example.com/api/" }
    }

    "llms と llmsFull を切ると、どちらのファイルも出ない" {
        val files = DokkaRunner.run(
            LlmsSources.SHOP,
            DokkaRunner.configuration(moduleName = "shop", pluginJson = """{ "llms": false, "llmsFull": false }"""),
        ).files
        files["llms.txt"].shouldBeNull()
        files["llms-full.txt"].shouldBeNull()
    }

    "モジュール段 (置き換えを遅らせる run) では、モジュールの出力ルートに出て、リンクは束ねる run が仕上げる仮の形で、出力ルートからのパスを持つ" {
        val run = DokkaRunner.run(
            LlmsSources.SHOP,
            DokkaRunner.configuration(moduleName = "shop", delayTemplateSubstitution = true),
        )
        val llms = run.files["llms.txt"].shouldNotBeNull()
        val links = LINK.findAll(llms).map { it.groupValues[1] }.toList()
        withClue(run.files.keys.sorted()) {
            links.isEmpty() shouldBe false
            links.forEach { link ->
                link shouldStartWith "katachi-dokka-path:"
                link.removePrefix("katachi-dokka-path:").substringBefore('#') shouldBeIn run.files.keys
            }
        }
    }

    "モジュール段では baseUrl を使わない (置き場所を知らないので、束ねる run が絶対にする)" {
        val llms = DokkaRunner.run(
            LlmsSources.SHOP,
            DokkaRunner.configuration(
                moduleName = "shop",
                delayTemplateSubstitution = true,
                pluginJson = """{ "baseUrl": "https://example.com/api-docs/shop/" }""",
            ),
        ).files["llms.txt"].shouldNotBeNull()
        llms shouldNotContain "https://example.com"
        llms shouldContain "- [Cart](katachi-dokka-path:shop.api/-cart/index.html.md)"
    }
})

private val HEADING = Regex("^## (.+)$", RegexOption.MULTILINE)
private val LINK = Regex("""\]\(([^)]+)\)""")

/** The lines under `## [title]`, up to the next heading. */
internal fun String.section(title: String): String =
    substringAfter("\n## $title\n\n").substringBefore("\n## ").trim()
