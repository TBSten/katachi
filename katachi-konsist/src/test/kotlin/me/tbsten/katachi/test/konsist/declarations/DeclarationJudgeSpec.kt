package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * The judging of declarations and members (impl-spec 8.1): one source judged against one block,
 * each failure written as `<declaration> (line <n>) | <label>: <text> | ...`.
 */

/** [body] as a file of package `com.example`: its first line is line 3. */
private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

class DeclarationJudgeSpec : FreeSpec({
    "判定の順" - {
        "禁止は許可より強い" {
            judged(kt("class FooImpl")) {
                `class`("*")
                not.`class`("*Impl")
            } shouldBe listOf("FooImpl (line 3) | Not allowed: class FooImpl | Prohibited by: not class *Impl")
        }

        "except で禁止から外したものは許可の行で通る" {
            judged(kt("class DefaultImpl\nclass FooImpl")) {
                `class`("*")
                not.`class`("*Impl").except("DefaultImpl")
            } shouldBe listOf("FooImpl (line 4) | Not allowed: class FooImpl | Prohibited by: not class *Impl except DefaultImpl")
        }

        "どの行にも当たらなければ anythingElse で通る" {
            judged(kt("class Sample\nprivate fun helper() {}\nfun leak() {}")) {
                `class`(fileName)
                private.anythingElse()
            } shouldBe listOf("leak (line 5) | Not allowed: fun leak()")
        }

        "禁止の行だけを書いた種類は、禁止に当たらないものも違反" {
            judged(kt("class Sample")) {
                not.`class`("*Impl")
            } shouldBe listOf("Sample (line 3) | Not allowed: class Sample")
        }
    }

    "修飾子とアノテーション" - {
        "public は何も書いていない宣言に当たり、private には当たらない" {
            judged(kt("class A\nprivate class B")) {
                public.`class`("*")
            } shouldBe listOf(
                "B (line 4) | Not allowed: private class B | Expected: public class * | Differs in: modifiers (missing: public)",
            )
        }

        "書かなかった修飾子は問わない" {
            judged(kt("data class Sample(val id: Int)")) { `class`(fileName) }.shouldBeEmpty()
        }

        "or はどちらかでよい" {
            judged(kt("internal class A\nprivate class B\nclass C")) {
                (internal or private).`class`("*")
            } shouldBe listOf(
                "C (line 5) | Not allowed: class C | Expected: (internal or private) class * | " +
                    "Differs in: modifiers (missing: internal)",
            )
        }

        "アノテーションをいくつも書けば、すべて要る" {
            judged(kt("@A @B class X\n@A class Y")) {
                annotated("A", "B").`class`("*")
            } shouldBe listOf(
                "Y (line 4) | Not allowed: @A class Y | Expected: @A @B class * | Differs in: annotations (missing: @B)",
            )
        }

        "完全修飾名のアノテーションは import から解決して当たる" {
            judged(kt("import androidx.compose.runtime.Composable\n\n@Composable\nfun Sample() {}")) {
                annotated("androidx.compose.runtime.Composable").`fun`(fileName)
            }.shouldBeEmpty()
        }

        "種類が違えば kind を言う" {
            judged(kt("class Sample")) { `interface`(fileName) } shouldBe listOf(
                "Sample (line 3) | Not allowed: class Sample | Expected: interface Sample | Differs in: kind (is class, expected interface)",
            )
        }

        "private・protected・override のメンバも数える" {
            judged(kt("abstract class Sample : Base() {\n    private val a = 1\n    protected fun b() {}\n    override fun c() {}\n}")) {
                `class`(fileName) { }
            } shouldBe listOf(
                "Sample.a (line 4) | Not allowed: private val a in class Sample",
                "Sample.b (line 5) | Not allowed: protected fun b() in class Sample",
                "Sample.c (line 6) | Not allowed: override fun c() in class Sample",
            )
        }
    }

    "型・引数・戻り値・親の型" - {
        "プロパティの型はパッケージと空白を外して比べ、* が使える" {
            judged(kt("class Sample {\n    val a: kotlinx.coroutines.flow.StateFlow< Int >\n}")) {
                `class`(fileName) { `val`("a", "StateFlow<*>") }
            }.shouldBeEmpty()
        }

        "推論させた型は、型を書いた行に当たらない" {
            judged(kt("class Sample {\n    val a = 1\n}")) {
                `class`(fileName) { `val`("a", "Int") }
            } shouldBe listOf(
                "Sample.a (line 4) | Not allowed: val a in class Sample | Expected: val a: Int | Differs in: type (not written, expected Int)",
            )
        }

        "nullable は区別する" {
            judged(kt("val name: String? = null")) { `val`("name", "String") } shouldBe listOf(
                "name (line 3) | Not allowed: val name: String? | Expected: val name: String | Differs in: type (is String?, expected String)",
            )
        }

        "引数は型の並びで比べ、書かなければ問わない" {
            judged(kt("fun load(a: String, b: Int) {}\nfun save(x: Int) {}\nfun drop(x: Int) {}")) {
                `fun`("load")("String", "Int")
                `fun`("save")()
                `fun`("drop")
            } shouldBe listOf(
                "save (line 4) | Not allowed: fun save(Int) | Expected: fun save() | Differs in: parameters (are (Int), expected ())",
            )
        }

        "戻り値は、ブロック本体なら Unit、式本体で書いていなければ当たらない" {
            judged(kt("fun onA() {}\nfun onB(): Unit {}\nfun onC() = Unit")) {
                `fun`("on*")().returns("Unit")
            } shouldBe listOf(
                "onC (line 5) | Not allowed: fun onC() | Expected: fun on*(): Unit | Differs in: return type (not written, expected Unit)",
            )
        }

        "親の型は、型引数を書かなければ名前で、書けば型引数ごと比べる。いくつも書けばすべて要る" {
            judged(kt("class Sample : Repository<User>, Closeable")) {
                `class`(fileName).superclass("Repository", "Closeable")
            }.shouldBeEmpty()
            judged(kt("class Sample : Repository<User>")) {
                `class`(fileName).superclass("Repository<Admin>")
            } shouldBe listOf(
                "Sample (line 3) | Not allowed: class Sample : Repository<User> | Expected: class Sample : Repository<Admin> | " +
                    "Differs in: superclass (missing: Repository<Admin>)",
            )
        }
    }

    "書いた種類だけが allow list" - {
        "import だけを書けば宣言は見ない" {
            judged(kt("class Anything\nfun whatever() {}")) { `import`("**") }.shouldBeEmpty()
        }

        "宣言だけを書けば import は見ない" {
            judged(kt("import android.content.Context\n\nclass Sample")) { `class`(fileName) }.shouldBeEmpty()
        }

        "{ } を付けなければメンバは見ない。空の { } はメンバを置かない" {
            val source = kt("class Sample {\n    fun a() {}\n}")
            judged(source) { `class`(fileName) }.shouldBeEmpty()
            judged(source) { `class`(fileName) { } } shouldBe listOf("Sample.a (line 4) | Not allowed: fun a() in class Sample")
        }
    }

    "必須と足りないもの" - {
        "* を含まない名前は必須で、無ければ足りない" {
            judged(kt("interface Sample")) {
                `interface`(fileName)
                `class`("Default$fileName").superclass(fileName)
            } shouldBe listOf("DefaultSample | Missing: class DefaultSample : Sample")
        }

        "optional() と * を含む名前は無くてもよい" {
            judged(kt("interface Sample")) {
                `interface`(fileName)
                `class`("Default$fileName").optional()
                `object`("*Factory")
            }.shouldBeEmpty()
        }

        "名前だけ当たる宣言は 1 件にまとめ、足りないは出さない" {
            judged(kt("class Sample")) { data.`class`(fileName) } shouldBe listOf(
                "Sample (line 3) | Not allowed: class Sample | Expected: data class Sample | Differs in: modifiers (missing: data)",
            )
        }

        "名前は当たるが形が違う宣言が anythingElse で許されたら、足りないに Found を添える" {
            judged(kt("class Sample {\n    private fun invoke() {}\n}")) {
                `class`(fileName) {
                    override.`fun`("invoke")
                    private.anythingElse()
                }
            } shouldBe listOf("Sample.invoke | Missing: override fun invoke(...) in class Sample | Found: private fun invoke() (line 4)")
        }

        "メンバの必須は、入れ子のクラスまで見る" {
            judged(kt("class Sample {\n    class Mode {\n        fun next() {}\n    }\n}")) {
                `class`(fileName) {
                    `class`("Mode") {
                        `fun`("previous")
                    }
                }
            } shouldBe listOf(
                "Sample.Mode.next (line 5) | Not allowed: fun next() in class Mode",
                "Sample.Mode.previous | Missing: fun previous(...) in class Mode",
            )
        }

        "足りないものは、ファイル直下が先、メンバが後" {
            judged(kt("class Sample {\n}")) {
                `class`(fileName) { `val`("state") }
                `object`("Keys")
            } shouldBe listOf(
                "Keys | Missing: object Keys",
                "Sample.state | Missing: val state in class Sample",
            )
        }
    }
})
