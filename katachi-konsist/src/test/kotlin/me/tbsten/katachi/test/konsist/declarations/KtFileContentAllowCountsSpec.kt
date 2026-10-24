package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.DeclarationAllow
import me.tbsten.katachi.konsist.declarations.DeclarationListScope
import me.tbsten.katachi.konsist.declarations.Modifiers

/**
 * Every count of modifiers `allow(...)` takes, none to ten, for every kind, on an allowed line and
 * after `not.`: each count is an overload of its own, so each one is called once here. The
 * modifiers are annotations `@A1` to `@An`, which every kind can carry, so the dump shows that
 * each of them, in its place, reached the line.
 */

/** `@A1` to `@A[count]` as values of this block. */
private fun DeclarationListScope.annotations(count: Int): List<Modifiers> = (1..count).map { annotated("A$it") }

/** How the dump prints the annotations of [count]: `@A1 @A2`, then [rest] after them. */
private fun dumped(count: Int, vararg rest: String): String =
    ((1..count).map { "@A$it" } + rest).joinToString(" ", "{", "}")

/** A line of one kind written on `on` with `count` modifiers, and the dump it gives with a status. */
private class KindLine(
    val write: DeclarationListScope.(on: DeclarationAllow, count: Int) -> Unit,
    val dump: (count: Int, status: String) -> String,
)

private val LINES: List<KindLine> = listOf(
    KindLine({ on, n -> allowType(on, annotations(n), `class`, "X") }) { n, status -> "$status class${dumped(n)} X" },
    KindLine({ on, n -> allowFunInterface(on, annotations(n), `fun`, `interface`, "L") }) { n, status ->
        "$status interface${dumped(n, "fun")} L"
    },
    KindLine({ on, n -> allowFunction(on, annotations(n), `fun`, "f") }) { n, status -> "$status fun${dumped(n)} f" },
    KindLine({ on, n -> allowProperty(on, annotations(n), `val`, "v", "Int") }) { n, status -> "$status val${dumped(n)} v type=Int" },
    KindLine({ on, n -> allowTypeAlias(on, annotations(n), `typealias`, "T") }) { n, status -> "$status typealias${dumped(n)} T" },
)

class KtFileContentAllowCountsSpec : FreeSpec({
    "修飾子の数ごとの多重定義" - {
        for (count in 0..10) {
            "修飾子 $count 個で、どの種類の行もその修飾子をすべて持つ" {
                dumpOf { LINES.forEach { it.write(this, this, count) } } shouldBe
                    LINES.joinToString("\n") { it.dump(count, "required") }
            }

            "修飾子 $count 個の not.allow(...) は、どの種類も禁止の行になる" {
                dumpOf { LINES.forEach { it.write(this, not, count) } } shouldBe
                    LINES.joinToString("\n") { it.dump(count, "prohibited") }
            }

            "修飾子 $count 個の allow(..., anythingElse)" {
                dumpOf { allowAnythingElse(this, annotations(count), anythingElse) } shouldBe "anythingElse ${dumped(count)}"
            }

            "修飾子 $count 個でクラスの { } の中にも書ける" {
                dumpOf {
                    allowType(this, emptyList(), `class`, "Host", body = {
                        allowFunction(this, annotations(count), `fun`, "f")
                    })
                } shouldBe """
                    required class{} Host
                      members:
                        required fun${dumped(count)} f
                """.trimIndent()
            }
        }
    }
})
