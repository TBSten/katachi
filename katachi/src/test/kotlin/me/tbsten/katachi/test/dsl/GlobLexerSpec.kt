package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.internal.GlobLexer

class GlobLexerSpec : FreeSpec({
    "捕捉する * の数は {} と [] の中とエスケープを数えない" - {
        "素の * は 1 つずつ数える" {
            GlobLexer.capturingStarCount("*Screen*") shouldBe 2
        }

        "{} の中の * は数えない" {
            GlobLexer.capturingStarCount("*{Screen,*Route}") shouldBe 1
        }

        "[] の中の * は数えない" {
            GlobLexer.capturingStarCount("[*]*") shouldBe 1
        }

        "エスケープした * は数えない" {
            GlobLexer.capturingStarCount("""\**""") shouldBe 1
        }

        "** は 2 つの * として数える" {
            GlobLexer.capturingStarCount("**") shouldBe 2
        }
    }

    "ワイルドカードの有無" - {
        "* ? [ { のどれかがあればワイルドカードを含む" {
            listOf("*", "a?", "[a]", "{a,b}").forEach { GlobLexer.hasGlobWildcard(it) shouldBe true }
        }

        "エスケープしたものだけならワイルドカードを含まない" {
            GlobLexer.hasGlobWildcard("""\*\?\[\{""") shouldBe false
        }

        "名前を付けられないワイルドカードは ? [ { だけ" {
            GlobLexer.hasNonCapturingWildcard("*") shouldBe false
            listOf("a?", "[a]", "{a,b}").forEach { GlobLexer.hasNonCapturingWildcard(it) shouldBe true }
        }
    }

    "n 番目の捕捉する * だけを置き換える" - {
        "指定した番号だけが置き換わり、返さなかった * は残る" {
            GlobLexer.replaceCapturingStars("*_*_*") { index -> if (index == 1) "mid" else null } shouldBe "*_mid_*"
        }

        "{} の中の * は番号に数えず、置き換えもしない" {
            GlobLexer.replaceCapturingStars("{a,*}*") { "x" } shouldBe "{a,*}x"
        }

        "エスケープはそのまま残る" {
            GlobLexer.replaceCapturingStars("""\**""") { "x" } shouldBe """\*x"""
        }
    }

    "寛容モードは閉じない { や [ を例外にせず末尾まで中とみなす" - {
        "閉じない { の中の * は数えない" {
            GlobLexer.capturingStarCount("*{a,*") shouldBe 1
        }

        "閉じない [ の中の * は数えない" {
            GlobLexer.capturingStarCount("*[a*") shouldBe 1
        }

        "対応の無い } は文字として読む" {
            GlobLexer.capturingStarCount("}*") shouldBe 1
        }
    }

    "{} を直積で展開する" - {
        "選択肢ごとの文字列になる" {
            GlobLexer.expandBraces("a{b,c}d").shouldNotBeNull() shouldContainExactly listOf("abd", "acd")
        }

        "複数の {} は直積になる" {
            GlobLexer.expandBraces("{a,b}{1,2}").shouldNotBeNull() shouldContainExactly
                listOf("a1", "a2", "b1", "b2")
        }

        "空の選択肢は空文字列になる" {
            GlobLexer.expandBraces("x{,y}").shouldNotBeNull() shouldContainExactly listOf("x", "xy")
        }

        "{} の中の * ? [] とエスケープは選択肢にそのまま残る" {
            GlobLexer.expandBraces("{*a,b?,[c]}").shouldNotBeNull() shouldContainExactly
                listOf("*a", "b?", "[c]")
        }

        "{} が無ければ元の文字列 1 つ" {
            GlobLexer.expandBraces("a*b").shouldNotBeNull() shouldContainExactly listOf("a*b")
        }

        "展開が 64 通りを超えたら null" {
            GlobLexer.expandBraces("{a,b}".repeat(6)).shouldNotBeNull().size shouldBe 64
            GlobLexer.expandBraces("{a,b}".repeat(7)).shouldBeNull()
        }
    }

    "エスケープを外す" {
        GlobLexer.unescapeLiteral("""a\*b\\c""") shouldBe """a*b\c"""
        GlobLexer.unescapeLiteral("plain") shouldBe "plain"
    }
})
