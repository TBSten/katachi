package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind
import me.tbsten.katachi.konsist.declarations.internal.renderRule

/** The receiver of an extension, written between the kind and the name of a line: how it matches, what refuses it, how it is shown. */

private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

class KtFileContentReceiverSpec : FreeSpec({
    "fun の行" - {
        "required(`fun`, \"Hoge\", \"*\")() は Hoge の拡張関数が 1 つあれば通る" {
            judged(kt("class Hoge\nfun Hoge.bar() {}")) {
                `package`("**")
                allow(`class`, "Hoge")
                required(`fun`, "Hoge", "*")()
            }.shouldBeEmpty()
        }

        "Hoge の拡張関数が無ければ、レシーバー付きの形で足りないと出す" {
            judged(kt("fun bar() {}")) {
                `package`("**")
                allow(`fun`, "bar")
                required(`fun`, "Hoge", "*")()
            } shouldBe listOf("* | Missing: fun Hoge.*()")
        }

        "名前が当たってレシーバーが違えば、違いにレシーバーを出す" {
            judged(kt("fun Other.bar() {}")) {
                `package`("**")
                required(`fun`, "Hoge", "bar")
            } shouldBe listOf(
                "bar (line 3) | Not allowed: fun Other.bar() | Expected: fun Hoge.bar(...) | " +
                    "Differs in: receiver (is Other, expected Hoge)",
            )
        }

        "拡張関数でない関数は、レシーバーを書いた行に当たらない" {
            judged(kt("fun bar() {}")) {
                `package`("**")
                required(`fun`, "Hoge", "bar")
            } shouldBe listOf(
                "bar (line 3) | Not allowed: fun bar() | Expected: fun Hoge.bar(...) | " +
                    "Differs in: receiver (none, expected Hoge)",
            )
        }

        "レシーバー * は拡張関数ならどれでも当たり、拡張関数でない関数には当たらない" {
            judged(kt("fun String.a() {}\nfun Int.b() {}\nfun c() {}")) {
                `package`("**")
                allow(`fun`, "*", "*")
            } shouldBe listOf("c (line 5) | Not allowed: fun c()")
        }

        "レシーバーを書かない行は今まで通りレシーバーを見ない" {
            judged(kt("fun String.a() {}\nfun a() {}")) {
                `package`("**")
                allow(`fun`, "a")
            }.shouldBeEmpty()
        }

        "修飾子とアノテーションの後ろにも書ける" {
            judged(kt("annotation class Marker\n@Marker\ninternal fun String.a() {}")) {
                `package`("**")
                allow(annotation, `class`, "Marker")
                required(annotated("Marker"), internal, `fun`, "String", "a")
            }.shouldBeEmpty()
        }

        "not.allow(`fun`, \"Context\", \"*\") は Context の拡張関数だけを禁止する" {
            judged(kt("class Context\nfun Context.a() {}\nfun String.b() {}")) {
                `package`("**")
                allow(`class`, "Context")
                not.allow(`fun`, "Context", "*")
                allow(`fun`, "*")
            } shouldBe listOf(
                "a (line 4) | Not allowed: fun Context.a() | Prohibited by: not fun Context.*(...)",
            )
        }

        "クラスの { } の中のメンバ拡張関数にも書ける" {
            judged(kt("class Sample {\n    fun String.a() {}\n}")) {
                `package`("**")
                required(`class`, "Sample") { required(`fun`, "String", "a")() }
            }.shouldBeEmpty()
        }
    }

    "val / var の行" - {
        "required(`val`, \"Hoge\", \"*\", \"*\") は Hoge の拡張プロパティが 1 つあれば通る" {
            judged(kt("class Hoge\nval Hoge.size: Int get() = 0")) {
                `package`("**")
                allow(`class`, "Hoge")
                required(`val`, "Hoge", "*", "*")
            }.shouldBeEmpty()
        }

        "レシーバー・名前・型の 3 つで当てる。型は拡張プロパティでも書いた型を見る" {
            judged(kt("val String.isBlankish: Boolean get() = true\nval String.length2: Int get() = 0")) {
                `package`("**")
                allow(`val`, "String", "is*", "Boolean")
            } shouldBe listOf("length2 (line 4) | Not allowed: val String.length2: Int")
        }

        "名前が当たってレシーバーが違えば、違いにレシーバーを出す" {
            judged(kt("val Int.size: Int get() = 0")) {
                `package`("**")
                required(`val`, "String", "size", "Int")
            } shouldBe listOf(
                "size (line 3) | Not allowed: val Int.size: Int | Expected: val String.size: Int | " +
                    "Differs in: receiver (is Int, expected String)",
            )
        }

        "拡張でないプロパティは、レシーバーを書いた行に当たらない" {
            judged(kt("val size: Int = 0")) {
                `package`("**")
                required(`val`, "String", "size", "*")
            } shouldBe listOf(
                "size (line 3) | Not allowed: val size: Int | Expected: val String.size: * | " +
                    "Differs in: receiver (none, expected String)",
            )
        }

        "var と `val` or `var` にも書ける" {
            judged(kt("var String.a: Int\n    get() = 0\n    set(value) {}\nval String.b: Int get() = 0")) {
                `package`("**")
                required(`var`, "String", "a", "Int")
                required(`val` or `var`, "String", "b", "Int")
            }.shouldBeEmpty()
        }

        "型の * は、型を書いていない拡張プロパティには当たらない（拡張でないプロパティと同じ）" {
            judged(kt("val String.a get() = 0")) {
                `package`("**")
                allow(`val`, "String", "*", "*")
            } shouldBe listOf("a (line 3) | Not allowed: val String.a")
        }

        "レシーバーを書かない行は今まで通りレシーバーを見ない" {
            judged(kt("val String.a: Int get() = 0\nval a: Int = 0")) {
                `package`("**")
                allow(`val`, "a", "Int")
            }.shouldBeEmpty()
        }

        "not.allow(`val`, \"Context\", \"*\", \"*\") は Context の拡張プロパティを禁止する" {
            judged(kt("class Context\nval Context.a: Int get() = 0\nval b: Int = 0")) {
                `package`("**")
                allow(`class`, "Context")
                not.allow(`val`, "Context", "*", "*")
                allow(`val`, "*")
            } shouldBe listOf(
                "a (line 4) | Not allowed: val Context.a: Int | Prohibited by: not val Context.*: *",
            )
        }

        "モデルにレシーバーと型が入る" {
            dumpOf { required(public, `val`, "Hoge", "*", "Int") } shouldBe "required val{public} * receiver=Hoge type=Int"
        }
    }

    "型の読み方" - {
        "型引数は * で受けられる" {
            judged(kt("fun List<Int>.a() {}")) {
                `package`("**")
                required(`fun`, "List<*>", "a")
            }.shouldBeEmpty()
        }

        "? は nullable で、String は String? に当たらない" {
            judged(kt("fun String?.a() {}\nfun String.b() {}\nfun String?.c() {}")) {
                `package`("**")
                allow(`fun`, "String?", "a")
                allow(`fun`, "String", "*")
            } shouldBe listOf("c (line 5) | Not allowed: fun String?.c()")
        }

        "パッケージ付きで書かれたレシーバーもパッケージを見ずに当てる" {
            judged(kt("fun com.example.Hoge.a() {}")) {
                `package`("**")
                required(`fun`, "Hoge", "a")
            }.shouldBeEmpty()
        }

        "関数型のレシーバーは括弧があっても無くても同じに読む" {
            judged(kt("fun (() -> Unit).a() {}\nfun (() -> Unit).b() {}")) {
                `package`("**")
                required(`fun`, "() -> Unit", "a")
                required(`fun`, "(() -> Unit)", "b")
            }.shouldBeEmpty()
        }
    }

    "書き方の誤り" - {
        "読めないレシーバーのパターンは Receiver" {
            shouldThrow<KatachiKtFileContentPatternException> {
                rulesOf { allow(`fun`, "List<[", "a") }
            }.what shouldBe KtFileContentPatternKind.Receiver
        }

        "パッケージに * を使ったレシーバーは全ての型に当たるので断る" {
            shouldThrow<KatachiKtFileContentPatternException> {
                rulesOf { allow(`fun`, "com.example.*", "a") }
            }.what shouldBe KtFileContentPatternKind.Receiver
        }
    }

    "直し方" - {
        "拡張の宣言を許す行は、レシーバーを付けて出し、貼ればその宣言が通る。型を書いていない拡張プロパティはレシーバー無しで出す" {
            val source = kt("class Sample\nfun List<Int>.a() {}\nval String.b: Int get() = 0\nval String.c get() = 0")
            fixLinesOf(source) {
                `package`("**")
                required(`class`, fileName)
            }.filter { "allow(`" in it }.map { it.substringAfterLast(" / ") } shouldBe listOf(
                "allow(`fun`, \"List<Int>\", \"a\")",
                "allow(`val`, \"String\", \"b\", \"Int\")",
                "allow(`val`, \"c\")",
            )
            judged(source) {
                `package`("**")
                required(`class`, fileName)
                allow(`fun`, "List<Int>", "a")
                allow(`val`, "String", "b", "Int")
                allow(`val`, "c")
            }.shouldBeEmpty()
        }
    }

    "表示" - {
        "モデルにレシーバーが入る" {
            dumpOf { required(`fun`, "Hoge", "*")() } shouldBe "required fun{} * receiver=Hoge params=()"
        }

        "行は Kotlin の書き方でレシーバーを名前の前に出す。関数型は括弧に入れる" {
            renderRule(rulesOf { allow(public, `fun`, "Hoge", "to*")().returns("Unit") }.firstDeclaration) shouldBe
                "public fun Hoge.to*(): Unit"
            renderRule(rulesOf { allow(`fun`, "() -> Unit", "a") }.firstDeclaration) shouldBe "fun (() -> Unit).a(...)"
        }
    }
})
