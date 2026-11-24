package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.internal.GlobProblem
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException

/*
 * What `Glob.compile` refuses, and the problem each refusal carries.
 */

class GlobErrorSpec : FreeSpec({
    fun modulePath(pattern: String) = Glob.compile(pattern, Glob.MODULE_SEPARATOR)

    "モジュールパスの ** は末尾に1つだけ書ける" - {
        "末尾の ** は受け付けられる" {
            modulePath(":feature:**").requireAtMostOneTrailingDoubleStar()
        }

        "\":feature:**:impl\" は拒否される" {
            val exception = shouldThrow<KatachiGlobSyntaxException> {
                modulePath(":feature:**:impl").requireAtMostOneTrailingDoubleStar()
            }
            exception.message.shouldNotBeNull() shouldContain "last segment"
        }

        "** を2つ書くと拒否される" {
            shouldThrow<KatachiGlobSyntaxException> {
                modulePath(":feature:**:**").requireAtMostOneTrailingDoubleStar()
            }
        }

        "ワイルドカードを含まないモジュールパスは受け付けられる" {
            modulePath(":core:data").requireAtMostOneTrailingDoubleStar()
        }
    }

    "宣言時の誤りは problem の型で区別できる" - {
        fun problemOf(pattern: String, separator: Char = '/') =
            shouldThrow<KatachiGlobSyntaxException> { Glob.compile(pattern, separator) }.problem

        "閉じない [" {
            problemOf("a[bc/d").shouldBeInstanceOf<GlobProblem.SeparatorInBrackets>()
            problemOf("a[bc").shouldBeInstanceOf<GlobProblem.UnclosedBracket>().segment shouldBe "a[bc"
        }

        "閉じない {" {
            problemOf("x/a{b,c").shouldBeInstanceOf<GlobProblem.UnclosedBrace>().segment shouldBe "a{b,c"
        }

        "対応の無い ] と }" {
            problemOf("a]").shouldBeInstanceOf<GlobProblem.UnmatchedClosing>().character shouldBe ']'
            problemOf("a}").shouldBeInstanceOf<GlobProblem.UnmatchedClosing>().character shouldBe '}'
        }

        "空のクラス" {
            problemOf("[]").shouldBeInstanceOf<GlobProblem.EmptyCharacterClass>()
            problemOf("[!]").shouldBeInstanceOf<GlobProblem.EmptyCharacterClass>()
        }

        "逆順の範囲" {
            val problem = problemOf("[z-a]").shouldBeInstanceOf<GlobProblem.ReversedRange>()
            problem.from shouldBe 'z'
            problem.to shouldBe 'a'
        }

        "クラスの中の区切り文字" {
            problemOf("[a/b]").shouldBeInstanceOf<GlobProblem.SeparatorInBrackets>().separator shouldBe '/'
        }

        "選択肢が 1 つも無い {}" {
            problemOf("{}").shouldBeInstanceOf<GlobProblem.EmptyBraces>()
        }

        "入れ子の {" {
            problemOf("{a,{b}}").shouldBeInstanceOf<GlobProblem.NestedBraces>()
        }

        "{} の中の **" {
            problemOf("{**}").shouldBeInstanceOf<GlobProblem.DoubleStarInBraces>()
        }

        "{a/b,c} は閉じない { ではなく区切り文字の誤りになる" {
            problemOf("{a/b,c}").shouldBeInstanceOf<GlobProblem.SeparatorInBraces>().separator shouldBe '/'
        }

        "モジュールパスの {a:b} は : の誤りになる" {
            problemOf(":x:{a:b}", ':').shouldBeInstanceOf<GlobProblem.SeparatorInBraces>().separator shouldBe ':'
        }

        "文面は ASCII だけで、直し方を含む" {
            listOf("a[bc", "a{b", "a]", "[]", "[z-a]", "[a/b]", "{}", "{a,{b}}", "{**}", "{a/b}").forEach { pattern ->
                val message = shouldThrow<KatachiGlobSyntaxException> { Glob.compile(pattern) }.message.shouldNotBeNull()
                message.all { it.code < 128 } shouldBe true
                message shouldContain "`$pattern`"
            }
        }
    }

    "壊れた pattern は compile 時に落ちる" - {
        "空文字列" {
            shouldThrow<KatachiGlobSyntaxException> { Glob.compile("") }
        }

        "区切り文字が2つ続く" {
            shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a//b") }
        }

        "区切り文字で終わる" {
            shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a/b/") }
        }

        "** がセグメントの一部になっている" {
            val exception = shouldThrow<KatachiGlobSyntaxException> { Glob.compile("a**b") }
            exception.message.shouldNotBeNull() shouldContain "whole segment"
        }

        "セグメントの末尾の \\" {
            shouldThrow<KatachiGlobSyntaxException> { Glob.compile("""a\""") }
        }

        "メタ文字でないものをエスケープしている" {
            shouldThrow<KatachiGlobSyntaxException> { Glob.compile("""\a.kt""") }
        }
    }
})
