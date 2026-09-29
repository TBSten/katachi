package me.tbsten.katachi.intellij.uitest.generate

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.toPropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sequences of what the user and the IDE do around the generation of two files from an entry
 * (start, an edit from outside, an unsaved edit, save, delete, Gradle and the template re-read
 * finishing or failing, the definition changing, an IDE call failing, the project closing), played
 * on the real `SingleFileGeneration` and ledger over fakes whose every wait is a gate
 * ([GenerationFlowMachine]). After each step: no content is overwritten (disk or unsaved), one
 * generation per file, the guidance stays after a failure, the ledger and the notification of the
 * spec agree, and Gradle's requests do not overlap. `-Pkatachi.pbt.scale=N` runs N times as many.
 */
class GenerationFlowPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260929L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0

    private fun config(iterations: Int) = PropTest(seed = seed, iterations = (iterations * scale).toInt().coerceAtLeast(1), shrinkingMode = ShrinkingMode.Bounded(400)).toPropTestConfig()

    // covers: 論点2, 論点6, 論点16
    @Test
    fun `どんな操作の列でも中身を上書きせず1ファイル1本で失敗後に案内が残り台帳と通知が一致する`() = runBlocking<Unit> {
        val seen = mutableSetOf<String>()
        checkAll(config(400), Arb.list(flowOpArb(), 1..45)) { ops ->
            val machine = GenerationFlowMachine()
            ops.forEach { op ->
                try {
                    machine.apply(op)
                } catch (e: IllegalStateException) {
                    throw AssertionError("${e.message}\nsteps: ${machine.log.joinToString(" -> ")}", e)
                }
            }
            seen += machine.outcomes
        }
        val missing = REACHED.filter { it !in seen }
        assertTrue("the sequences never reached: $missing (reached: $seen)", missing.isEmpty())
    }

    private companion object {
        /** Every ending the invariants are about: a test that never gets there would pass for nothing. */
        val REACHED = listOf(
            "generated", "refused", "refused unsaved", "failed Katachi", "failed ChangedMeanwhile", "failed TemplateGone",
            "failed CatalogReloadFailed", "cancelled in Gradle", "cancelled in the re-read", "ide failure save the file",
            "ide failure create the directories", "ide failure write the provisional file",
        )
    }
}
