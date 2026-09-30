package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.toPropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.constant
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Sequences of operations on the real ViewModel, with every state they reach checked and drawn, each
 * sequence in Japanese or English ([languageArb]). See [ScreenMachine] for how a sequence plays and
 * [stateViolationsOf] for what must hold.
 *
 * The seed is fixed so that `test` is repeatable; `-Pkatachi.pbt.seed=N` tries another and
 * `-Pkatachi.pbt.scale=N` runs N times as many sequences (the defaults keep `test` short). A failure
 * prints the shrunk catalog and operations, and the steps that led to it.
 */
class ScreenPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260927L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0

    private fun config(defaultIterations: Int) = PropTest(
        seed = seed,
        iterations = (defaultIterations * scale).toInt().coerceAtLeast(1),
        shrinkingMode = ShrinkingMode.Bounded(400),
    ).toPropTestConfig()

    private val reached = sortedMapOf<String, Int>()

    private fun ScreenMachine.collect() = reached.forEach { (key, count) -> this@ScreenPropertyTest.reached.merge(key, count, Int::plus) }

    private fun report(name: String, started: Long, renders: Int) {
        val millis = (System.nanoTime() - started) / 1_000_000
        println("[pbt] $name: ${millis}ms, $renders renders (seed=$seed)")
        println("[pbt] reached: $reached")
    }

    @Test
    fun `どんな操作の列でも例外なく仕様の状態に留まり選択と入力を勝手に失わず各状態を2つの幅で描ける`() = runBlocking {
        val started = System.nanoTime()
        var renders = 0
        checkAll(config(defaultIterations = 60), catalogArb(), opsArb(1..30), languageArb) { catalog, ops, language ->
            ScreenMachine(catalog, render = true, language).use { machine ->
                machine.run(ops)
                machine.collect()
                renders += machine.renders
            }
        }
        report("with rendering", started, renders)
    }

    // covers: 検証の計画 ジャンプ・強調
    @Test
    fun `通知からのテンプレートを見るを混ぜた操作の列でも強調は1行だけで頼んだ行が一覧に出て各状態を描ける`() = runBlocking {
        val started = System.nanoTime()
        var renders = 0
        checkAll(config(defaultIterations = 60), catalogArb(), opsArb(1..30, stepWithRevealArb), languageArb) { catalog, ops, language ->
            ScreenMachine(catalog, render = true, language).use { machine ->
                machine.run(ops)
                machine.collect()
                renders += machine.renders
            }
        }
        report("with [View template]", started, renders)
    }

    @Test
    fun `描かずに長い操作の列を多く流しても仕様の状態に留まる`() = runBlocking {
        val started = System.nanoTime()
        checkAll(config(defaultIterations = 300), catalogArb(), opsArb(1..80), languageArb) { catalog, ops, language ->
            ScreenMachine(catalog, render = false, language).use { machine ->
                machine.run(ops)
                machine.collect()
            }
        }
        report("without rendering", started, 0)
    }

    @Test
    fun `katachi本体が書いたJSONの上で操作しても仕様の状態に留まり各状態を描ける`() = runBlocking {
        val started = System.nanoTime()
        var renders = 0
        checkAll(config(defaultIterations = 25), Arb.constant(syntheticCatalog), opsArb(1..30), languageArb) { catalog, ops, language ->
            ScreenMachine(catalog, render = true, language).use { machine ->
                machine.run(ops)
                machine.collect()
                renders += machine.renders
            }
        }
        report("synthetic JSON from katachi", started, renders)
    }

    @Test
    fun `数百件のテンプレートでも操作して描ける`() = runBlocking {
        val started = System.nanoTime()
        var renders = 0
        checkAll(config(defaultIterations = 4), catalogArb(sizes = 150..300), opsArb(1..12), languageArb) { catalog, ops, language ->
            ScreenMachine(catalog, render = true, language).use { machine ->
                machine.run(ops)
                machine.collect()
                renders += machine.renders
            }
        }
        report("hundreds of templates", started, renders)
    }
}
