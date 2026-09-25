package me.tbsten.katachi.test.dokka

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class SummariesSpec : FreeSpec({
    val run by lazy { DokkaRunner.run(SOURCE, DokkaRunner.configuration(moduleName = "notes")) }
    val summaries by lazy {
        run.files["notes/notes/llms.txt"].shouldNotBeNull().lines()
            .mapNotNull { ITEM.matchEntire(it) }
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    "llms.txt の各行の概要" - {
        "@llm があれば、KDoc の最初の段落より優先してその文になる" {
            summaries["Tagged"] shouldBe "What an agent should know first."
        }

        "@llm が無ければ KDoc の最初の段落から取り、最初の文が長ければ1文で切る" {
            summaries["LongFirst"] shouldBe
                "This first sentence is long enough on its own to be the whole summary of it."
        }

        "最初の文が短ければ2文目まで入れ、3文目は入れない" {
            summaries["Short"] shouldBe "Short one. Second sentence here."
        }

        "インラインコードの中のピリオドでは切らず、コードは閉じたまま残る" {
            summaries["Role"] shouldBe "Declares a role: `\"UseCase\" { title = \"Use case. Really.\" }` in the scope."
        }

        "リンクは途中で切らず、Markdown 版へのリンクのまま残る" {
            summaries["Linked"] shouldBe "Starts at [Short](-short/index.html.md). Then [LongFirst](-long-first/index.html.md) runs."
        }

        "全角の句点でも文を区切る" {
            summaries["Japanese"] shouldBe "日本語の説明です。二文目です。"
        }

        "KDoc が無ければ概要は空で、区切りの : も付かない" {
            run.files["notes/notes/llms.txt"].shouldNotBeNull().lines() shouldContainLine "- [Plain](-plain/index.html.md)"
        }
    }

    "@llm の文は HTML のページには出ず、Markdown 版の本文にも KDoc のタグとしては出ない" {
        run.files["notes/notes/-tagged/index.html"].shouldNotBeNull() shouldNotContain "What an agent should know first"
        val markdown = run.files["notes/notes/-tagged/index.html.md"].shouldNotBeNull()
        markdown.lines().first { it.startsWith("> ") } shouldBe "> What an agent should know first."
        markdown shouldNotContain "**llm**"
    }
})

private infix fun List<String>.shouldContainLine(line: String) = (line in this) shouldBe true

private val ITEM = Regex("""- \[([^\]]+)\]\([^)]+\): (.+)""")

private val SOURCE: String = """
    |/src/main/kotlin/notes/Notes.kt
    |package notes
    |
    |/**
    | * A long description that is not what an agent reads first.
    | *
    | * @llm What an agent should know first.
    | */
    |public class Tagged
    |
    |/** This first sentence is long enough on its own to be the whole summary of it. The second is dropped. */
    |public class LongFirst
    |
    |/** Short one. Second sentence here. Third sentence is dropped. */
    |public class Short
    |
    |/**
    | * Declares a role: `"UseCase" { title = "Use case. Really." }` in the scope. Then more text
    | * follows here. And more.
    | */
    |public class Role
    |
    |/** Starts at [Short]. Then [LongFirst] runs. Not this one. */
    |public class Linked
    |
    |/** 日本語の説明です。二文目です。三文目は出ません。 */
    |public class Japanese
    |
    |public class Plain
""".trimMargin()
