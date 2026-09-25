package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dokka.llms.markdown.DocTagMarkdown
import me.tbsten.katachi.dokka.llms.markdown.DocumentationMarkdown
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.doc.A
import org.jetbrains.dokka.model.doc.B
import org.jetbrains.dokka.model.doc.BlockQuote
import org.jetbrains.dokka.model.doc.Br
import org.jetbrains.dokka.model.doc.CodeBlock
import org.jetbrains.dokka.model.doc.CodeInline
import org.jetbrains.dokka.model.doc.CustomDocTag
import org.jetbrains.dokka.model.doc.CustomTagWrapper
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationLink
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.Em
import org.jetbrains.dokka.model.doc.H1
import org.jetbrains.dokka.model.doc.H2
import org.jetbrains.dokka.model.doc.H5
import org.jetbrains.dokka.model.doc.Li
import org.jetbrains.dokka.model.doc.Ol
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Param
import org.jetbrains.dokka.model.doc.Return
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.doc.Throws
import org.jetbrains.dokka.model.doc.Ul

class DocTagMarkdownSpec : FreeSpec({
    val known = DRI(packageName = "shop", classNames = "Cart")
    val markdown = DocTagMarkdown(resolve = { dri -> if (dri == known) "shop/-cart/index.html" else null })

    "インライン" - {
        "強調・コード・外部リンクを Markdown の記法に戻す" {
            val tag = P(
                listOf(
                    B(listOf(Text("bold"))),
                    Text(" "),
                    Em(listOf(Text("em"))),
                    Text(" "),
                    CodeInline(listOf(Text("code"))),
                    Text(" "),
                    A(listOf(Text("site")), params = mapOf("href" to "https://example.com")),
                ),
            )
            markdown.oneLine(tag) shouldBe "**bold** *em* `code` [site](https://example.com)"
        }

        "resolve の答えが無いリンクは unresolved に任せる" {
            val deferred = DocTagMarkdown(
                resolve = { null },
                unresolved = { label, dri -> "<$label:${dri.classNames}>" },
            )
            val tag = P(listOf(DocumentationLink(known, listOf(Text("Cart")))))
            deferred.oneLine(tag) shouldBe "<Cart:Cart>"
        }

        "宣言へのリンクは resolve の答えで結び、答えが無ければ文字だけ残す" {
            val tag = P(
                listOf(
                    DocumentationLink(known, listOf(Text("Cart"))),
                    Text(" and "),
                    DocumentationLink(DRI(packageName = "elsewhere"), listOf(Text("Other"))),
                ),
            )
            markdown.oneLine(tag) shouldBe "[Cart](shop/-cart/index.html) and Other"
        }

        "バッククォートを含むコードは二重のバッククォートで囲む" {
            markdown.oneLine(P(listOf(CodeInline(listOf(Text("a`b")))))) shouldBe "``a`b``"
        }

        "brief は最初の段落だけを1行にする" {
            val root = CustomDocTag(
                listOf(P(listOf(Text("First line"), Br, Text("continues."))), P(listOf(Text("Second.")))),
                name = "MARKDOWN_FILE",
            )
            markdown.brief(root) shouldBe "First line continues."
        }
    }

    "ブロック" - {
        "段落・見出し・コードブロック・引用を空行で区切る" {
            val root = CustomDocTag(
                listOf(
                    P(listOf(Text("Intro."))),
                    H2(listOf(Text("Usage"))),
                    CodeBlock(listOf(Text("val a = 1"), Br, Text("val b = 2")), params = mapOf("lang" to "kotlin")),
                    BlockQuote(listOf(P(listOf(Text("Quoted."))))),
                ),
                name = "MARKDOWN_FILE",
            )
            markdown.block(root) shouldBe "Intro.\n\n## Usage\n\n```kotlin\nval a = 1\nval b = 2\n```\n\n> Quoted."
        }

        "headingOffset を渡すと見出しをその段数だけ下げ、6 段より下には下げない" {
            val nested = DocTagMarkdown(resolve = { null }, headingOffset = 3)
            val root = CustomDocTag(
                listOf(H1(listOf(Text("Top"))), H2(listOf(Text("Usage"))), H5(listOf(Text("Deep")))),
                name = "MARKDOWN_FILE",
            )
            nested.block(root) shouldBe "#### Top\n\n##### Usage\n\n###### Deep"
        }

        "箇条書きと番号付きの箇条書きを書く" {
            val root = CustomDocTag(
                listOf(
                    Ul(listOf(Li(listOf(P(listOf(Text("one"))))), Li(listOf(Text("two"))))),
                    Ol(listOf(Li(listOf(Text("first"))), Li(listOf(Text("second"))))),
                ),
                name = "MARKDOWN_FILE",
            )
            markdown.block(root) shouldBe "- one\n- two\n\n1. first\n2. second"
        }
    }

    "KDoc 全体" - {
        "本文の後にブロックタグを箇条書きにし、@featured は出さない" {
            val node = DocumentationNode(
                listOf(
                    Description(CustomDocTag(listOf(P(listOf(Text("Adds an item.")))), name = "MARKDOWN_FILE")),
                    Param(P(listOf(Text("What to add."))), "item"),
                    Return(P(listOf(Text("The cart.")))),
                    Throws(P(listOf(Text("When closed."))), "IllegalStateException", null),
                    CustomTagWrapper(P(listOf(Text("Start here."))), "featured"),
                ),
            )
            DocumentationMarkdown(markdown).of(node) shouldBe
                "Adds an item.\n\n" +
                "- **Parameters**\n" +
                "  - `item`: What to add.\n" +
                "- **Returns**: The cart.\n" +
                "- **Throws** `IllegalStateException`: When closed."
        }

        "KDoc が無ければ空文字" {
            DocumentationMarkdown(markdown).of(null) shouldBe ""
        }
    }
})
