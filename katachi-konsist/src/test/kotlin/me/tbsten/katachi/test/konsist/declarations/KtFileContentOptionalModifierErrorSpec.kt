@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentModifierProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.failuresOf
import me.tbsten.katachi.konsist.declarations.internal.judgeKtFile

/**
 * The ways of writing `.optional()` that cannot mean anything, refused while the definition is
 * read, and the checks of the modifiers a declaration can carry, which `.optional()` ones go through too.
 */

/** The code every fix of the failures of [source] shows, in the order the report lists them. */
private fun fixCodesOf(source: String, block: KtFileContentScope.() -> Unit): List<String> {
    val rules = rulesOf(fileName = "Sample", block = block)
    return failuresOf(judgeKtFile(factsOf(source), rules), SAMPLE_PATH, rules).flatMap { failure -> failure.howToFix.flatMap { it.code } }
}

private fun lineErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentLineException =
    shouldThrow<KatachiKtFileContentLineException> { rulesOf(block = block) }

private fun modifierErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentModifierException =
    shouldThrow<KatachiKtFileContentModifierException> { rulesOf(block = block) }

class KtFileContentOptionalModifierErrorSpec : FreeSpec({
    "書けない .optional()" - {
        "annotated(…).optional() は OptionalAnnotation で、アノテーションを外すか .optional() を外す案内" {
            lineErrorOf { allow(annotated("Inject").optional(), `class`, "*") }.let {
                it.problem shouldBe KtFileContentLineProblem.OptionalAnnotation
                it.line shouldBe "@Inject.optional()"
                it.message shouldBe """
                    "@Inject.optional()" at ${it.declaredAt} makes an annotation optional.
                    An annotation the line does not write is not asked about, so an optional one would ask for nothing.
                    Remove the annotation, or remove .optional() if the declaration has to carry it.
                """.trimIndent()
            }
            lineErrorOf { allow((annotated("A") or private).optional(), `class`, "*") }.problem shouldBe
                KtFileContentLineProblem.OptionalAnnotation
        }

        ".optional() を 2 回付けると OptionalTwice" {
            lineErrorOf { allow(suspend.optional().optional(), `fun`, "*") }.let {
                it.problem shouldBe KtFileContentLineProblem.OptionalTwice
                it.line shouldBe "suspend.optional().optional()"
                it.message.orEmpty() shouldContain "Keep one .optional()."
            }
        }

        "or の片方だけの .optional() は OptionalInsideOr で、or 全体に付ける案内" {
            lineErrorOf { allow(internal.optional() or private, `fun`, "*") }.let {
                it.problem shouldBe KtFileContentLineProblem.OptionalInsideOr
                it.line shouldBe "internal.optional() or private"
                it.message.orEmpty() shouldContain "(internal or private).optional()"
            }
            lineErrorOf { allow(internal or private.optional(), `fun`, "*") }.line shouldBe "internal or private.optional()"
            lineErrorOf { allow(internal.optional() or private.optional(), `fun`, "*") }.problem shouldBe
                KtFileContentLineProblem.OptionalInsideOr
        }

        "allow(…, anythingElse) の .optional() は OptionalOnAnythingElse" {
            lineErrorOf { allow(suspend.optional(), anythingElse) }.let {
                it.problem shouldBe KtFileContentLineProblem.OptionalOnAnythingElse
                it.line shouldBe "suspend.optional() (anything)"
                it.message.orEmpty() shouldContain "Remove the .optional() modifier."
            }
        }

        "not.allow(…) の .optional() は OptionalAfterNot" {
            lineErrorOf { not.allow(suspend.optional(), `fun`, "*Sync") }.let {
                it.problem shouldBe KtFileContentLineProblem.OptionalAfterNot
                it.line shouldBe "not suspend.optional() fun *Sync(...)"
            }
        }

        "not.allow(…, anythingElse) は .optional() があっても今までどおり AnythingElseAfterNot" {
            lineErrorOf { not.allow(private.optional(), anythingElse) }.problem shouldBe KtFileContentLineProblem.AnythingElseAfterNot
        }
    }

    "付けられない組み合わせの検査" - {
        "種類に付けられない .optional() の修飾子は NotApplicable" {
            modifierErrorOf { allow(data.optional(), `interface`, "*") }.let {
                it.problem shouldBe KtFileContentModifierProblem.NotApplicable
                it.modifiers shouldBe listOf("data")
                it.kind shouldBe "interface"
                it.line shouldBe "data.optional() interface *"
            }
        }

        "種類のどれかに付けられるなら通る" {
            shouldNotThrowAny { rulesOf { allow(suspend.optional(), `fun`, "a") } }
            shouldNotThrowAny { rulesOf { allow(data.optional(), `class` or `interface`, "*") } }
        }

        "inline は fun・val・var に、external は typealias 以外に書ける" {
            shouldNotThrowAny { rulesOf { allow(inline, `val` or `var`, "*") } }
            shouldNotThrowAny { rulesOf { allow(external, `class` or `interface` or `object`, "*"); allow(external, `val`, "*") } }
            judged("package com.example\n\ninline val Int.twice get() = this * 2\n") {
                `package`("**")
                allow(inline, `val`, "*")
            } shouldBe emptyList()
        }

        "inline val を許す直し方の行は、例外を投げずに書ける" {
            val code = fixCodesOf("package com.example\n\ninline val Int.twice get() = this * 2\n") { required(`fun`, "x") }
                .single { it.startsWith("allow(") }
            code shouldBe "allow(inline, `val`, \"Int\", \"twice\")"
            shouldNotThrowAny { rulesOf { allow(inline, `val`, "Int", "twice") } }
        }

        "メンバだけの .optional() の修飾子をファイル直下に書くと OnlyInClassBody" {
            modifierErrorOf { allow(override.optional(), `fun`, "*") }.problem shouldBe KtFileContentModifierProblem.OnlyInClassBody
            modifierErrorOf { allow(open.optional(), `fun`, "*") }.let {
                it.problem shouldBe KtFileContentModifierProblem.OnlyInClassBody
                it.kind shouldBe "fun"
            }
            shouldNotThrowAny { rulesOf { required(`class`, "A") { allow(override.optional(), `fun`, "*") } } }
        }

        "必須の修飾子と一緒に付けられない .optional() の修飾子は Conflicting" {
            modifierErrorOf { allow(internal, private.optional(), `fun`, "*") }.let {
                it.problem shouldBe KtFileContentModifierProblem.Conflicting
                it.modifiers shouldBe listOf("private", "internal")
            }
        }

        "束ねのどれかが付けられるなら通り、どの候補とも付けられなければ Conflicting" {
            shouldNotThrowAny { rulesOf { allow(internal, (internal or private).optional(), `fun`, "*") } }
            modifierErrorOf { allow(internal or private, public.optional(), `fun`, "*") }.modifiers shouldBe listOf("public", "internal")
        }
    }

    "宣言につながらない .optional()" - {
        "行に渡さなかった suspend.optional() は DanglingModifiers" {
            lineErrorOf { suspend.optional() }.let {
                it.problem shouldBe KtFileContentLineProblem.DanglingModifiers
                it.line shouldBe "suspend.optional()"
            }
            lineErrorOf { (internal or private).optional() }.line shouldBe "(internal or private).optional()"
        }

        "行に渡さなかった .optional() の位置は、元の修飾子ではなく .optional() を呼んだ行" {
            var writtenAt: DeclarationSite? = null
            var optionalAt: DeclarationSite? = null
            lineErrorOf {
                val written = suspend; writtenAt = DeclarationSite.capture()
                written.optional(); optionalAt = DeclarationSite.capture()
                allow(written, `fun`, "a")
            }.declaredAt.let {
                it shouldBe optionalAt
                it shouldNotBe writtenAt
            }
        }

        ".optional() の元の修飾子は、行に渡せば使われたと数える" {
            shouldNotThrowAny { rulesOf { allow(suspend.optional(), `fun`, "a") } }
        }
    }

    "値の文字" - {
        "toString は書いたとおり" {
            rulesOf {
                val suspendOptional = suspend.optional()
                val visibilityOptional = (internal or private).optional()
                suspendOptional.toString() shouldBe "suspend.optional()"
                visibilityOptional.toString() shouldBe "(internal or private).optional()"
                allow(suspendOptional, visibilityOptional, `fun`, "a")
            }
        }
    }
})
