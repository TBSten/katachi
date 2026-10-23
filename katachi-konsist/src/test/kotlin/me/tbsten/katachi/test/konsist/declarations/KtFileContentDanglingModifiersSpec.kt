@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.internal.EvaluationInput
import me.tbsten.katachi.konsist.declarations.internal.PatternCache
import me.tbsten.katachi.konsist.declarations.internal.evaluateWithStandIns
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/**
 * Modifiers or annotations written on a line of their own, which no declaration follows:
 * `annotated("Inject")` alone does not apply to the next line, so it is refused instead of
 * being ignored.
 */
private fun danglingOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentLineException =
    shouldThrow<KatachiKtFileContentLineException> { rulesOf(block = block) }

class KtFileContentDanglingModifiersSpec : FreeSpec({
    "宣言につながらない修飾子" - {
        "単独の行の annotated は DanglingModifiers" {
            danglingOf { annotated("Inject") }.let {
                it.problem shouldBe KtFileContentLineProblem.DanglingModifiers
                it.line shouldBe "@Inject"
                it.message.orEmpty() shouldContain "@Inject"
                it.message.orEmpty() shouldContain "is not followed by a declaration"
            }
        }

        "annotated の次の行に宣言を書いても、つながらないので DanglingModifiers" {
            danglingOf {
                annotated("Inject")
                `class`("A")
            }.line shouldBe "@Inject"
        }

        "private などの修飾子だけの行も DanglingModifiers" {
            danglingOf { private }.line shouldBe "private"
            danglingOf { public.data }.line shouldBe "public data"
            danglingOf { (internal or private) }.line shouldBe "(internal or private)"
        }

        "not. の修飾子だけの行も DanglingModifiers" {
            danglingOf { not.private }.line shouldBe "not private"
        }

        "not. の行の直し方は anythingElse() を勧めない（not. の後では別の例外になるため）" {
            val message = danglingOf { not.private }.message.orEmpty()
            message shouldContain "not.private"
            (message.contains("anythingElse()")) shouldBe false
        }

        "not. でない行の直し方は anythingElse() も挙げる" {
            danglingOf { private }.message.orEmpty() shouldContain "anythingElse()"
        }

        "クラスの { } の中の単独の修飾子も DanglingModifiers" {
            danglingOf { `class`("A") { private } }.line shouldBe "private"
        }

        "declaredAt は書かれた行を指す" {
            val error = danglingOf { annotated("Inject") }
            error.declaredAt.toString() shouldContain "KtFileContentDanglingModifiersSpec.kt"
        }

        "最初に書かれた単独の修飾子で止まる" {
            danglingOf {
                private
                annotated("X")
            }.line shouldBe "private"
        }
    }

    "定義を読んだ時点での検出" - {
        "architecture { } の評価中に、単独の annotated で DanglingModifiers になる" {
            val error = shouldThrow<KatachiKtFileContentLineException> {
                architecture {
                    files = wholeTree()
                    "R".role {
                        layout { "src" / "*".ktFile() }
                        ktFileContent { annotated("Inject") }
                    }
                }
            }
            error.problem shouldBe KtFileContentLineProblem.DanglingModifiers
            error.line shouldBe "@Inject"
            error.declaredAt.toString() shouldContain "KtFileContentDanglingModifiersSpec.kt"
        }

        "名前つきのブロックの単独の修飾子も定義の時点で DanglingModifiers になる" {
            shouldThrow<KatachiKtFileContentLineException> {
                architecture {
                    files = wholeTree()
                    "R".role {
                        layout { "src" / "*".ktFile() }
                        "名前".ktFileContent { private }
                    }
                }
            }.line shouldBe "private"
        }

        "stand-in では書かれず実際のファイルでだけ書かれる単独の修飾子は、定義の時点では捕まらず検査で出る" {
            shouldNotThrowAny {
                architecture {
                    files = wholeTree()
                    "R".role {
                        layout { "src" / "*".ktFile() }
                        ktFileContent { if (fileName == "Real") annotated("Inject") }
                    }
                }
            }
            val violations = ktFileContentRun(
                "src/Real.kt" to "package a\n\nclass Real\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent { if (fileName == "Real") annotated("Inject") }
            }
            violations.report() shouldContain "is not followed by a declaration"
        }
    }

    "宣言につながる修飾子は例外にならない" - {
        "kind を呼ぶ・anythingElse・or・invoke につなげた修飾子" {
            shouldNotThrowAny {
                rulesOf {
                    annotated("HiltViewModel").public.`class`("A") {
                        private.anythingElse()
                        override.`fun`("x")
                    }
                    (internal or private).data.`object`("B")
                    annotated("Serializable")(`object` or `class`)("C")
                    const(`val` or `var`)("D")
                    public.`typealias`("E")
                    not.private.`fun`("*")
                    not.annotated("Deprecated").`class`("F")
                }
            }
        }

        "変数に取っておいた修飾子を複数の行に使っても例外にならない" {
            shouldNotThrowAny {
                rulesOf {
                    val priv = private
                    priv.`fun`("a")
                    priv.`val`("b")
                }
            }
        }

        "fileName を読むブロックの stand-in の実行では、条件が偽で使われなかった修飾子を見逃す" {
            val block: KtFileContentScope.() -> Unit = {
                val inject = annotated("Inject")
                if (fileName.endsWith("ViewModel")) inject.`class`(fileName)
            }
            shouldNotThrowAny {
                evaluateWithStandIns(block, EvaluationInput("Sample", { it }, PatternCache()), SPEC_SITE)
            }
            danglingOf(block).problem shouldBe KtFileContentLineProblem.DanglingModifiers
        }

        "fileName を読まないブロックの stand-in の実行では DanglingModifiers を投げる" {
            shouldThrow<KatachiKtFileContentLineException> {
                evaluateWithStandIns({ annotated("Inject") }, EvaluationInput("Sample", { it }, PatternCache()), SPEC_SITE)
            }.problem shouldBe KtFileContentLineProblem.DanglingModifiers
        }

        "何も書かないブロックは例外にならない" {
            shouldNotThrowAny { rulesOf { } }
        }
    }
})
