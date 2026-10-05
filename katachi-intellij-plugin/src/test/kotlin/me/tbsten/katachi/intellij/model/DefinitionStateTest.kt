package me.tbsten.katachi.intellij.model

import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.ViolationsStateScripts
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/** The state of loading the definition, one transition at a time. The sequences are in the `uiTest` property tests. */
class DefinitionStateTest {
    private val snapshot = definitionSnapshot()
    private val other = definitionSnapshot(definitionModel(), module(":other"))
    private val failure = LoadFailure.Gradle(GradleFailure.Other(listOf("boom")))
    private val later = DEFINITION_INSTANT.plusSeconds(5)

    private val ready = DefinitionState.NotLoaded.startLoading().loaded(listOf(snapshot))

    @Test
    fun `何も読んでいない状態は答えを持たない`() {
        assertNull(DefinitionState.NotLoaded.answer)
        assertNull(DefinitionState.Loading(previous = null).answer)
        assertNull(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, previous = null).answer)
    }

    @Test
    fun `Ready と Loading と Unavailable は持っている Ready で答える`() {
        assertSame(ready, ready.answer)
        assertSame(ready, DefinitionState.Loading(ready).answer)
        assertSame(ready, DefinitionState.Unavailable(DefinitionUnavailableReason.NoDefinition, ready).answer)
    }

    @Test
    fun `読み始めても前の答えは消えず 読み込み中にもう一度始めても同じインスタンス`() {
        val loading = ready.startLoading()
        assertEquals(DefinitionState.Loading(ready), loading)
        assertSame(loading, loading.startLoading())
        assertEquals(DefinitionState.Loading(previous = null), DefinitionState.NotLoaded.startLoading())
    }

    @Test
    fun `読み終えると Fresh の Ready になる`() {
        assertEquals(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh), ready)
        assertEquals(DefinitionState.Ready(listOf(other), Freshness.Fresh), ready.startLoading().loaded(listOf(other)))
    }

    @Test
    fun `同じ内容を読み直すと definitions の list は前のものを使い回す`() {
        val rebuilt = listOf(definitionSnapshot())
        val again = ready.markStale(later, StaleReason.DefinitionChanged).startLoading().loaded(rebuilt)
        assertSame(ready.definitions, again.definitions)
        assertEquals(Freshness.Fresh, again.freshness)
    }

    @Test
    fun `すでに同じ Fresh の Ready を読み終えたなら同じインスタンスを返す`() {
        assertSame(ready, ready.loaded(listOf(snapshot)))
    }

    @Test
    fun `定義が変わると答えは残して Stale にし 最初の since を保つ`() {
        val stale = ready.markStale(later, StaleReason.DefinitionChanged)
        assertEquals(Freshness.Stale(later, StaleReason.DefinitionChanged), (stale as DefinitionState.Ready).freshness)
        assertEquals(ready.definitions, stale.definitions)
        // Another change later keeps the first since and gives back the same instance.
        assertSame(stale, stale.markStale(later.plusSeconds(60), StaleReason.DefinitionChanged))
    }

    @Test
    fun `Loading の間の変更は前の答えを Stale にする`() {
        val loading = ready.startLoading().markStale(later, StaleReason.DefinitionChanged)
        val previous = (loading as DefinitionState.Loading).previous
        assertEquals(Freshness.Stale(later, StaleReason.DefinitionChanged), previous?.freshness)
        assertSame(loading, loading.markStale(later.plusSeconds(1), StaleReason.DefinitionChanged))
    }

    @Test
    fun `答えの無い状態は Stale にできず同じインスタンス`() {
        val none = DefinitionState.NotLoaded
        assertSame(none, none.markStale(later, StaleReason.DefinitionChanged))
        val loading = DefinitionState.Loading(previous = null)
        assertSame(loading, loading.markStale(later, StaleReason.DefinitionChanged))
        val unavailable = DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, previous = null)
        assertSame(unavailable, unavailable.markStale(later, StaleReason.DefinitionChanged))
    }

    @Test
    fun `読み込みの失敗は答えがあれば Stale の ReloadFailed で残し 無ければ Unavailable`() {
        val failed = ready.startLoading().loadFailed(later, failure)
        assertEquals(Freshness.Stale(later, StaleReason.ReloadFailed(failure)), (failed as DefinitionState.Ready).freshness)
        assertEquals(ready.definitions, failed.definitions)
        assertEquals(
            DefinitionState.Unavailable(DefinitionUnavailableReason.LoadFailed, previous = null),
            DefinitionState.NotLoaded.startLoading().loadFailed(later, failure),
        )
    }

    @Test
    fun `失敗のあとに定義が変わっても理由は更新され since は最初のまま`() {
        val failed = ready.loadFailed(later, failure)
        val changed = failed.markStale(later.plusSeconds(9), StaleReason.DefinitionChanged) as DefinitionState.Ready
        assertEquals(Freshness.Stale(later, StaleReason.DefinitionChanged), changed.freshness)
    }

    @Test
    fun `Unavailable にしても前の答えは持ち続ける`() {
        val unavailable = ready.unavailable(DefinitionUnavailableReason.NoDefinition)
        assertEquals(DefinitionState.Unavailable(DefinitionUnavailableReason.NoDefinition, ready), unavailable)
        assertEquals(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, previous = null), DefinitionState.NotLoaded.unavailable(DefinitionUnavailableReason.NotSynced))
    }

    @Test
    fun `標準の列は NotLoaded から Loading Ready Stale の順にたどり 失敗のあとも答えを保つ`() {
        val cycle = DefinitionStateScripts.standardCycle(snapshot)
        assertEquals(DefinitionState.NotLoaded, cycle.first())
        assertTrue(cycle[1] is DefinitionState.Loading && (cycle[1] as DefinitionState.Loading).previous == null)
        assertTrue(cycle[2] is DefinitionState.Ready)
        assertTrue((cycle[3] as DefinitionState.Ready).freshness is Freshness.Stale)
        assertNotNull((cycle[4] as DefinitionState.Loading).previous)
        assertEquals(Freshness.Fresh, (cycle[5] as DefinitionState.Ready).freshness)
        assertTrue(cycle.drop(2).all { it.answer != null })
        assertEquals(cycle.size, cycle.zipWithNext().count { (a, b) -> a != b } + 1)
    }

    // --- the violations' side ---

    @Test
    fun `違反の状態も NotRun 以外は答えを持ち Unreadable は前の答えで答える`() {
        val path = Paths.get("/w/katachi.sarif")
        val violations = listOf(ViolationsStateScripts.violation(module(":a").id, Paths.get("/w/A.kt")))
        val cycle = ViolationsStateScripts.standardCycle(violations, path)
        assertNull(cycle.first().answer)
        assertEquals(violations, cycle[1].answer?.runs)
        assertTrue(cycle[2].answer?.stale == true)
        // Half-written: the earlier answer is still there.
        assertEquals(cycle[2].answer, cycle[3].answer)
        assertNull(cycle.last().answer)
    }
}
