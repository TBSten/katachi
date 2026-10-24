package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.konsist.declarations.DeclarationListScope
import me.tbsten.katachi.konsist.declarations.DeclarationVocabulary
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentModifierProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.Modifiers

/*
 * Which modifier can be on which kind, and where. The table below is written out again from the
 * design's table rather than read from the implementation, so a slip on either side shows.
 */

private val EVERY_KIND: Set<String> = setOf("class", "interface", "object", "fun", "val", "var", "typealias")

/** The kinds each modifier can be on, as the design lists them. */
private val APPLICABLE: Map<String, Set<String>> = buildMap {
    for (it in listOf("public", "internal", "protected", "private", "expect", "actual")) put(it, EVERY_KIND)
    put("data", setOf("class", "object"))
    put("sealed", setOf("class", "interface"))
    for (it in listOf("enum", "value", "annotation")) put(it, setOf("class"))
    for (it in listOf("abstract", "open")) put(it, setOf("class", "fun", "val", "var"))
    put("inner", setOf("class"))
    put("companion", setOf("object"))
    put("override", setOf("fun", "val", "var"))
    for (it in listOf("operator", "infix", "suspend", "tailrec")) put(it, setOf("fun"))
    put("inline", setOf("fun", "val", "var"))
    put("external", setOf("class", "interface", "object", "fun", "val", "var"))
    put("const", setOf("val"))
    put("lateinit", setOf("var"))
}

/** Modifiers only a member can carry. */
private val CLASS_BODY_ONLY: Set<String> = setOf("protected", "inner", "companion", "override")

/** Each of the 24 modifier words, by its keyword. */
private val MODIFIERS: Map<String, DeclarationVocabulary.() -> Modifiers> = mapOf(
    "public" to { public }, "protected" to { protected }, "private" to { private }, "internal" to { internal },
    "expect" to { expect }, "actual" to { actual }, "abstract" to { abstract }, "open" to { open },
    "sealed" to { sealed }, "const" to { const }, "external" to { external }, "override" to { override },
    "lateinit" to { lateinit }, "tailrec" to { tailrec }, "suspend" to { suspend }, "inner" to { inner },
    "enum" to { enum }, "annotation" to { annotation }, "companion" to { companion }, "inline" to { inline },
    "value" to { value }, "infix" to { infix }, "operator" to { operator }, "data" to { data },
)

/** Writes a line of each kind, by its keyword, named `X`, with the modifier given in front. */
private val KINDS: Map<String, DeclarationListScope.(Modifiers) -> Unit> = mapOf(
    "class" to { required(it, `class`, "X") },
    "interface" to { required(it, `interface`, "X") },
    "object" to { required(it, `object`, "X") },
    "fun" to { required(it, `fun`, "X") },
    "val" to { required(it, `val`, "X") },
    "var" to { required(it, `var`, "X") },
    "typealias" to { required(it, `typealias`, "X") },
)

/** What writing [modifier] in front of [kind] comes to, as one word, in the file or in a class. */
private fun outcomeOf(modifier: String, kind: String, inClassBody: Boolean): String {
    val line: KtFileContentScope.() -> Unit = {
        val write: DeclarationListScope.() -> Unit = { KINDS.getValue(kind)(this, MODIFIERS.getValue(modifier)(this)) }
        if (inClassBody) required(`class`, "Host") { write() } else write()
    }
    return try {
        val rules = rulesOf(block = line)
        val written = if (inClassBody) rules.firstDeclaration.members?.lines?.single() else rules.firstDeclaration
        "ok ${written?.shapes?.joinToString { it.kind.keyword }}"
    } catch (error: KatachiKtFileContentModifierException) {
        "${error.problem} ${error.modifiers} ${error.kind}"
    } catch (error: KatachiKtFileContentLineException) {
        "${error.problem}"
    }
}

/** What the design says writing [modifier] in front of [kind] comes to. */
private fun expectedOutcomeOf(modifier: String, kind: String, inClassBody: Boolean): String = when {
    !inClassBody && modifier in CLASS_BODY_ONLY -> "${KtFileContentModifierProblem.OnlyInClassBody} [$modifier] null"
    !inClassBody && modifier in setOf("abstract", "open") && kind in setOf("fun", "val", "var") ->
        "${KtFileContentModifierProblem.OnlyInClassBody} [$modifier] $kind"
    inClassBody && kind == "typealias" -> "${KtFileContentLineProblem.TypeAliasInClassBody}"
    kind in APPLICABLE.getValue(modifier) -> "ok $kind"
    else -> "${KtFileContentModifierProblem.NotApplicable} [$modifier] $kind"
}

