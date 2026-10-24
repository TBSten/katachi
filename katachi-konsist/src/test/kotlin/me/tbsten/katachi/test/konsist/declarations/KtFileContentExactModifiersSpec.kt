@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.failureOf
import me.tbsten.katachi.konsist.declarations.internal.judgeKtFile
import me.tbsten.katachi.konsist.declarations.internal.renderRule
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * A declaration line matches the modifiers of the Kotlin source exactly: a modifier the line does
 * not write may not be on the declaration, unless the line makes it `.optional()`. `public` and
 * `final` count as written. Annotations, `allow(..., anythingElse)` and `not.allow(...)` keep
 * asking only for what they write.
 */

/** [body] as a file of package `com.example`: its first line is line 3. */
private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

/** The fixes [block] gives to the failures of [source], in the order the report lists them. */
private fun fixesOf(source: String, block: KtFileContentScope.() -> Unit): List<List<String>> {
    val rules = rulesOf(fileName = "Sample", block = block)
    return judgeKtFile(factsOf(source), rules).map { failureOf(it, SAMPLE_PATH, rules) }.map { failure ->
        failure.howToFix.map { fix -> (listOf(fix.text) + fix.code).joinToString(" ") }
    }
}

class KtFileContentExactModifiersSpec : FreeSpec({
    "書かなかった修飾子" - {
        "書かなかった修飾子が付いた宣言には当たらない" {
            judged(kt("suspend fun execute() {}")) { allow(`fun`, "execute") } shouldBe listOf(
                "execute (line 3) | Not allowed: suspend fun execute() | Expected: fun execute(...) | " +
                    "Differs in: modifiers (has suspend, which the line does not allow)",
            )
        }

        "書いた修飾子だけの宣言に当たり、足りなければ今までどおり足りないと言う" {
            judged(kt("suspend fun load() {}")) { allow(suspend, `fun`, "load") }.shouldBeEmpty()
            judged(kt("fun load() {}")) { allow(suspend, `fun`, "load") } shouldBe listOf(
                "load (line 3) | Not allowed: fun load() | Expected: suspend fun load(...) | Differs in: modifiers (missing: suspend)",
            )
        }

        "余計な修飾子がいくつもあれば Kotlin の順に並べる" {
            judged(kt("suspend inline fun execute() {}")) { allow(`fun`, "execute") }.single().let {
                it.substringAfter("Differs in: ") shouldBe "modifiers (has suspend, inline, which the line does not allow)"
            }
        }

        "required の行も同じ読み方で、余計な修飾子のある宣言は形違い" {
            judged(kt("open class Sample")) { required(`class`, fileName) } shouldBe listOf(
                "Sample (line 3) | Not allowed: open class Sample | Expected: class Sample | " +
                    "Differs in: modifiers (has open, which the line does not allow)",
            )
        }

        "fun interface の fun も修飾子なので、fun を書かない interface の行には当たらない" {
            judged(kt("fun interface Sample { fun run() }")) { allow(`interface`, fileName) }.single().let {
                it.substringAfter("Differs in: ") shouldBe "modifiers (has fun, which the line does not allow)"
            }
            judged(kt("fun interface Sample { fun run() }")) { allow(`fun`, `interface`, fileName) }.shouldBeEmpty()
            judged(kt("interface Sample")) { allow(`fun`, `interface`, fileName) }.single().let {
                it.substringAfter("Differs in: ") shouldBe "modifiers (missing: fun)"
            }
        }

        "fun interface の余計な fun には .optional() ではなく `fun`, `interface` と書く直し方を出す" {
            for (line in listOf<KtFileContentScope.() -> Unit>({ allow(`interface`, fileName) }, { allow(`class` or `interface`, fileName) })) {
                fixesOf(kt("fun interface Sample { fun run() }"), line).single().map { it.substringBefore(" at ") }.take(2) shouldBe listOf(
                    "Write `fun`, `interface` in the line",
                    "Remove fun from the declaration",
                )
            }
            fixesOf(kt("internal fun interface Sample { fun run() }")) { allow(`interface`, fileName) }.single()
                .map { it.substringBefore(" at ") }.take(3) shouldBe listOf(
                    "Add internal.optional() to the line",
                    "Write `fun`, `interface` in the line",
                    "Remove internal, fun from the declaration",
                )
        }
    }

    "Kotlin の既定の修飾子" - {
        "修飾子を書かない行は class X にも public class X にも final class X にも当たる" {
            judged(kt("class A\npublic class B\nfinal class C")) { allow(`class`, "*") }.shouldBeEmpty()
        }

        "修飾子を書かない行は open class X にも internal class X にも当たらない" {
            judged(kt("open class A\ninternal class B")) { allow(`class`, "*") } shouldBe listOf(
                "A (line 3) | Not allowed: open class A",
                "B (line 4) | Not allowed: internal class B",
            )
        }

        "allow(public, …) と allow(…) は同じ宣言に当たる" {
            val source = kt("class A\npublic class B\ninternal class C\nopen class D")
            judged(source) { allow(public, `class`, "*") } shouldBe judged(source) { allow(`class`, "*") }
        }
    }

    ".optional() の修飾子" - {
        "suspend.optional() は suspend が付いていても付いていなくても当たる" {
            judged(kt("fun execute() {}")) { allow(suspend.optional(), `fun`, "execute") }.shouldBeEmpty()
            judged(kt("suspend fun execute() {}")) { allow(suspend.optional(), `fun`, "execute") }.shouldBeEmpty()
            judged(kt("inline fun execute() {}")) { allow(suspend.optional(), `fun`, "execute") }.single().let {
                it.substringAfter("Differs in: ") shouldBe "modifiers (has inline, which the line does not allow)"
            }
        }

        "(internal or private).optional() は可視性なし・internal・private のどれにも当たり、protected には当たらない" {
            judged(kt("class Sample {\n    fun a() {}\n    internal fun b() {}\n    private fun c() {}\n    protected fun d() {}\n}")) {
                required(`class`, fileName) { allow((internal or private).optional(), `fun`, "*") }
            } shouldBe listOf("Sample.d (line 7) | Not allowed: protected fun d() in class Sample")
        }

        "必須の修飾子と並べると、必須のほうは要る" {
            val line: KtFileContentScope.() -> Unit = { allow((internal or private).optional(), data, `class`, "*") }
            judged(kt("data class A(val a: Int)\ninternal data class B(val b: Int)"), block = line).shouldBeEmpty()
            judged(kt("internal class C"), block = line).single().let {
                it shouldBe "C (line 3) | Not allowed: internal class C"
            }
        }

        "or の束ねは少なくとも 1 つで、束ねたものがいくつ付いていても当たる" {
            val line: KtFileContentScope.() -> Unit = { allow(suspend or inline, `fun`, "*") }
            judged(kt("suspend fun a() {}\ninline fun b() {}\nsuspend inline fun c() {}"), block = line).shouldBeEmpty()
            judged(kt("fun d() {}\nsuspend operator fun e() {}"), block = line) shouldBe listOf(
                "d (line 3) | Not allowed: fun d()",
                "e (line 4) | Not allowed: suspend operator fun e()",
            )
        }
    }

    "今のままのもの" - {
        "アノテーションは書いたものが要り、書かなかったものは問わない" {
            judged(kt("@Inject @Suppress(\"unused\") class FooImpl")) { allow(annotated("Inject"), `class`, "*Impl") }.shouldBeEmpty()
        }

        "allow(private, anythingElse) は private が付いていればほかの修飾子は問わない" {
            judged(kt("private suspend fun helper() {}\nprivate inline fun other() {}")) { allow(private, anythingElse) }.shouldBeEmpty()
        }

        "not.allow(…) は書いた修飾子が付いていれば、ほかの修飾子があっても禁止する" {
            judged(kt("internal class XManager\nclass Other")) {
                allow(internal.optional(), `class`, "*")
                not.allow(`class`, "*Manager")
            } shouldBe listOf("XManager (line 3) | Not allowed: internal class XManager | Prohibited by: not class *Manager")
        }
    }

    "出力" - {
        "余計な修飾子の直し方は、行に .optional() を足すか、宣言から外すか" {
            fixesOf(kt("suspend inline fun execute() {}")) { allow(`fun`, "execute") }.single().let { fixes ->
                fixes[0] shouldBe "Add suspend.optional(), inline.optional() to the line at ${fixes[0].substringAfter(" at ")}"
                fixes[0].substringAfter(" at ").startsWith("KtFileContentExactModifiersSpec.kt:") shouldBe true
                fixes[1] shouldBe "Remove suspend, inline from the declaration"
            }
        }

        "宣言を直す案内は .optional() の修飾子を落とした Kotlin の宣言の形で出す" {
            fixesOf(kt("inline fun Sample() {}")) { required(suspend.optional(), `fun`, fileName) }.single()
                .first { it.startsWith("Change the declaration to: ") } shouldBe "Change the declaration to: fun Sample(...)"
            judged(kt("inline fun Sample() {}")) { required(suspend.optional(), `fun`, fileName) }.single()
                .substringAfter("Expected: ").substringBefore(" |") shouldBe "suspend.optional() fun Sample(...)"
        }

        "余計な修飾子も数えて、行の候補のうち近いほうと比べる" {
            // Without counting `abstract` as one more, data (written first) and open would tie.
            judged(kt("abstract class Sample")) { required(data or open, `class`, fileName) }.single()
                .substringAfter("Differs in: ") shouldBe "modifiers (missing: open)"
        }

        "足りないだけの宣言には .optional() の直し方を出さない" {
            fixesOf(kt("fun load() {}")) { allow(suspend, `fun`, "load") }.single().none { it.startsWith("Add ") } shouldBe true
        }

        "public を求める行に private の宣言は足りないとだけ言い、private を余計な修飾子にしない" {
            judged(kt("private fun load() {}")) { required(public, `fun`, "load") }.single().let {
                it.substringAfter("Differs in: ") shouldBe "modifiers (missing: public)"
            }
            fixesOf(kt("private fun load() {}")) { required(public, `fun`, "load") }.single().none { it.startsWith("Add ") } shouldBe true
        }

        "Found のある足りない宣言にも、余計な修飾子の直し方を出す" {
            fixesOf(kt("open class Sample")) {
                required(`class`, fileName)
                allow(anythingElse)
            }.single().take(2).map { it.substringBefore(" at ") } shouldBe listOf(
                "Add open.optional() to the line",
                "Remove open from the declaration",
            )
        }

        "余計な宣言を許す直し方は、宣言の修飾子をすべて書いた行" {
            fixesOf(kt("internal data class Extra(val a: Int)")) { required(`fun`, "x") }.first().last().let {
                it.substringAfterLast(": ") shouldBe "allow(internal, data, `class`, \"Extra\")"
            }
            fixesOf(kt("fun interface Listener { fun on() }")) { required(`fun`, "x") }.first().last().let {
                it.substringAfterLast(": ") shouldBe "allow(`fun`, `interface`, \"Listener\")"
            }
            fixesOf(kt("class Plain")) { required(`fun`, "x") }.first().last().let {
                it.substringAfterLast(": ") shouldBe "allow(`class`, \"Plain\")"
            }
        }

        "規則の描画では .optional() の修飾子を書いたとおりに出す" {
            rulesOf {
                allow(suspend.optional(), `fun`, "execute")
                allow((internal or private).optional(), data, `class`, "*")
                allow(internal or private, suspend.optional(), `fun`, "run")
            }.declarations?.lines.orEmpty().map(::renderRule) shouldBe listOf(
                "suspend.optional() fun execute(...)",
                "(internal or private).optional() data class *",
                "(internal or private) suspend.optional() fun run(...)",
            )
        }

        "役割ページの表でも .optional() の修飾子を書いたとおりに出す" {
            val page = rolePageOf {
                "UseCase".role {
                    example("GetUserUseCase")
                    layout { "src" / "*UseCase".ktFile() }
                    ktFileContent {
                        required(`class`, fileName) { required(suspend.optional(), operator, `fun`, "invoke") }
                    }
                }
            }.getValue("UseCase.md")
            page.lines() shouldContain "| `suspend.optional() operator fun invoke(...)` | required |"
        }
    }
})
