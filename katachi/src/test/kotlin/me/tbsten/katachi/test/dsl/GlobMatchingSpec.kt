package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.internal.Glob

/*
 * What `Glob` answers for a path: which paths a pattern matches, what each wildcard took, and the empty levels it must refuse.
 */

class GlobMatchingSpec : FreeSpec({
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

    "matchRanges は捕捉した各ワイルドカードの位置を返す" {
        val glob = Glob.compile("*/?/{a,b}*/**")
        glob.matchRanges("x/y/az/p/q") shouldContainExactly listOf(0..0, 5..5, 7..9)
        glob.matchRanges("x/y/b/").shouldBeNull()
        Glob.compile("a/**/b").matchRanges("a/b") shouldContainExactly listOf(null)
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

    "pattern と separator が同じ Glob は等しい" {
        Glob.compile("*.kt") shouldBe Glob.compile("*.kt")
        (Glob.compile("*.kt") == Glob.compile("*.kts")) shouldBe false
    }

    "空の段を含むパスは、どのパターンにも一致しない" - {
        "段全体の * は空の段に一致しない" {
            val glob = Glob.compile("src/*/Foo.kt")
            glob.matches("src//Foo.kt") shouldBe false
            glob.match("src//Foo.kt").shouldBeNull()
            glob.matchGroups("src//Foo.kt").shouldBeNull()
            glob.matchRanges("src//Foo.kt").shouldBeNull()
            glob.matches("src/a/Foo.kt") shouldBe true
        }

        "先頭と末尾の区切り文字も空の段になる" {
            Glob.compile("*/Foo.kt").matches("/Foo.kt") shouldBe false
            Glob.compile("src/*").matches("src/") shouldBe false
            Glob.compile("**").matches("") shouldBe false
            Glob.compile("*").matches("") shouldBe false
        }

        "** は空の段をまたいで一致しない" {
            Glob.compile("src/**/Foo.kt").matches("src//Foo.kt") shouldBe false
            Glob.compile("src/**").matches("src/a//b") shouldBe false
        }

        "文字と混ざった * も空の段には一致しない" {
            Glob.compile("src/*Impl.kt/Foo.kt").matches("src//Foo.kt") shouldBe false
            Glob.compile("src/{*,x}/Foo.kt").matches("src//Foo.kt") shouldBe false
        }

        "ルートつきのモジュールパスは先頭の : だけを許す" {
            val glob = modulePath(":feature:*")
            glob.matches(":feature:home") shouldBe true
            glob.matches(":feature:") shouldBe false
            glob.matches(":feature::home") shouldBe false
            glob.matches("feature:home") shouldBe false
            modulePath(":**").matches(":") shouldBe false
        }

        "実在のパスの答えは変わらない" {
            Glob.compile("src/*/Foo.kt").matches("src/a/Foo.kt") shouldBe true
            Glob.compile("a/**/b").matches("a/b") shouldBe true
            Glob.compile("*").matches(".gitignore") shouldBe true
        }
    }
})
