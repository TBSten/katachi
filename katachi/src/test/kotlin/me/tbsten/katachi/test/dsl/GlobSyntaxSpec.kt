package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.internal.Glob

/*
 * How `Glob` reads and translates the syntax: `?`, `[...]`, `{...}`, escapes, and what the pattern reports about itself.
 */

class GlobSyntaxSpec : FreeSpec({
    fun modulePath(pattern: String) = Glob.compile(pattern, Glob.MODULE_SEPARATOR)

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

    "hasWildcard はワイルドカードの有無を返す" {
        Glob.compile("build.gradle.kts").hasWildcard shouldBe false
        Glob.compile("*.kt").hasWildcard shouldBe true
        modulePath(":feature:**").hasWildcard shouldBe true
    }
})
