package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlin.random.Random

/**
 * The matching over random allow lists (plan P1, properties 1 to 4 and 6): a source that follows
 * the rules passes, and each single change to it is reported exactly once.
 *
 * Every trial parses a file with Konsist, so there are fewer trials than `:katachi`'s properties
 * run; `KATACHI_PBT_ITERATIONS` and `KATACHI_PBT_SEED` change them as they do there.
 */
class KtFileContentPropertySpec : FreeSpec({
    val iterations = System.getenv("KATACHI_PBT_ITERATIONS")?.toIntOrNull()?.coerceAtMost(MAX_TRIALS) ?: DEFAULT_TRIALS
    val seed = System.getenv("KATACHI_PBT_SEED")?.toLongOrNull() ?: 20261004L

    "沿うソースは違反 0" {
        val random = Random(seed)
        repeat(iterations) { trial ->
            val declarations = random.declarations()
            withClue("trial $trial: $declarations") {
                judged(sourceOf(declarations)) { declarations.forEach { line(it) } }.shouldBeEmpty()
            }
        }
    }

    "列挙していない宣言を 1 つ足すと、その 1 件" {
        val random = Random(seed + 1)
        repeat(iterations) { trial ->
            val declarations = random.declarations()
            val extra = GeneratedDeclaration(GeneratedKind.Class, "Extra", GeneratedVisibility.None)
            val withExtra = declarations.toMutableList().apply { add(random.nextInt(0, size + 1), extra) }
            withClue("trial $trial: $withExtra") {
                val found = judged(sourceOf(withExtra)) { declarations.forEach { line(it) } }
                found shouldHaveSize 1
                found.single() shouldStartWith "Extra (line "
            }
        }
    }

    "必須の宣言を 1 つ消すと、足りないが 1 件" {
        val random = Random(seed + 2)
        repeat(iterations) { trial ->
            val declarations = random.declarations()
            val removed = declarations.random(random)
            withClue("trial $trial: $declarations without $removed") {
                val found = judged(sourceOf(declarations - removed)) { declarations.forEach { line(it) } }
                found shouldHaveSize 1
                found.single() shouldStartWith "${removed.name} | Missing: "
            }
        }
    }

    "求められた可視性を 1 つ変えると 1 件（余計と足りないの 2 件にならない）" {
        val random = Random(seed + 3)
        repeat(iterations) { trial ->
            val declarations = random.declarations().map { if (it.visibility == GeneratedVisibility.None) it.copy(visibility = GeneratedVisibility.Private) else it }
            val changed = declarations.random(random)
            val other = if (changed.visibility == GeneratedVisibility.Private) GeneratedVisibility.Internal else GeneratedVisibility.Private
            val source = sourceOf(declarations.map { if (it == changed) it.copy(visibility = other) else it })
            withClue("trial $trial: $changed made ${other.keyword}") {
                val found = judged(source) { declarations.forEach { line(it) } }
                found shouldHaveSize 1
                found.single() shouldStartWith "${changed.name} (line "
                found.single().substringAfter("Differs in: ") shouldBe "modifiers (missing: ${changed.visibility.keyword})"
            }
        }
    }

    "宣言の順を入れ替え、空行やコメントを入れても、行番号のほかは同じ結果" {
        val random = Random(seed + 4)
        repeat(iterations) { trial ->
            val declarations = random.declarations()
            val extra = GeneratedDeclaration(GeneratedKind.Function, "extra", GeneratedVisibility.None)
            val rules: me.tbsten.katachi.konsist.declarations.KtFileContentScope.() -> Unit = { declarations.drop(1).forEach { line(it) } }
            val plain = judged(sourceOf(declarations + extra), block = rules).map(::withoutLines).sorted()
            val shuffled = judged(sourceOf((declarations + extra).shuffled(random), noise = random), block = rules).map(::withoutLines).sorted()
            withClue("trial $trial: $declarations") { shuffled shouldBe plain }
        }
    }
})

/** Trials per property unless `KATACHI_PBT_ITERATIONS` says otherwise. */
private const val DEFAULT_TRIALS: Int = 25

/** At most this many, however many `KATACHI_PBT_ITERATIONS` asks for: each one parses a file. */
private const val MAX_TRIALS: Int = 200

private fun withoutLines(line: String): String = line.replace(Regex(""" \(line \d+\)"""), "")
