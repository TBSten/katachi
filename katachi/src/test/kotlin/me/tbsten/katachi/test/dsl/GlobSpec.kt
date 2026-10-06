package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.internal.GlobProblem
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException

class GlobSpec : FreeSpec({
    fun modulePath(pattern: String) = Glob.compile(pattern, Glob.MODULE_SEPARATOR)

    "* はちょうど1階層にマッチする" - {
        "\":feature:*\" は :feature:home にマッチする" {
            modulePath(":feature:*").matches(":feature:home") shouldBe true
        }

        "\":feature:*\" は :feature 自体にはマッチしない" {
            modulePath(":feature:*").matches(":feature") shouldBe false
        }

        "\":feature:*\" は :feature:home:impl にはマッチしない" {
            modulePath(":feature:*").matches(":feature:home:impl") shouldBe false
        }

        "\":core:*:*\" は2階層をそれぞれ捕捉する" {
            modulePath(":core:*:*").match(":core:data:remote")
                .shouldNotBeNull()
                .wildcards shouldContainExactly listOf("data", "remote")
        }
    }

    "** は0階層以上にマッチする" - {
        "\":feature:**\" は :feature 自体にマッチし、捕捉は空になる" {
            modulePath(":feature:**").match(":feature")
                .shouldNotBeNull()
                .wildcards shouldContainExactly emptyList()
        }

        "\":feature:**\" は :feature:home にマッチする" {
            modulePath(":feature:**").match(":feature:home")
                .shouldNotBeNull()
                .wildcards shouldContainExactly listOf("home")
        }

        "\":feature:**\" は :feature:home:impl にマッチし、捕捉は階層ごとに平坦化される" {
            modulePath(":feature:**").match(":feature:home:impl")
                .shouldNotBeNull()
                .wildcards shouldContainExactly listOf("home", "impl")
        }

        "\":core:*:**\" では手前の * の添字が固定され、** の分は末尾に並ぶ" {
            modulePath(":core:*:**").match(":core:x:a:b")
                .shouldNotBeNull()
                .wildcards shouldContainExactly listOf("x", "a", "b")
        }

        "ファイルパスの途中の ** は0階層にもマッチする" {
            val glob = Glob.compile("src/**/useCase")
            glob.matches("src/useCase") shouldBe true
            glob.matches("src/commonMain/useCase") shouldBe true
            glob.matches("src/commonMain/kotlin/useCase") shouldBe true
        }

        "先頭の ** は0階層以上にマッチする" {
            val glob = Glob.compile("**/build.gradle.kts")
            glob.matches("build.gradle.kts") shouldBe true
            glob.matches("core/data/build.gradle.kts") shouldBe true
        }
    }

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

    "ファイル名の中の * は名前の一部にマッチし、ディレクトリ境界を越えない" - {
        "\"*UseCase.kt\" は GetUserUseCase.kt にマッチする" {
            Glob.compile("*UseCase.kt").match("GetUserUseCase.kt")
                .shouldNotBeNull()
                .wildcards shouldContainExactly listOf("GetUser")
        }

        "\"*UseCase.kt\" はサブディレクトリ内の同名ファイルにはマッチしない" {
            Glob.compile("*UseCase.kt").matches("useCase/GetUserUseCase.kt") shouldBe false
        }

        "文字と混ざった * は空にも当たり、値は空文字列になる" {
            Glob.compile("app_icon*.xml").match("app_icon.xml")
                .shouldNotBeNull()
                .wildcards shouldContainExactly listOf("")
            Glob.compile("DropShadow*").match("DropShadow")
                .shouldNotBeNull()
                .wildcards shouldContainExactly listOf("")
            Glob.compile("*UseCase.kt").matches("UseCase.kt") shouldBe true
        }

        "段全体の * は実在のパスでは空の段を作らない" {
            Glob.compile("feature/*").matches("feature") shouldBe false
            modulePath(":feature:*").matches(":feature") shouldBe false
        }

        "* は . を含む名前にもマッチするが、拡張子の . は区切りではない" {
            Glob.compile("*.kt").match("Foo.android.kt")
                .shouldNotBeNull()
                .wildcards shouldContainExactly listOf("Foo.android")
        }

        "* はドットファイルにもマッチする" {
            Glob.compile("*").matches(".gitignore") shouldBe true
        }
    }

    "モジュールパスとファイルパスで * / ** の意味が同じになる" {
        val modules = modulePath(":a:*:c")
        val paths = Glob.compile("a/*/c")
        modules.matches(":a:b:c") shouldBe paths.matches("a/b/c")
        modules.matches(":a:c") shouldBe paths.matches("a/c")
        modules.matches(":a:b:b2:c") shouldBe paths.matches("a/b/b2/c")

        val recursiveModules = modulePath(":a:**")
        val recursivePaths = Glob.compile("a/**")
        recursiveModules.match(":a:b:c").shouldNotBeNull().wildcards shouldContainExactly
            recursivePaths.match("a/b/c").shouldNotBeNull().wildcards
    }

    "? は区切り文字以外の 1 文字に当たる" - {
        "1 文字に当たる" {
            Glob.compile("Foo?.kt").matches("FooA.kt") shouldBe true
        }

        "0 文字にも 2 文字にも当たらない" {
            Glob.compile("Foo?.kt").matches("Foo.kt") shouldBe false
            Glob.compile("Foo?.kt").matches("FooAB.kt") shouldBe false
        }

        "区切り文字には当たらない" {
            Glob.compile("Foo?.kt").matches("Foo/.kt") shouldBe false
        }

        "値にならない" {
            Glob.compile("*?.kt").match("AB.kt").shouldNotBeNull().wildcards shouldContainExactly listOf("A")
        }
    }

    "[...] は列挙か範囲のどれか 1 文字に当たる" - {
        "列挙と範囲のどれか 1 文字に当たる" {
            val glob = Glob.compile("[abx-z].kt")
            listOf("a", "b", "x", "y", "z").forEach { glob.matches("$it.kt") shouldBe true }
            listOf("c", "w", "ab").forEach { glob.matches("$it.kt") shouldBe false }
        }

        "! と ^ は否定になる" {
            listOf("[!abc].kt", "[^abc].kt").forEach { pattern ->
                val glob = Glob.compile(pattern)
                glob.matches("d.kt") shouldBe true
                glob.matches("a.kt") shouldBe false
            }
        }

        "否定のクラスも区切り文字には当たらない" {
            Glob.compile("a[!x]b").matches("a/b") shouldBe false
            Glob.compile("a[!x]b").matches("aYb") shouldBe true
        }

        "範囲が区切り文字をまたいでも区切り文字には当たらない" {
            Glob.compile("a[.-9]b").matches("a/b") shouldBe false
            Glob.compile("a[.-9]b").matches("a5b") shouldBe true
        }

        "- は先頭か末尾ならリテラル" {
            Glob.compile("[-a].kt").matches("-.kt") shouldBe true
            Glob.compile("[a-].kt").matches("-.kt") shouldBe true
            Glob.compile("[a-].kt").matches("b.kt") shouldBe false
        }

        "! と ^ は先頭以外ならリテラル" {
            Glob.compile("[a!^].kt").matches("!.kt") shouldBe true
            Glob.compile("[a!^].kt").matches("^.kt") shouldBe true
        }

        "クラスの中の * ? { はリテラル" {
            val glob = Glob.compile("[*?{].kt")
            listOf("*", "?", "{").forEach { glob.matches("$it.kt") shouldBe true }
            glob.matches("a.kt") shouldBe false
            glob.hasWildcard shouldBe true
        }

        "クラスの中の \\] はリテラルの ]" {
            val glob = Glob.compile("""[\]a].kt""")
            glob.matches("].kt") shouldBe true
            glob.matches("a.kt") shouldBe true
        }

        "値にならない" {
            Glob.compile("*[a-c]").match("xb").shouldNotBeNull().wildcards shouldContainExactly listOf("x")
        }

        "[a-z] は大文字に当たらない" {
            Glob.compile("[a-z].kt").matches("A.kt") shouldBe false
        }
    }

    "{...} は選択肢のどれか 1 つに当たる" - {
        "選択肢のどれかに当たる" {
            val glob = Glob.compile("*.{kt,kts}")
            glob.matches("Foo.kt") shouldBe true
            glob.matches("build.kts") shouldBe true
            glob.matches("Foo.java") shouldBe false
        }

        "空の選択肢は空に当たる" {
            val glob = Glob.compile("build.gradle{,.kts}")
            glob.matches("build.gradle") shouldBe true
            glob.matches("build.gradle.kts") shouldBe true
            glob.matches("build.gradle.x") shouldBe false
        }

        "{} の中の * は値にならない" {
            Glob.compile("*{Screen,*Route}").match("HomeFooRoute")
                .shouldNotBeNull().wildcards shouldContainExactly listOf("HomeFoo")
        }

        "{} の中に ? と [..] を書ける" {
            val glob = Glob.compile("{a?,[xy]z}.kt")
            glob.matches("ab.kt") shouldBe true
            glob.matches("xz.kt") shouldBe true
            glob.matches("az.kt") shouldBe true
            glob.matches("bz.kt") shouldBe false
        }

        "{} の外の , はリテラル" {
            Glob.compile("a,b.kt").matches("a,b.kt") shouldBe true
        }

        "{} の中でも区切り文字は越えない" {
            Glob.compile("{*}.kt").matches("a/b.kt") shouldBe false
        }

        "モジュールパスでも同じ構文が使える" {
            val glob = modulePath(":feature:{home,settings}")
            glob.matches(":feature:home") shouldBe true
            glob.matches(":feature:other") shouldBe false
        }
    }

    "エスケープすればメタ文字をリテラルとして書ける" - {
        "\\[ \\] はリテラルにマッチする" {
            val glob = Glob.compile("""\[abc\].kt""")
            glob.matches("[abc].kt") shouldBe true
            glob.matches("a.kt") shouldBe false
        }

        "\\* はリテラルの * にマッチし、ワイルドカードにならない" {
            val glob = Glob.compile("""\*.kt""")
            glob.matches("*.kt") shouldBe true
            glob.matches("Foo.kt") shouldBe false
            glob.hasWildcard shouldBe false
        }

        "\\? \\[ \\{ はリテラルで hasWildcard を立てない" {
            listOf("""a\?""", """a\[""", """a\{""").forEach { Glob.compile(it).hasWildcard shouldBe false }
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

    "? [] {} の意味はモジュールパスとファイルパスで同じになる" {
        val modules = modulePath(":a:[xy]?:{b,c}")
        val paths = Glob.compile("a/[xy]?/{b,c}")
        listOf("xa" to "b", "ya" to "c", "za" to "b", "xa" to "d").forEach { (middle, last) ->
            modules.matches(":a:$middle:$last") shouldBe paths.matches("a/$middle/$last")
        }
    }

    "? [ { のどれかを含むパターンは hasWildcard が立つ" {
        listOf("Foo?.kt", "[ab].kt", "{a,b}.kt").forEach { Glob.compile(it).hasWildcard shouldBe true }
    }

    "? [ { を含む段で literalPrefix が止まる" {
        Glob.compile("src/{main,test}/kotlin").literalPrefix shouldContainExactly listOf("src")
        Glob.compile("src/ma?n/kotlin").literalPrefix shouldContainExactly listOf("src")
        Glob.compile("src/[mt]ain/kotlin").literalPrefix shouldContainExactly listOf("src")
        Glob.compile("src/main/kotlin").literalPrefix shouldContainExactly listOf("src", "main", "kotlin")
    }

    "matchRanges は捕捉した各ワイルドカードの位置を返す" {
        val glob = Glob.compile("*/?/{a,b}*/**")
        glob.matchRanges("x/y/az/p/q") shouldContainExactly listOf(0..0, 5..5, 7..9)
        glob.matchRanges("x/y/b/").shouldBeNull()
        Glob.compile("a/**/b").matchRanges("a/b") shouldContainExactly listOf(null)
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

    "照合は常に大文字小文字を区別する" {
        Glob.compile("*UseCase.kt").matches("GetUserusecase.kt") shouldBe false
        Glob.compile("README.md").matches("readme.md") shouldBe false
    }

    "正規表現のメタ文字はリテラルとして扱われる" {
        val glob = Glob.compile("build.gradle.kts")
        glob.matches("build.gradle.kts") shouldBe true
        glob.matches("buildXgradle.kts") shouldBe false
    }

    "マッチしなかったときは match が null を返す" {
        Glob.compile("*UseCase.kt").match("Repository.kt").shouldBeNull()
    }

    "hasWildcard はワイルドカードの有無を返す" {
        Glob.compile("build.gradle.kts").hasWildcard shouldBe false
        Glob.compile("*.kt").hasWildcard shouldBe true
        modulePath(":feature:**").hasWildcard shouldBe true
    }

    "pattern と separator が同じ Glob は等しい" {
        Glob.compile("*.kt") shouldBe Glob.compile("*.kt")
        (Glob.compile("*.kt") == Glob.compile("*.kts")) shouldBe false
    }
})
