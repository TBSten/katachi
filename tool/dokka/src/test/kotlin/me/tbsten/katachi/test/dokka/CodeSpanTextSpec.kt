package me.tbsten.katachi.test.dokka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dokka.internal.markdown.CodeSpanText

class CodeSpanTextSpec : FreeSpec({
    "Dokka が読んだ文字から、区切りの残りと詰め物の空白を外す" - {
        // Left: what Dokka's parser hands over. Right: the code the author wrote.
        listOf(
            "code" to "code",
            "a`b" to "a`b",
            "` `val` `" to "`val`",
            "` allow(`fun`, \"*\") `" to "allow(`fun`, \"*\")",
            "`plain`" to "plain",
            "`` a```b ``" to "a```b",
            "` ` `" to "`",
            "`" to "`",
            "``" to "``",
        ).forEach { (parsed, written) ->
            "$parsed は $written" { CodeSpanText.of(parsed) shouldBe written }
        }
    }

    "Markdown に戻すときは、中のバッククォートより長い区切りで囲む" - {
        listOf(
            "code" to "`code`",
            "a`b" to "``a`b``",
            "`val`" to "`` `val` ``",
            "a``b" to "```a``b```",
        ).forEach { (text, span) ->
            "$text は $span" { CodeSpanText.span(text) shouldBe span }
        }
    }

    "二重のバッククォートで書いた KDoc" - {
        val run by lazy { DokkaRunner.run(SOURCE) }

        "HTML のページには書いたとおりのコードが出る" {
            val pages = run.files.filterKeys { it.endsWith(".html") }.values.joinToString("\n")
            pages shouldContain "<code class=\"lang-kotlin\">`val`</code>"
            pages shouldContain "<code class=\"lang-kotlin\">allow(`val`, &quot;*&quot;)</code>"
            pages shouldNotContain "` `val` `"
        }

        "Markdown のページと llms のファイルには、読み直せるコード表記で出る" {
            val markdown = run.files.filterKeys { it.endsWith(".md") || it.endsWith(".txt") }
            withClue(markdown.keys.sorted()) {
                markdown.values.filter { "` `val` ` `" in it } shouldContainExactly emptyList()
                val page = markdown.entries.single { it.key.endsWith("-kind/index.html.md") }.value
                page shouldContain "The kind of a property: `` `val` `` here."
                page shouldContain "Written as ``allow(`val`, \"*\")``."
            }
        }
    }
})

private val SOURCE: String = """
    |/src/main/kotlin/sample/Kind.kt
    |package sample
    |
    |/**
    | * The kind of a property: `` `val` `` here.
    | *
    | * Written as `` allow(`val`, "*") ``.
    | */
    |public class Kind
""".trimMargin()
