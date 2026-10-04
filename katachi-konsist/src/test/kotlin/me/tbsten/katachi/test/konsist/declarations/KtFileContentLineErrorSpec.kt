package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.Modifiers
import me.tbsten.katachi.konsist.declarations.TypeLine

/** Every [KtFileContentLineProblem]: a way of writing a line that cannot mean anything. */

private fun lineErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentLineException =
    shouldThrow<KatachiKtFileContentLineException> { rulesOf(block = block) }

class KtFileContentLineErrorSpec : FreeSpec({
    "except と optional" - {
        "許可の行の except は ExceptWithoutNot" {
            lineErrorOf { `import`("kotlin.**").except("kotlin.io.**") }.let {
                it.problem shouldBe KtFileContentLineProblem.ExceptWithoutNot
                it.line shouldBe "import kotlin.**"
                it.message shouldBe """
                    .except(...) on line "import kotlin.**" at ${it.declaredAt} has no effect.
                    except narrows a prohibited line; an allowed line already allows only what it matches.
                    Start the line with not., or remove .except(...).
                """.trimIndent()
            }
            lineErrorOf { `class`("A").except("B") }.line shouldBe "class A"
            lineErrorOf { `package`("a.**").except("a.b") }.line shouldBe "package a.**"
            lineErrorOf { `class`("H") { `fun`("x").except("y") } }.problem shouldBe KtFileContentLineProblem.ExceptWithoutNot
        }

        "名前の無い except は ExceptWithoutNames" {
            lineErrorOf { not.`class`("*").except() }.let {
                it.problem shouldBe KtFileContentLineProblem.ExceptWithoutNames
                it.line shouldBe "not class *"
            }
            lineErrorOf { not.`import`("a.**").except() }.problem shouldBe KtFileContentLineProblem.ExceptWithoutNames
        }

        "禁止の行の optional は OptionalOnProhibited" {
            lineErrorOf { not.`class`("A").optional() }.let {
                it.problem shouldBe KtFileContentLineProblem.OptionalOnProhibited
                it.line shouldBe "not class A"
            }
            lineErrorOf { not.`import`("a.b").optional() }.line shouldBe "not import a.b"
        }
    }

    "{ } と後ろにつなぐもの" - {
        "{ } を 2 回付けると BodyWrittenTwice" {
            lineErrorOf { `class`("A") { }.superclass("B") { } }.let {
                it.problem shouldBe KtFileContentLineProblem.BodyWrittenTwice
                it.line shouldBe "class A : B"
            }
        }

        "禁止の行の { } は BodyOnProhibited" {
            lineErrorOf { not.`class`("A") { } }.let {
                it.problem shouldBe KtFileContentLineProblem.BodyOnProhibited
                it.line shouldBe "not class A"
            }
        }

        "引数を 2 回書くと ParametersWrittenTwice（anyArgs() も書いたことになる）" {
            lineErrorOf { `fun`("x")()("String") }.let {
                it.problem shouldBe KtFileContentLineProblem.ParametersWrittenTwice
                it.line shouldBe "fun x()"
            }
            lineErrorOf { `fun`("x")(anyArgs())() }.line shouldBe "fun x(...)"
        }

        "returns を 2 回書くと ReturnTypeWrittenTwice" {
            lineErrorOf { `fun`("x").returns("A").returns("B") }.let {
                it.problem shouldBe KtFileContentLineProblem.ReturnTypeWrittenTwice
                it.line shouldBe "fun x(...): A"
            }
        }

        "名前の無い superclass は SuperclassWithoutNames" {
            lineErrorOf { `class`("A").superclass() }.problem shouldBe KtFileContentLineProblem.SuperclassWithoutNames
        }
    }

    "or と not" - {
        "禁止と許可を or でつなぐと NotInsideOr" {
            lineErrorOf { (not.private or internal).`class`("A") }.let {
                it.problem shouldBe KtFileContentLineProblem.NotInsideOr
                it.line shouldBe "not private or internal"
            }
            lineErrorOf { (not.`class` or `interface`)("A") }.line shouldBe "not class or interface"
            lineErrorOf { (`val` or not.`var`)("a") }.line shouldBe "val or not var"
        }

        "両方が禁止なら or でつなげる" {
            rulesOf { (not.`class` or not.`interface`)("*Impl") }.dump() shouldBe "prohibited class{}|interface{} *Impl"
        }

        "not のあとの anythingElse は AnythingElseAfterNot" {
            lineErrorOf { not.private.anythingElse() }.let {
                it.problem shouldBe KtFileContentLineProblem.AnythingElseAfterNot
                it.line shouldBe "not private (anything)"
            }
        }
    }

    "種類と annotated" - {
        "クラスの中の typealias は TypeAliasInClassBody" {
            lineErrorOf { `class`("A") { `typealias`("B") } }.let {
                it.problem shouldBe KtFileContentLineProblem.TypeAliasInClassBody
                it.line shouldBe "typealias B"
            }
        }

        "名前の無い annotated は AnnotatedWithoutNames" {
            lineErrorOf { annotated().`class`("A") }.let {
                it.problem shouldBe KtFileContentLineProblem.AnnotatedWithoutNames
                it.line shouldBe "annotated()"
                it.message?.lines()?.first() shouldBe "annotated() at ${it.declaredAt} names no annotation."
            }
            lineErrorOf { public.annotated() }.problem shouldBe KtFileContentLineProblem.AnnotatedWithoutNames
        }
    }

    "ほかのブロックの値" - {
        "ファイル直下の値でクラスの中に行を書くと OutsideItsBlock" {
            lineErrorOf {
                val fromFile = private
                `class`("A") { fromFile.anythingElse() }
            }.let {
                it.problem shouldBe KtFileContentLineProblem.OutsideItsBlock
                it.line shouldBe "private (anything)"
            }
        }

        "ラベルで外のスコープを指しても、クラスの中から import は書けない" {
            lineErrorOf {
                `class`("A") { this@lineErrorOf.`import`("kotlin.**") }
            }.problem shouldBe KtFileContentLineProblem.OutsideItsBlock
        }

        "ほかのブロックの種類と or でつなぐと OutsideItsBlock" {
            lineErrorOf {
                val fromFile = `class`
                `class`("A") { (fromFile or `interface`)("B") }
            }.problem shouldBe KtFileContentLineProblem.OutsideItsBlock
        }

        "評価が終わった後に値を使うと OutsideItsBlock" {
            lateinit var kept: Modifiers
            lateinit var line: TypeLine
            rulesOf {
                kept = private
                line = `class`("A")
            }

            shouldThrow<KatachiKtFileContentLineException> { kept.anythingElse() }
                .problem shouldBe KtFileContentLineProblem.OutsideItsBlock
            shouldThrow<KatachiKtFileContentLineException> { line.optional() }
                .problem shouldBe KtFileContentLineProblem.OutsideItsBlock
        }
    }

    "文面" - {
        "どの文面も 3 行で ASCII だけ、宣言元を含む" {
            val errors = listOf<KtFileContentScope.() -> Unit>(
                { `import`("a.**").except("b") },
                { not.`class`("*").except() },
                { not.`class`("A").optional() },
                { `class`("A") { }.superclass("B") { } },
                { not.`class`("A") { } },
                { `fun`("x")()() },
                { `fun`("x").returns("A").returns("B") },
                { `class`("A").superclass() },
                { (not.private or internal).`class`("A") },
                { `class`("A") { `typealias`("B") } },
                { annotated() },
                { not.private.anythingElse() },
            ).map(::lineErrorOf)

            errors.map { it.problem }.toSet().size shouldBe errors.size
            for (error in errors) {
                val message = error.message.orEmpty()
                message.lines().size shouldBe 3
                message.filter { it.code > 127 } shouldBe ""
                (error.declaredAt.toString() in message) shouldBe true
                error.declaredAt.fileName shouldBe "KtFileContentLineErrorSpec.kt"
            }
        }
    }
})
