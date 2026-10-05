package me.tbsten.katachi.intellij.uitest.pbt.definition

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.model.loadFailed
import me.tbsten.katachi.intellij.model.loaded
import me.tbsten.katachi.intellij.model.markStale
import me.tbsten.katachi.intellij.model.startLoading
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [DefinitionState] over random walks of the transitions: the sequence NotLoaded, Loading, Ready, Stale,
 * Loading(previous), Ready... in every order the real service can produce. The invariants are what the two features
 * rely on to keep drawing: an answer once read is never lost between reads, and a repeated signal is not a new state.
 */
class DefinitionStatePropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261007L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val snapshots = listOf(
        definitionSnapshot(definitionModel(roleModel("a.One", layoutEntry("a/*.kt")))),
        definitionSnapshot(definitionModel(roleModel("a.Two", layoutEntry("b/*.kt")))),
        definitionSnapshot(definitionModel(), module(":other")),
    )
    private val failure = LoadFailure.Gradle(GradleFailure.Other(listOf("boom")))

    private fun walk(seed: Long, length: Int = 40): List<DefinitionState> = DefinitionStateScripts.random(Random(seed), length, snapshots)

    // covers: 状態の列
    @Test
    fun `一度でも答えが読めたら あとの状態はどれも答えを持ち続ける`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { walkSeed ->
            val states = walk(walkSeed)
            val firstAnswered = states.indexOfFirst { it.answer != null }
            if (firstAnswered >= 0) {
                for ((index, state) in states.withIndex().drop(firstAnswered)) {
                    assertNotNull("seed=$walkSeed state #$index $state", state.answer)
                }
            }
        }
    }

    // covers: 状態の列
    @Test
    fun `読み終えた直後は答えが Fresh の Ready で 前の答えと内容が同じなら definitions を使い回す`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { walkSeed ->
            val states = walk(walkSeed)
            for (state in states) {
                for (snapshot in snapshots) {
                    val after = state.loaded(listOf(snapshot))
                    assertEquals(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh), after)
                    val previous = state.answer
                    if (previous != null && previous.definitions == after.definitions) assertSame(previous.definitions, after.definitions)
                }
            }
        }
    }

    // covers: 状態の列
    @Test
    fun `同じ合図を続けて送っても状態は変わらない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { walkSeed ->
            val states = walk(walkSeed)
            val later = DEFINITION_INSTANT.plusSeconds(1000)
            for (state in states) {
                val loading = state.startLoading()
                assertSame("seed=$walkSeed $state", loading, loading.startLoading())
                val stale = state.markStale(later, StaleReason.DefinitionChanged)
                assertSame("seed=$walkSeed $state", stale, stale.markStale(later.plusSeconds(5), StaleReason.DefinitionChanged))
                val failed = state.loadFailed(later, failure)
                assertEquals("seed=$walkSeed $state", failed, failed.loadFailed(later.plusSeconds(5), failure))
            }
        }
    }

    // covers: 状態の列
    @Test
    fun `古いという印の since は最初の変更の時刻のままで 読み込み中も答えは前のものを指す`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { walkSeed ->
            val states = walk(walkSeed)
            for ((index, state) in states.withIndex()) {
                val answer = state.answer ?: continue
                val freshness = answer.freshness
                if (freshness is Freshness.Stale) {
                    val first = state.markStale(DEFINITION_INSTANT.plusSeconds(100_000), StaleReason.DefinitionChanged).answer?.freshness as? Freshness.Stale
                    assertEquals("seed=$walkSeed #$index", freshness.since, first?.since)
                }
                if (state is DefinitionState.Loading) assertSame("seed=$walkSeed #$index", state.previous, state.answer)
            }
        }
    }

    // covers: 状態の列
    @Test
    fun `同値を落とさない列でも Loading は直前の答えの定義を previous に持ち 内容の変わらない読み込みは定義の列を使い回す`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { walkSeed ->
            // Every transition is a state of the walk, the repeated ones too: the pairs below are the transitions themselves.
            val states = DefinitionStateScripts.random(Random(walkSeed), 40, snapshots, dropEqual = false)
            assertEquals(DefinitionState.NotLoaded, states.first())
            for ((before, after) in states.zipWithNext()) {
                // Loading carries the answer of the state before it (an edit during loading re-marks it stale, so only the definitions are compared).
                if (after is DefinitionState.Loading) assertEquals("seed=$walkSeed $before -> $after", before.answer?.definitions, after.previous?.definitions)
                if (before is DefinitionState.Ready && after is DefinitionState.Ready && before.definitions == after.definitions) {
                    assertSame("seed=$walkSeed", before.definitions, after.definitions)
                }
            }
        }
    }

    // covers: 状態の列
    @Test
    fun `NotLoaded から始めて読み込み前に失敗しても答えが無いことは変わらない`() {
        val failed = DefinitionState.NotLoaded.startLoading().loadFailed(DEFINITION_INSTANT, failure)
        assertNull(failed.answer)
    }
}
