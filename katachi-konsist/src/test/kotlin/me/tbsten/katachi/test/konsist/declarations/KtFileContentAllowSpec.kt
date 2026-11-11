package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentModifierProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/**
 * `allow(modifiers..., kind, name, ...)`: every overload -- none to ten modifiers, for each of the
 * kinds, a fun interface and `anythingElse` -- writes the line the values passed to it make.
 */

/** How the dump prints the nine annotations that, with one modifier, make the ten values the longest overloads take. */
private const val NINE_ANNOTATIONS: String = "@A1 @A2 @A3 @A4 @A5 @A6 @A7 @A8 @A9"

private fun modifierErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentModifierException =
    shouldThrow<KatachiKtFileContentModifierException> { rulesOf(block = block) }

class KtFileContentAllowSpec : FreeSpec({
    "種類ごとの allow" - {
        "class・interface・object・type: 修飾子 0 個・数個・10 個" {
            dumpOf {
                required(`class`, "A")
                required(annotated("Keep"), public, data, `class`, "B")
                required(
                    annotated("A1"), annotated("A2"), annotated("A3"), annotated("A4"), annotated("A5"),
                    annotated("A6"), annotated("A7"), annotated("A8"), annotated("A9"), sealed,
                    type, "C",
                )
                required(`interface`, "D")
                required(`object`, "E")
            } shouldBe """
                required class{} A
                required class{@Keep public data} B
                required class{$NINE_ANNOTATIONS sealed}|interface{$NINE_ANNOTATIONS sealed} C
                required interface{} D
                required object{} E
            """.trimIndent()
        }

        "fun: 修飾子 0 個・数個・10 個" {
            dumpOf {
                required(`fun`, "a")
                required(annotated("Composable"), public, suspend, `fun`, "b")
                required(
                    annotated("A1"), annotated("A2"), annotated("A3"), annotated("A4"), annotated("A5"),
                    annotated("A6"), annotated("A7"), annotated("A8"), annotated("A9"), inline,
                    `fun`, "c",
                )
            } shouldBe """
                required fun{} a
                required fun{@Composable public suspend} b
                required fun{$NINE_ANNOTATIONS inline} c
            """.trimIndent()
        }

        "val・var: 修飾子 0 個・数個・10 個と型" {
            dumpOf {
                required(`val`, "a")
                required(annotated("Keep"), private, const, `val`, "B").type("String")
                required(
                    annotated("A1"), annotated("A2"), annotated("A3"), annotated("A4"), annotated("A5"),
                    annotated("A6"), annotated("A7"), annotated("A8"), annotated("A9"), lateinit,
                    `var`, "c").type("Int")
            } shouldBe """
                required val{} a
                required val{@Keep private const} B type=String
                required var{$NINE_ANNOTATIONS lateinit} c type=Int
            """.trimIndent()
        }

        "typealias: 修飾子 0 個・数個・10 個" {
            dumpOf {
                required(`typealias`, "A")
                required(annotated("Keep"), private, expect, `typealias`, "B")
                required(
                    annotated("A1"), annotated("A2"), annotated("A3"), annotated("A4"), annotated("A5"),
                    annotated("A6"), annotated("A7"), annotated("A8"), annotated("A9"), internal,
                    `typealias`, "C",
                )
            } shouldBe """
                required typealias{} A
                required typealias{@Keep private expect} B
                required typealias{$NINE_ANNOTATIONS internal} C
            """.trimIndent()
        }

        "fun interface: 修飾子 0 個・数個・10 個" {
            dumpOf {
                required(`fun`, `interface`, "AListener")
                required(annotated("Keep"), public, expect, `fun`, `interface`, "BListener")
                required(
                    annotated("A1"), annotated("A2"), annotated("A3"), annotated("A4"), annotated("A5"),
                    annotated("A6"), annotated("A7"), annotated("A8"), annotated("A9"), internal,
                    `fun`, `interface`, "CListener",
                ) { allow(`fun`, "on*") }
            } shouldBe """
                required interface{fun} AListener
                required interface{@Keep public expect fun} BListener
                required interface{$NINE_ANNOTATIONS internal fun} CListener
                  members:
                    optional fun{} on*
            """.trimIndent()
        }

        "anythingElse: 修飾子 0 個・数個・10 個" {
            dumpOf {
                allow(anythingElse)
                allow(annotated("Preview"), private, anythingElse)
                allow(
                    annotated("A1"), annotated("A2"), annotated("A3"), annotated("A4"), annotated("A5"),
                    annotated("A6"), annotated("A7"), annotated("A8"), annotated("A9"), internal,
                    anythingElse,
                )
            } shouldBe """
                anythingElse {}
                anythingElse {@Preview private}
                anythingElse {$NINE_ANNOTATIONS internal}
            """.trimIndent()
        }

        "書いた順ではなく、どの順に渡しても同じ行になる" {
            dumpOf {
                required(data, public, `class`, "A")
                required(public, data, `class`, "B")
            } shouldBe """
                required class{public data} A
                required class{public data} B
            """.trimIndent()
        }

        "戻り値に後ろの書き方をつなげる" {
            dumpOf(fileName = "HomeViewModel") {
                required(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
                    allow(public, `fun`, "on*")().returns("Unit")
                    required(`fun`, "load")("id: String", "count: Int")
                    allow(`val`, "isLoading").type("Boolean")
                }
                allow(`typealias`, "Callback")
            } shouldBe """
                required class{@HiltViewModel public} HomeViewModel supers=[ViewModel]
                  members:
                    optional fun{public} on* params=() returns=Unit
                    required fun{} load params=(id: String, count: Int)
                    optional val{} isLoading type=Boolean
                optional typealias{} Callback
            """.trimIndent()
        }
    }

    "or" - {
        "修飾子の or と種類の or は、その組み合わせのどれか" {
            dumpOf {
                allow(internal or private, data, `class` or `object`, "*")
                allow(annotated("Preview") or annotated("PreviewLightDark"), private, `fun`, "*Preview")
                allow(const, `val` or `var`, "*")
            } shouldBe """
                optional class{internal data}|object{internal data}|class{private data}|object{private data} *
                optional fun{@Preview private}|fun{@PreviewLightDark private} *Preview
                optional val{const} *
            """.trimIndent()
        }

        "付けられない種類は外れる: data は interface に付かない" {
            dumpOf { allow(data, `class` or `interface`, "*") } shouldBe "optional class{data} *"
        }

        "anythingElse の前の or" {
            dumpOf { allow(internal or private, anythingElse) } shouldBe "anythingElse {internal}|{private}"
        }
    }

    "not.allow" - {
        "禁止の行になり、except で一部を通す" {
            dumpOf {
                not.allow(public, `class`, "*Impl").except("LegacyImpl")
                not.allow(`fun`, "*")("*: Context")
                not.allow(`fun`, `interface`, "*")
                not.allow(private, `typealias`, "*")
            } shouldBe """
                prohibited class{public} *Impl except=[LegacyImpl]
                prohibited fun{} * params=(*: Context)
                prohibited interface{fun} *
                prohibited typealias{private} *
            """.trimIndent()
        }

        "クラスの { } の中の not.allow" {
            dumpOf {
                required(`class`, "A") {
                    allow(`val`, "*")
                    not.allow(public, `var`, "*").except("isEnabled")
                }
            } shouldBe """
                required class{} A
                  members:
                    optional val{} *
                    prohibited var{public} * except=[isEnabled]
            """.trimIndent()
        }

        "not.allow(…, anythingElse) は AnythingElseAfterNot" {
            shouldThrow<KatachiKtFileContentLineException> { rulesOf { not.allow(anythingElse) } }.let {
                it.problem shouldBe KtFileContentLineProblem.AnythingElseAfterNot
                it.line shouldBe "not (anything)"
            }
            shouldThrow<KatachiKtFileContentLineException> {
                rulesOf { required(`class`, "A") { not.allow(private, anythingElse) } }
            }.line shouldBe "not private (anything)"
        }
    }

    "付けられない組み合わせ" - {
        "同時に付けられない修飾子は Conflicting" {
            modifierErrorOf { required(public, internal, `class`, "X") }.let {
                it.problem shouldBe KtFileContentModifierProblem.Conflicting
                it.modifiers shouldBe listOf("public", "internal")
                it.line shouldBe "public internal class X"
            }
            modifierErrorOf {
                required(
                    annotated("A1"), annotated("A2"), annotated("A3"), annotated("A4"), annotated("A5"),
                    annotated("A6"), annotated("A7"), annotated("A8"), data, sealed,
                    `class`, "Y",
                )
            }.modifiers shouldBe listOf("sealed", "data")
        }

        "種類に付けられない修飾子は NotApplicable" {
            modifierErrorOf { required(data, `interface`, "X") }.let {
                it.problem shouldBe KtFileContentModifierProblem.NotApplicable
                it.kind shouldBe "interface"
            }
            modifierErrorOf { required(const, `fun`, "x") }.kind shouldBe "fun"
            modifierErrorOf { required(lateinit, `val`, "x") }.kind shouldBe "val"
            modifierErrorOf { required(data, `typealias`, "X") }.kind shouldBe "typealias"
        }

        "fun interface の種類に interface が無いと NotApplicable" {
            modifierErrorOf { required(`fun`, `class`, "X") }.let {
                it.problem shouldBe KtFileContentModifierProblem.NotApplicable
                it.modifiers shouldBe listOf("fun")
                it.kind shouldBe "class"
            }
            dumpOf { allow(`fun`, type, "*") } shouldBe "optional interface{fun} *"
        }

        "クラスの中だけの修飾子をファイル直下に書くと OnlyInClassBody" {
            modifierErrorOf { required(override, `fun`, "x") }.problem shouldBe KtFileContentModifierProblem.OnlyInClassBody
            modifierErrorOf { allow(protected, anythingElse) }.problem shouldBe KtFileContentModifierProblem.OnlyInClassBody
            dumpOf { required(`class`, "A") { required(override, `fun`, "x") } } shouldBe """
                required class{} A
                  members:
                    required fun{override} x
            """.trimIndent()
        }
    }
})
