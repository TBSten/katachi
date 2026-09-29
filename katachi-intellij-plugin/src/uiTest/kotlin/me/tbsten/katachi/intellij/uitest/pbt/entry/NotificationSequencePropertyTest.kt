package me.tbsten.katachi.intellij.uitest.pbt.entry

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.FileContentState
import org.junit.Test

/**
 * Sequences of what the user and the IDE do to three files' editor notifications: open, close,
 * reopen, empty or fill, x, the four settings, the index going Loading -> Ready and gaining or
 * losing matches, a generation starting, succeeding or failing, and the project service being
 * made again. After each step every open file's panel must equal the plain model's, and a step on
 * one file must not change another's ([NotificationMachine]).
 * `-Pkatachi.pbt.scale=N` runs N times as many sequences.
 */
class NotificationSequencePropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260929L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0

    private fun config(iterations: Int) = PropTestConfig(seed = seed, iterations = (iterations * scale).toInt().coerceAtLeast(1), shrinkingMode = ShrinkingMode.Bounded(400))

    private val opArb: Arb<NotificationOp> = arbitrary { rs ->
        val r = rs.random
        val file = r.nextInt(3)
        // Weighted: mostly things a user does to files, sometimes the IDE-wide ones.
        when (r.nextInt(100)) {
            in 0..17 -> NotificationOp.Open(file)
            in 18..27 -> NotificationOp.Close(file)
            in 28..37 -> NotificationOp.Reopen(file)
            in 38..47 -> NotificationOp.SetContent(file, FileContentState.entries[r.nextInt(2)])
            in 48..55 -> NotificationOp.Dismiss(file)
            in 56..62 -> NotificationOp.Toggle(r.nextInt(4))
            in 63..69 -> NotificationOp.Availability(EntryAvailability.entries[r.nextInt(4)])
            in 70..76 -> NotificationOp.AddMatch(file)
            in 77..80 -> NotificationOp.RemoveMatch(file)
            in 81..86 -> NotificationOp.GenerationStarts(file)
            in 87..91 -> NotificationOp.GenerationSucceeds(file)
            in 92..96 -> NotificationOp.GenerationFails(file)
            else -> NotificationOp.RecreateService
        }
    }

    // covers: 論点3, 論点8, 論点15, 論点22
    @Test
    fun `どんな操作の列でも表示はモデルの判定と一致し別のファイルの表示は変わらない`() = runBlocking<Unit> {
        checkAll(config(400), Arb.list(opArb, 1..40)) { ops ->
            val machine = NotificationMachine()
            ops.forEach { op ->
                try {
                    machine.apply(op)
                } catch (e: IllegalStateException) {
                    throw AssertionError("${e.message}\nsteps: ${machine.log.joinToString(" -> ")}", e)
                }
            }
        }
    }
}
