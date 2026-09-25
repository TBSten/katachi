package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import java.io.File

class PageMarkdownSpec : FreeSpec({
    val run by lazy {
        DokkaRunner.run(
            LlmsSources.SHOP,
            DokkaRunner.configuration(moduleName = "shop", includes = listOf(LlmsSources.INCLUDES)),
        )
    }

    "単一モジュールの出力" - {
        "HTML ページのどれにも、同じ URL に .md を足した Markdown 版がある" {
            run.files.keys.filter { it.endsWith(".html") && it != "navigation.html" }
                .filterNot { "$it.md" in run.files } shouldContainExactly emptyList()
        }

        "Markdown 版と llms ファイルのリンクは、そのファイルからの相対で同じ run が書いたファイルを指し、ページへのリンクは .md を指す" {
            val links = run.files.filterKeys { it.endsWith(".md") || File(it).name.startsWith("llms") }
                .flatMap { (path, text) -> LINK.findAll(text).map { path to it.groupValues[1] }.toList() }
            links.filterNot { (_, link) -> link.endsWith(".md") }.map { it.second } shouldContainExactly emptyList()
            links.filterNot { (path, link) -> resolve(path, link) in run.files }
                .map { (path, link) -> "$path -> $link" } shouldContainExactly emptyList()
        }
    }

    "型のページの Markdown 版" - {
        val cart by lazy { run.files["shop/shop.api/-cart/index.html.md"].shouldNotBeNull() }

        "名前の見出し、概要、種類と置き場所の行で始まる" {
            cart shouldStartWith "# Cart\n\n" +
                "> A cart of [Item](../-item/index.html.md)s, checked out with [checkout](../checkout.html.md).\n\n" +
                "*class* in package [shop.api](../index.html.md)\n"
        }

        "シグネチャと KDoc の本文が続く" {
            cart shouldContain "```kotlin\nclass Cart(val owner: String)\n```"
            cart shouldContain "```kotlin\nval cart = Cart(\"alice\")\n```"
        }

        "メンバを種類ごとに、名前・Markdown 版へのリンク・概要で並べる" {
            cart shouldContain "## Constructors\n\n- [Cart](-cart.html.md)"
            cart shouldContain "## Functions\n\n- [add](add.html.md): Adds [item](add.html.md) to the cart."
            cart shouldContain "## Properties\n\n- [owner](owner.html.md): Who the cart belongs to."
        }
    }

    "メンバのページの Markdown 版は、オーバーロードごとにシグネチャと KDoc を並べる" {
        val checkout = run.files["shop/shop.api/checkout.html.md"].shouldNotBeNull()
        checkout shouldContain "```kotlin\nfun checkout(cart: Cart): Receipt\n```"
        checkout shouldContain "```kotlin\nfun checkout(cart: Cart, coupon: String): Receipt\n```"
        checkout shouldContain "*function* in package [shop.api](index.html.md)"
    }

    "パッケージのページの Markdown 版は、includes の説明を概要にして、型と関数を並べる" {
        val pkg = run.files["shop/shop.api/index.html.md"].shouldNotBeNull()
        pkg shouldStartWith "# shop.api\n\n> The entry points of the shop.\n\n*package* in module [shop](../../index.html.md)\n"
        pkg shouldContain "## Types\n\n- [Cart](-cart/index.html.md)"
        pkg shouldContain "- [pay](pay.html.md): **Deprecated** Pays for nothing."
    }

    "モジュールのページの Markdown 版は、Featured とパッケージを並べる" {
        val module = run.files["index.html.md"].shouldNotBeNull()
        module shouldContain "## Featured\n\n- [Cart](shop/shop.api/-cart/index.html.md): Start here to build an order."
        module shouldEndWith "## Packages\n\n" +
            "- [shop.api](shop/shop.api/index.html.md): The entry points of the shop.\n" +
            "- [shop.internal](shop/shop.internal/index.html.md)\n"
    }

    "baseUrl を渡すと、Markdown 版のリンクもその URL を前に付けた絶対 URL になる" {
        val files = DokkaRunner.run(
            LlmsSources.SHOP,
            DokkaRunner.configuration(moduleName = "shop", pluginJson = """{ "baseUrl": "https://example.com/api" }"""),
        ).files
        files["shop/shop.api/-cart/index.html.md"].shouldNotBeNull() shouldContain
            "[checkout](https://example.com/api/shop/shop.api/checkout.html.md)"
    }

    "pageMarkdown を切ると、Markdown 版は出ない" {
        val files = DokkaRunner.run(
            LlmsSources.SHOP,
            DokkaRunner.configuration(moduleName = "shop", pluginJson = """{ "pageMarkdown": false }"""),
        ).files
        files["index.html.md"].shouldBeNull()
        files.keys.filter { it.endsWith(".md") } shouldContainExactly emptyList()
    }
})

private val LINK = Regex("""\]\(([^)\s]+)\)""")

/** [link], written in the file at [path], as a path from the output root. */
internal fun resolve(path: String, link: String): String {
    val directory = path.substringBeforeLast('/', "")
    val target = link.substringBefore('#')
    return File(if (directory.isEmpty()) target else "$directory/$target").normalize().invariantSeparatorsPath
}
