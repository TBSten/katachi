@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.internal.reportForTest
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentModifierProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.EvaluationInput
import me.tbsten.katachi.konsist.declarations.internal.PatternCache
import me.tbsten.katachi.konsist.declarations.internal.evaluateWithStandIns
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * The values passed to `allow(...)`: which block they belong to, using one value more than once,
 * a value only some files reach, and the candidates a line keeps when only some of them can be.
 */

private fun lineErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentLineException =
    shouldThrow<KatachiKtFileContentLineException> { rulesOf(block = block) }

private fun modifierErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentModifierException =
    shouldThrow<KatachiKtFileContentModifierException> { rulesOf(block = block) }

class KtFileContentAllowValuesSpec : FreeSpec({
    "ほかのブロックの値" - {
        "ファイル直下の修飾子を or の左に置いてクラスの中で使うと OutsideItsBlock" {
            lineErrorOf {
                val outer = private
                required(`class`, "A") { required(outer or public, `fun`, "f") }
            }.problem shouldBe KtFileContentLineProblem.OutsideItsBlock
        }

        "ファイル直下の修飾子を or の右に置いてクラスの中で使うと OutsideItsBlock" {
            lineErrorOf {
                val outer = private
                required(`class`, "A") { required(public or outer, `fun`, "f") }
            }.let {
                it.problem shouldBe KtFileContentLineProblem.OutsideItsBlock
                it.line shouldBe "public or private"
            }
        }

        "ほかのブロックの val と or でつなぐと OutsideItsBlock" {
            lineErrorOf {
                val outer = `val`
                required(`class`, "A") { allow(outer or `var`, "x") }
            }.problem shouldBe KtFileContentLineProblem.OutsideItsBlock
        }

        "ほかのブロックの anythingElse を渡すと OutsideItsBlock" {
            lineErrorOf {
                val outer = anythingElse
                required(`class`, "A") { allow(private, outer) }
            }.let {
                it.problem shouldBe KtFileContentLineProblem.OutsideItsBlock
                it.line shouldBe "private (anything)"
            }
        }
    }

    "同じ値を何度も使う" - {
        "同じ修飾子を 1 行に 2 回渡すと 1 つと同じ" {
            dumpOf {
                val p = private
                allow(p, p, `class`, "*")
            } shouldBe "optional class{private} *"
        }

        "同じ種類の値を複数の行で使い回せる" {
            dumpOf {
                val k = `class` or `interface`
                required(k, "A")
                required(public, k, "B")
                not.allow(k, "*Impl")
            } shouldBe """
                required class{}|interface{} A
                required class{public}|interface{public} B
                prohibited class{}|interface{} *Impl
            """.trimIndent()
        }
    }

    "fileName を読むブロックの種類の値" - {
        val block: KtFileContentScope.() -> Unit = {
            val kind = `class`
            if (fileName.endsWith("ViewModel")) allow(kind, fileName)
        }

        "定義を読んだ時点（stand-in）では、使われなかった種類の値を見逃す" {
            shouldNotThrowAny {
                evaluateWithStandIns(block, EvaluationInput("Sample", { it }, PatternCache()), SPEC_SITE)
            }
        }

        "そのファイルで使われなかった種類の値は DanglingModifiers" {
            shouldThrow<KatachiKtFileContentLineException> { rulesOf(fileName = "Home", block = block) }.let {
                it.problem shouldBe KtFileContentLineProblem.DanglingModifiers
                it.line shouldBe "class"
            }
            shouldNotThrowAny { rulesOf(fileName = "HomeViewModel", block = block) }
        }

        "架空の定義は通り、検査で DanglingModifiers が出る" {
            shouldNotThrowAny {
                architecture {
                    files = wholeTree()
                    "R".role {
                        layout { "src" / "*".ktFile() }
                        ktFileContent { if (fileName == "Real") `class` }
                    }
                }
            }
            val violations = ktFileContentRun(
                "src/Real.kt" to "package a\n\nclass Real\n",
                paths = { "src" / "*".ktFile() },
            ) {
                ktFileContent { if (fileName == "Real") `class` }
            }
            violations.reportForTest() shouldContain "\"class\" at "
            violations.reportForTest() shouldContain "is not passed to allow(...)"
        }
    }

    "fun interface の種類" - {
        "class or interface を渡すと interface に絞る" {
            dumpOf { allow(`fun`, `class` or `interface`, "*Listener") } shouldBe "optional interface{fun} *Listener"
        }

        "interface に付けられない修飾子は NotApplicable" {
            modifierErrorOf { required(data, `fun`, `interface`, "X") }.let {
                it.problem shouldBe KtFileContentModifierProblem.NotApplicable
                it.modifiers shouldBe listOf("data")
                it.kind shouldBe "interface"
            }
        }
    }

    "一部の候補だけが衝突する行" - {
        "衝突する候補だけを外す" {
            dumpOf { required(public or internal, internal, `class`, "X") } shouldBe "required class{internal} X"
        }

        "anythingElse でも衝突する候補だけを外す" {
            dumpOf { allow(public or internal, internal, anythingElse) } shouldBe "anythingElse {internal}"
        }

        "外した候補と付けられない候補が重なっても、残るものがあれば通る" {
            dumpOf { allow(data or sealed, sealed, `class` or `interface`, "*") } shouldBe
                "optional class{sealed}|interface{sealed} *"
        }

        "候補が全部衝突するときだけ Conflicting" {
            modifierErrorOf { required(public or private, internal, `class`, "X") }.let {
                it.problem shouldBe KtFileContentModifierProblem.Conflicting
                it.modifiers shouldBe listOf("public", "internal")
            }
            modifierErrorOf { allow(public or private, internal, anythingElse) }.problem shouldBe
                KtFileContentModifierProblem.Conflicting
        }
    }
})
