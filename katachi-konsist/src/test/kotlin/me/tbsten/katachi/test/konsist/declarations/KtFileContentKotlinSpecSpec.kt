package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/** `final`, type aliases in a class body and their aliased type, parameter modifiers and default values, annotation arguments and use-site targets. */

private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

class KtFileContentKotlinSpecSpec : FreeSpec({
    "final" - {
        "final を書いた行は final と書いた宣言だけに当たる" {
            judged(kt("open class A {\n    final override fun toString() = \"\"\n    override fun hashCode() = 0\n}")) {
                `package`("**")
                allow(open, `class`, "A") {
                    allow(final, override, `fun`, "toString")()
                    allow(final, override, `fun`, "hashCode")()
                }
            } shouldBe listOf(
                "A.hashCode (line 5) | Not allowed: override fun hashCode() in class A | Expected: final override fun hashCode() | " +
                    "Differs in: modifiers (missing: final)",
            )
        }

        "final を書かない行は final と書いた宣言にも当たる" {
            judged(kt("open class A {\n    final override fun toString() = \"\"\n}")) {
                `package`("**")
                allow(open, `class`, "A") { allow(override, `fun`, "toString")() }
            }.shouldBeEmpty()
        }
    }

    "typealias" - {
        "クラスの中の typealias も行に書ける" {
            judged(kt("class A {\n    typealias Id = String\n}")) {
                `package`("**")
                allow(`class`, "A") { allow(`typealias`, "Id") }
            }.shouldBeEmpty()
        }

        "右辺を書くと右辺の型も見る" {
            judged(kt("typealias Handler = (String) -> Unit\ntypealias Other = Int")) {
                `package`("**")
                allow(`typealias`, "Handler", "(*) -> Unit")
                allow(`typealias`, "Other", "(*) -> Unit")
            } shouldBe listOf(
                "Other (line 4) | Not allowed: typealias Other = Int | Expected: typealias Other = (*) -> Unit | " +
                    "Differs in: aliased type (is Int, expected (*) -> Unit)",
            )
        }
    }

    "引数の修飾子と既定値" - {
        "vararg を書くと vararg の引数だけに当たる" {
            judged(kt("fun a(vararg xs: Int) {}\nfun b(xs: Int) {}")) {
                `package`("**")
                allow(`fun`, "a")("vararg xs: Int")
                allow(`fun`, "b")("vararg xs: Int")
            } shouldBe listOf(
                "b (line 4) | Not allowed: fun b(xs: Int) | Expected: fun b(vararg xs: Int) | " +
                    "Differs in: parameters (are (xs: Int), expected (vararg xs: Int))",
            )
        }

        "= * は既定値のある引数に当たり、書かなければ既定値を見ない" {
            judged(kt("fun a(force: Boolean = false) {}\nfun b(force: Boolean) {}")) {
                `package`("**")
                allow(`fun`, "a")("force: Boolean = *")
                allow(`fun`, "b")("force: Boolean")
            }.shouldBeEmpty()
            judged(kt("fun b(force: Boolean) {}")) {
                `package`("**")
                allow(`fun`, "b")("force: Boolean = *")
            } shouldBe listOf(
                "b (line 3) | Not allowed: fun b(force: Boolean) | Expected: fun b(force: Boolean = *) | " +
                    "Differs in: parameters (are (force: Boolean), expected (force: Boolean = *))",
            )
        }
    }

    "アノテーションの引数と use-site target" - {
        "引数の glob と () を見る" {
            judged(kt("@Deprecated(\"x\", level = DeprecationLevel.HIDDEN)\nfun a() {}\n@Deprecated(\"y\")\nfun b() {}")) {
                `package`("**")
                allow(annotated("Deprecated(*HIDDEN*)"), `fun`, "a")()
                allow(annotated("Deprecated(*HIDDEN*)"), `fun`, "b")()
            } shouldBe listOf(
                "b (line 5) | Not allowed: @Deprecated fun b() | Expected: @Deprecated(*HIDDEN*) fun b() | " +
                    "Differs in: annotations (missing: @Deprecated(*HIDDEN*))",
            )
        }

        "get: の target を見る" {
            judged(kt("class A {\n    @get:JvmName(\"id\")\n    val a: Int = 1\n    @JvmName(\"b\")\n    val b: Int = 1\n}")) {
                `package`("**")
                allow(`class`, "A") {
                    allow(annotated("get:JvmName"), `val`, "a")
                    allow(annotated("get:JvmName"), `val`, "b")
                }
            } shouldBe listOf(
                "A.b (line 6) | Not allowed: @JvmName val b: Int in class A | Expected: @get:JvmName val b | " +
                    "Differs in: annotations (missing: @get:JvmName)",
            )
        }
    }
})