class ModifierValidationSpec : FreeSpec({
    "付けられる種類の表" - {
        for (modifier in MODIFIERS.keys) {
            "$modifier をファイル直下とクラスの中の各種類に付けたときの結果が表のとおり" {
                assertSoftly {
                    for (kind in KINDS.keys) {
                        for (inClassBody in listOf(false, true)) {
                            withClue("$modifier $kind, inClassBody=$inClassBody") {
                                outcomeOf(modifier, kind, inClassBody) shouldBe expectedOutcomeOf(modifier, kind, inClassBody)
                            }
                        }
                    }
                }
            }
        }

        "修飾子 fun は allow(`fun`, `interface`, …) だけで付き、interface になる" {
            rulesOf { required(`fun`, `interface`, "X") }.dump() shouldBe "required interface{fun} X"
        }
    }

    "付けられる種類に絞る" - {
        "allow(sealed, type, …) は sealed class か sealed interface になる" {
            rulesOf { required(sealed, type, "X") }.dump() shouldBe "required class{sealed}|interface{sealed} X"
        }

        "利用者が組んだ種類も type と同じ規則で絞る" {
            rulesOf { required(data, `class` or `interface`, "X") }.dump() shouldBe "required class{data} X"
            rulesOf { required(const, `val` or `var`, "X") }.dump() shouldBe "required val{const} X"
        }

        "or の片方だけが付けられる組み合わせなら、付けられる方だけが残る" {
            rulesOf { required(data or sealed, `interface`, "X") }.dump() shouldBe "required interface{sealed} X"
        }

        "allow(data, `interface`, …) は候補が無くなり、data と interface を名指しする" {
            val error = shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(data, `interface`, fileName) } }

            error.problem shouldBe KtFileContentModifierProblem.NotApplicable
            error.modifiers shouldBe listOf("data")
            error.kind shouldBe "interface"
            error.line shouldBe "data interface Sample"
            error.message shouldBe """
                Line "data interface Sample" in ktFileContent { } at ${error.declaredAt} cannot match anything.
                data can be put on class or object only, so no declaration has this shape.
                Remove data, or use class or object.
            """.trimIndent()
        }

        "allow(suspend, `fun`, `interface`, …) は suspend を名指しする" {
            val error = shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(suspend, `fun`, `interface`, "X") } }

            error.modifiers shouldBe listOf("suspend")
            error.kind shouldBe "interface"
        }
    }

    "組の中の衝突" - {
        "allow(public, internal, …) は Conflicting" {
            val error = shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(public, internal, `class`, "X") } }

            error.problem shouldBe KtFileContentModifierProblem.Conflicting
            error.modifiers shouldBe listOf("public", "internal")
            error.kind shouldBe null
            error.message shouldBe """
                Line "public internal class X" in ktFileContent { } at ${error.declaredAt} cannot match anything.
                public and internal cannot be on one declaration together.
                Write allow(public or internal, ...) to accept either, or keep only one of them.
            """.trimIndent()
        }

        "data と sealed、abstract と open、expect と actual もそれぞれ 1 つまで" {
            shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(data, sealed, `class`, "X") } }
                .modifiers shouldBe listOf("sealed", "data")
            shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(`class`, "H") { required(abstract, open, `fun`, "x") } } }
                .modifiers shouldBe listOf("abstract", "open")
            shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(expect, actual, `fun`, "x") } }
                .modifiers shouldBe listOf("expect", "actual")
        }

        "or でつないだ可視性は衝突しない" {
            rulesOf { required(public or internal, `class`, "X") }.dump() shouldBe "required class{public}|class{internal} X"
        }
    }

    "置き場" - {
        "ファイル直下の override は OnlyInClassBody。クラスの中なら通る" {
            val error = shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(override, `fun`, "invoke") } }

            error.problem shouldBe KtFileContentModifierProblem.OnlyInClassBody
            error.message shouldBe """
                Line "override fun invoke(...)" in ktFileContent { } at ${error.declaredAt} cannot match anything.
                override is only valid on a member of a class, and this line is written directly in the file.
                Move the line into the { } of a class line.
            """.trimIndent()
            rulesOf { required(`class`, "H") { required(override, `fun`, "invoke") } }.firstDeclaration.members?.lines?.size shouldBe 1
        }

        "ファイル直下の allow(abstract, `fun`, …) は種類も名指しする" {
            val error = shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(abstract, `fun`, "x") } }

            error.problem shouldBe KtFileContentModifierProblem.OnlyInClassBody
            error.kind shouldBe "fun"
            error.message?.lines()?.get(1) shouldBe
                "abstract on a fun is only valid inside a class, and this line is written directly in the file."
        }

        "ファイル直下の allow(abstract, `class`, …) は通る" {
            rulesOf { required(abstract, `class`, "X") }.dump() shouldBe "required class{abstract} X"
        }
    }

    "anythingElse の修飾子" - {
        "衝突と置き場だけを確かめる" {
            shouldThrow<KatachiKtFileContentModifierException> { rulesOf { allow(public, internal, anythingElse) } }
                .problem shouldBe KtFileContentModifierProblem.Conflicting
            shouldThrow<KatachiKtFileContentModifierException> { rulesOf { allow(override, anythingElse) } }
                .problem shouldBe KtFileContentModifierProblem.OnlyInClassBody
            rulesOf { allow(abstract, anythingElse) }.dump() shouldBe "anythingElse {abstract}"
            rulesOf { required(`class`, "H") { allow(override, anythingElse) } }.dump() shouldBe """
                required class{} H
                  members:
                    anythingElse {override}
            """.trimIndent()
        }
    }

    "文面" - {
        "例外の文面は ASCII だけ" {
            val messages = listOf(
                shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(data, `interface`, "X") } },
                shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(public, private, `class`, "X") } },
                shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(inner, `class`, "X") } },
                shouldThrow<KatachiKtFileContentModifierException> { rulesOf { required(open, `val`, "x") } },
            ).map { it.message.orEmpty() }

            for (message in messages) {
                message.filter { it.code > 127 } shouldBe ""
                message shouldNotContain "null"
            }
        }
    }
})
