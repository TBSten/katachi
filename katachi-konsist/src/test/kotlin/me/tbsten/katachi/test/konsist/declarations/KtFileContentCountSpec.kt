@file:Suppress("DEPRECATION")

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.ktFileContent

/** [body] as a file of package `com.example`: its first line is line 3. */
private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

private fun functions(count: Int): String = (1..count).joinToString("\n") { "fun f$it() {}" }

private fun countErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentLineException =
    shouldThrow<KatachiKtFileContentLineException> { rulesOf(block = block) }

/** `.count(...)` on a declaration line: how many declarations the line holds. */
class KtFileContentCountSpec : FreeSpec({
    "書き方" - {
        "範囲、min、max のどれでも書け、書いたとおりに行へ出る" {
            dumpOf {
                allow(`fun`, "*")().count(2..10)
                allow(`class`, "*").count(min = 3)
                allow(`object`, "*").count(max = 2)
                allow(`val`, "*").count(min = 1, max = 4)
            } shouldBe """
                optional fun{} * params=() count=.count(2..10)
                optional class{} * count=.count(min = 3)
                optional object{} * count=.count(max = 2)
                optional val{} * count=.count(min = 1, max = 4)
            """.trimIndent()
        }

        "クラスの本体の行にも、enum の entry・secondary constructor・init にも書ける" {
            dumpOf {
                allow(`class`, "Host") {
                    allow(`fun`, "on*").count(max = 3)
                    allow(entry, "*").count(1..5)
                    allow(constructor)("id: String").count(max = 2)
                    allow(init).count(max = 1)
                }
            } shouldBe """
                optional class{} Host
                  members:
                    optional fun{} on* count=.count(max = 3)
                    optional entry{} * count=.count(1..5)
                    optional constructor{} * params=(id: String) count=.count(max = 2)
                    optional init{} * count=.count(max = 1)
            """.trimIndent()
        }

        "required でも書け、下限を書かなければ 1 以上のまま" {
            val rule = rulesOf { required(`class`, "*Action").count(max = 2) }.firstDeclaration
            rule.minCount shouldBe 1
            rule.maxCount shouldBe 2
            val allowed = rulesOf { allow(`class`, "*Action").count(max = 2) }.firstDeclaration
            allowed.minCount shouldBe 0
            allowed.maxCount shouldBe 2
        }

        "allow に下限を書くと、その数だけ要る行になる" {
            rulesOf { allow(`fun`, "*").count(min = 2) }.firstDeclaration.minCount shouldBe 2
        }
    }

    "判定" - {
        "2..10 は、1 つだと足りず、2 つと 10 つは通り、11 だと多すぎる" {
            val block: KtFileContentScope.() -> Unit = {
                `package`("**")
                allow(`fun`, "*")().count(2..10)
            }
            judged(kt(functions(1)), block = block) shouldBe listOf(
                "* | Missing: fun *().count(2..10) | Count: 1 found, at least 2 expected",
            )
            judged(kt(functions(2)), block = block).shouldBeEmpty()
            judged(kt(functions(10)), block = block).shouldBeEmpty()
            judged(kt(functions(11)), block = block) shouldBe listOf(
                "* (line 13) | Too many: fun *().count(2..10) | Count: 11 found, at most 10 allowed | " +
                    "Found: f1 (line 3), f2 (line 4), f3 (line 5), f4 (line 6), f5 (line 7), f6 (line 8), " +
                    "f7 (line 9), f8 (line 10), f9 (line 11), f10 (line 12), f11 (line 13)",
            )
        }

        "0 つでも、下限のある allow は足りない" {
            judged(kt("class Sample"), block = {
                `package`("**")
                allow(`class`, "*")
                allow(`fun`, "*")().count(2..10)
            }) shouldBe listOf("* | Missing: fun *().count(2..10) | Count: 0 found, at least 2 expected")
        }

        "max だけなら、0 つでも通り、超えたときだけ多すぎる" {
            val block: KtFileContentScope.() -> Unit = {
                `package`("**")
                allow(`class`, "*").count(max = 1)
            }
            judged(kt("val x = 1"), block = {
                `package`("**")
                allow(`val`, "*")
                allow(`class`, "*").count(max = 1)
            }).shouldBeEmpty()
            judged(kt("class A"), block = block).shouldBeEmpty()
            judged(kt("class A\nclass B"), block = block) shouldBe listOf(
                "* (line 4) | Too many: class *.count(max = 1) | Count: 2 found, at most 1 allowed | Found: A (line 3), B (line 4)",
            )
        }

        "required の max だけは、0 つなら足りず、1 つと max までは通り、超えると多すぎる" {
            val block: KtFileContentScope.() -> Unit = {
                `package`("**")
                allow(`fun`, "*")
                required(`class`, "*Action").count(max = 2)
            }
            judged(kt("fun f() {}"), block = block) shouldBe
                listOf("*Action | Missing: class *Action.count(max = 2) | Count: 0 found, at least 1 expected")
            judged(kt("class HomeAction"), block = block).shouldBeEmpty()
            judged(kt("class HomeAction\nclass ListAction"), block = block).shouldBeEmpty()
            judged(kt("class HomeAction\nclass ListAction\nclass EditAction"), block = block).size shouldBe 1
        }

        "required に下限 0 を書くと、0 つでも通る" {
            judged(kt("fun f() {}"), block = {
                `package`("**")
                allow(`fun`, "*")
                required(`class`, "*Action").count(0..2)
            }).shouldBeEmpty()
        }

        "形の違う宣言は数えない" {
            val block: KtFileContentScope.() -> Unit = {
                `package`("**")
                allow(`fun`, "*")().count(max = 1)
                allow(`fun`, "*")("id: String").count(max = 1)
            }
            judged(kt("fun a() {}\nfun b(id: String) {}"), block = block).shouldBeEmpty()
        }

        "1 つの宣言に 2 つの行が当たったら、どちらの数にも数える" {
            val block: KtFileContentScope.() -> Unit = {
                `package`("**")
                allow(`fun`, "*")().count(min = 2)
                allow(`fun`, "f*")().count(max = 1)
            }
            judged(kt("fun f1() {}\nfun f2() {}"), block = block) shouldBe listOf(
                "f* (line 4) | Too many: fun f*().count(max = 1) | Count: 2 found, at most 1 allowed | Found: f1 (line 3), f2 (line 4)",
            )
            judged(kt("fun f1() {}\nfun g() {}"), block = block).shouldBeEmpty()
        }

        "禁止の行に当たる宣言は、allow の行の数に入らない" {
            judged(kt("fun a() {}\nfun b() {}"), block = {
                `package`("**")
                not.allow(`fun`, "b")
                allow(`fun`, "*")().count(max = 1)
            }) shouldBe listOf("b (line 4) | Not allowed: fun b() | Prohibited by: not fun b(...)")
        }

        "クラスの中の行は、そのクラスの中の数で数える" {
            val block: KtFileContentScope.() -> Unit = {
                `package`("**")
                allow(`class`, "*") { allow(`fun`, "on*")().count(max = 1) }
            }
            judged(kt("class A { fun onA() {} }\nclass B { fun onB() {} }"), block = block).shouldBeEmpty()
            judged(kt("class A { fun onA() {}\n    fun onB() {} }"), block = block) shouldBe listOf(
                "A.on* (line 4) | Too many: fun on*().count(max = 1) in class A | Count: 2 found, at most 1 allowed | " +
                    "Found: onA (line 3), onB (line 4)",
            )
        }

        "足りないときの直し方は、足す数と、下限を下げる道を示す" {
            fixLinesOf(kt(functions(1))) {
                `package`("**")
                allow(`fun`, "*")().count(2..10)
            }.size shouldBe 2
        }
    }

    "利用者の書き方: 関数は 2〜10 個、クラスは 1 つだけ" {
        val violations = ktFileContentRun(
            "util/OneFun.kt" to "package com.example\n\nfun only() {}\n",
            "util/Many.kt" to "package com.example\n\n" + functions(3) + "\nclass A\nclass B\n",
            paths = { "util" / "*".ktFile() },
        ) {
            ktFileContent {
                `package`("**")
                allow(`fun`, "*")().count(2..10)
                allow(`class`, "*").count(max = 1)
            }
        }

        violations.unsatisfiedLines() shouldBe listOf(
            "util/Many.kt: * (line 7) | Too many: class *.count(max = 1) | Count: 2 found, at most 1 allowed | Found: A (line 6), B (line 7)",
            "util/OneFun.kt: * | Missing: fun *().count(2..10) | Count: 1 found, at least 2 expected",
        )
    }

    "書き間違い" - {
        "範囲も下限も上限も無い count は CountWithoutBounds" {
            countErrorOf { allow(`class`, "A").count() }.let {
                it.problem shouldBe KtFileContentLineProblem.CountWithoutBounds
                it.line shouldBe "class A"
            }
        }

        "空の範囲・負の下限・1 未満の上限・下限が上限より大きいのは CountOutOfRange" {
            countErrorOf { allow(`class`, "A").count(5..2) }.problem shouldBe KtFileContentLineProblem.CountOutOfRange
            countErrorOf { allow(`class`, "A").count(min = -1) }.problem shouldBe KtFileContentLineProblem.CountOutOfRange
            countErrorOf { allow(`class`, "A").count(max = 0) }.problem shouldBe KtFileContentLineProblem.CountOutOfRange
            countErrorOf { allow(`class`, "A").count(0..0) }.problem shouldBe KtFileContentLineProblem.CountOutOfRange
            countErrorOf { allow(`class`, "A").count(min = 4, max = 3) }.problem shouldBe KtFileContentLineProblem.CountOutOfRange
        }

        "文面は、何を書いたかと直し方を言う" {
            countErrorOf { allow(`class`, "A").count(5..2) }.let {
                it.message shouldBe """
                    Line "class A" at ${it.declaredAt} writes 5..2, which no number of declarations satisfies.
                    A count has a minimum of zero or more and a maximum of one or more that is not below the minimum.
                    Write a range such as .count(2..10); to allow none, prohibit the declaration with not.allow(...).
                """.trimIndent()
            }
        }

        "not. の行の count は CountOnProhibited" {
            countErrorOf { not.allow(`class`, "A").count(max = 1) }.let {
                it.problem shouldBe KtFileContentLineProblem.CountOnProhibited
                it.line shouldBe "not class A"
            }
        }

        "count を 2 回書くと DetailWrittenTwice" {
            countErrorOf { allow(`class`, "A").count(max = 3).count(min = 1) }.let {
                it.problem shouldBe KtFileContentLineProblem.DetailWrittenTwice
                it.parameter shouldBe "count"
            }
        }
    }
})
