@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.test.report.reportForTest
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
 * A value of the vocabulary written on a line of its own and never passed to `allow(...)`: a
 * modifier, `annotated(...)`, a kind or `anythingElse`. `annotated("Inject")` alone does not apply
 * to the next line, so it is refused instead of being ignored.
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
                it.message.orEmpty() shouldContain "is not passed to allow(...)"
            }
        }

        "annotated の次の行に宣言を書いても、つながらないので DanglingModifiers" {
            danglingOf {
                annotated("Inject")
                required(`class`, "A")
            }.line shouldBe "@Inject"
        }

        "private などの修飾子だけの行も DanglingModifiers" {
            danglingOf { private }.line shouldBe "private"
            danglingOf { internal or private }.line shouldBe "(internal or private)"
        }

        "種類の値だけの行も DanglingModifiers" {
            danglingOf { `class` }.line shouldBe "class"
            danglingOf { `class` or `interface` }.line shouldBe "(class or interface)"
            danglingOf { `fun` }.line shouldBe "fun"
            danglingOf { `val` or `var` }.line shouldBe "(val or var)"
            danglingOf { `typealias` }.line shouldBe "typealias"
            danglingOf { type }.line shouldBe "(class or interface or object)"
        }

        "anythingElse の値だけの行も DanglingModifiers" {
            danglingOf { anythingElse }.line shouldBe "anythingElse"
        }

        "直し方は allow(...) に渡す書き方を挙げる" {
            val message = danglingOf { private }.message.orEmpty()
            message shouldContain "allow(annotated(\"Inject\"), `class`, \"Name\")"
            message shouldContain "allow(private, anythingElse)"
        }

        "クラスの { } の中の単独の修飾子・種類も DanglingModifiers" {
            danglingOf { required(`class`, "A") { private } }.line shouldBe "private"
            danglingOf { required(`class`, "A") { `fun` } }.line shouldBe "fun"
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
            violations.reportForTest() shouldContain "is not passed to allow(...)"
        }
    }

    "宣言につながる修飾子は例外にならない" - {
        "allow(...) に渡した修飾子・種類・anythingElse と、or でつないだ値" {
            shouldNotThrowAny {
                rulesOf {
                    allow(annotated("HiltViewModel"), public, `class`, "A") {
                        allow(private, anythingElse)
                        allow(override, `fun`, "x")
                    }
                    allow(internal or private, data, `object`, "B")
                    allow(annotated("Serializable"), `object` or `class`, "C")
                    allow(const, `val` or `var`, "D")
                    allow(public, `typealias`, "E")
                    not.allow(private, `fun`, "*")
                    not.allow(annotated("Deprecated"), `class`, "F")
                }
            }
        }

        "required(...) に渡した修飾子・種類と、or でつないだ値も使われたと数える" {
            shouldNotThrowAny {
                rulesOf {
                    required(annotated("HiltViewModel"), public, `class`, "A") {
                        required(override, `fun`, "x")
                        required(public or internal, `val`, "y")
                    }
                    required(internal or private, data, `object`, "B")
                    required(`fun`, `interface`, "*Listener")
                    required(const, `val` or `var`, "D")
                    required(public, `typealias`, "E")
                }
            }
        }

        "required(...) の行があっても、どこにも渡さなかった値は DanglingModifiers" {
            danglingOf {
                required(public, `class`, "A")
                internal
            }.line shouldBe "internal"
        }

        "変数に取っておいた修飾子を複数の行に使っても例外にならない" {
            shouldNotThrowAny {
                rulesOf {
                    val priv = private
                    required(priv, `fun`, "a")
                    required(priv, `val`, "b")
                }
            }
        }

        "fileName を読むブロックの stand-in の実行では、条件が偽で使われなかった修飾子を見逃す" {
            val block: KtFileContentScope.() -> Unit = {
                val inject = annotated("Inject")
                if (fileName.endsWith("ViewModel")) required(inject, `class`, fileName)
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
