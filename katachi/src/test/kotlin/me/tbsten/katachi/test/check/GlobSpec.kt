package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.Glob
import me.tbsten.katachi.check.KatachiGlobSyntaxException

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

        "* は0文字にマッチしない" {
            Glob.compile("*UseCase.kt").matches("UseCase.kt") shouldBe false
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

    "他ツールの glob のメタ文字はワイルドカードとして働かない" - {
        listOf("{" to "{a,b}.kt", "?" to "Foo?.kt", "[" to "[abc].kt").forEach { (character, pattern) ->
            "`$character` を含む pattern は KatachiGlobSyntaxException になる" {
                val exception = shouldThrow<KatachiGlobSyntaxException> { Glob.compile(pattern) }
                exception.message.shouldNotBeNull() shouldContain "only `*` and `**`"
            }
        }

        "エスケープすればリテラルとして書ける" {
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
