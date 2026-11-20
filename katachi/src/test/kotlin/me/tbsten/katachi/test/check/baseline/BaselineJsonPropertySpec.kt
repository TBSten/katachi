package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.random.Random
import me.tbsten.katachi.check.KatachiInvalidBaselineFileException
import me.tbsten.katachi.check.KatachiUnsupportedBaselineVersionException
import me.tbsten.katachi.check.internal.BaselineKey
import me.tbsten.katachi.check.internal.BaselineLedger
import me.tbsten.katachi.check.internal.baselineEntryLine
import me.tbsten.katachi.check.internal.parseBaseline
import me.tbsten.katachi.check.internal.renderBaseline
import me.tbsten.katachi.test.pbt.checkTrials
import me.tbsten.katachi.test.pbt.pbtSeed
import me.tbsten.katachi.test.pbt.pbtTrials
import me.tbsten.katachi.test.pbt.pick
import me.tbsten.katachi.test.pbt.without

/*
 * The baseline file over random ledgers: what katachi writes, it reads back as the same ledger, in
 * one spelling whatever order the ledger was built in, and the SARIF report's search text is a line
 * of it. A file damaged at random is refused with katachi's own exceptions, never anything else.
 *
 * The strings are drawn from pieces that are hard for a hand-written JSON writer and reader: quotes,
 * backslashes, control characters, a surrogate pair, `\u` lookalikes and the merge conflict markers
 * the reader looks for before it parses.
 */

private val PIECES: List<String> = listOf(
    "a", "Foo.kt", "src/main", "日本", "😀", " ", "\"", "\\", "\\u0041", "\n", "\r", "\t", "\u0000", "\u001f", "\u007f", "/", ",", ":",
    "{", "}", "[", "]", "<<<<<<<", "=======", ">>>>>>>", " ", "﻿", "-",
)

private fun Random.text(nonEmpty: Boolean = false): String =
    List(nextInt(if (nonEmpty) 1 else 0, 5)) { pick(PIECES) }.joinToString("")

private fun Random.key(): BaselineKey = BaselineKey(
    check = pick(listOf("a.Check", "b.Check", "layout", text(nonEmpty = true))),
    rule = pick(listOf("ByPath", "ByDeclaration", "NotHeldBack", text())),
    path = text(),
    role = if (nextBoolean()) text() else null,
    constraint = if (nextInt(3) == 0) text() else null,
    declaration = if (nextInt(3) == 0) text() else null,
)

private fun Random.ledger(): Map<BaselineKey, Int> =
    List(nextInt(0, 8)) { key() to pick(listOf(1, 1, 2, 3, 1000, Int.MAX_VALUE)) }.toMap()

private fun smallerLedgers(entries: Map<BaselineKey, Int>): List<Map<BaselineKey, Int>> {
    val list = entries.toList()
    return list.indices.map { list.without(it).toMap() }
}

private fun Map<BaselineKey, Int>.describe(): String = entries.joinToString("\n") { (key, count) -> "  $key x$count" }

private fun parse(text: String): Map<BaselineKey, Int> = parseBaseline(text, BASELINE_URI).entries

class BaselineJsonPropertySpec : FreeSpec({
    "書いたベースラインを読むと同じ台帳に戻り、書き直しても同じ文字列になる" {
        checkTrials(
            "render then parse",
            trials = pbtTrials(1000),
            seed = pbtSeed(20261126L),
            generate = { random -> random.ledger() },
            shrinks = ::smallerLedgers,
            describe = { it.describe() },
        ) { entries ->
            val text = renderBaseline(BaselineLedger(entries))
            withClue(text) {
                parse(text) shouldBe entries
                renderBaseline(BaselineLedger(parse(text))) shouldBe text
            }
        }
    }

    "台帳を組んだ順番が違っても、書かれる文字列は同じ" {
        checkTrials(
            "render does not depend on order",
            trials = pbtTrials(500),
            seed = pbtSeed(20261127L),
            generate = { random -> random.ledger() to random.nextLong() },
            shrinks = { (entries, seed) -> smallerLedgers(entries).map { it to seed } },
            describe = { it.first.describe() },
        ) { (entries, seed) ->
            val shuffled = entries.toList().shuffled(Random(seed)).toMap()
            renderBaseline(BaselineLedger(shuffled)) shouldBe renderBaseline(BaselineLedger(entries))
        }
    }

    "SARIF が探す 1 件ぶんの文字列は、書かれたベースラインの中にそのまま出てくる" {
        checkTrials(
            "baselineEntryLine is a line of the file",
            trials = pbtTrials(500),
            seed = pbtSeed(20261128L),
            generate = { random -> random.ledger() },
            shrinks = ::smallerLedgers,
            describe = { it.describe() },
        ) { entries ->
            val text = renderBaseline(BaselineLedger(entries))
            for ((key, count) in entries) text shouldContain baselineEntryLine(key, count)
        }
    }

    "書いたベースラインを 1 か所壊しても、読めるか katachi の例外で断られるかのどちらか" {
        val damage = listOf("\"", "\\", "{", "}", "[", "]", ",", ":", "0", "-1", "1e3", "null", "true", "<<<<<<< ", "\n", " ", "\uD83D")
        checkTrials(
            "damaged files are refused cleanly",
            trials = pbtTrials(3000),
            seed = pbtSeed(20261129L),
            generate = { random ->
                val text = renderBaseline(BaselineLedger(random.ledger()))
                val at = random.nextInt(text.length + 1)
                when (random.nextInt(3)) {
                    0 -> text.removeRange(at, minOf(text.length, at + random.nextInt(1, 4)))
                    1 -> text.substring(0, at) + random.pick(damage) + text.substring(at)
                    else -> text.substring(0, at)
                }
            },
            shrinks = { text -> text.indices.map { text.removeRange(it, it + 1) } },
        ) { text ->
            try {
                parse(text)
            } catch (_: KatachiInvalidBaselineFileException) {
                // Refused, as it should be.
            } catch (_: KatachiUnsupportedBaselineVersionException) {
                // A damaged version number can read as a newer format.
            }
        }
    }
})
