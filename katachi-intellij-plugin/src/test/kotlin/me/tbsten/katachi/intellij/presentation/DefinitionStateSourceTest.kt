package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.model.markStale
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/**
 * The definition state derived from the tool window's state (design 4.3), as a sequence of what the
 * ViewModel goes through: detection, the cache, a load, a save, a failed reload, a cancel.
 */
class DefinitionStateSourceTest {
    private val arch = module(":arch").copy(capabilities = setOf(KatachiCapability.Definition, KatachiCapability.Templates))
    private val old = module(":arch")
    private val v1 = snapshotOf(definitionModel(roleModel("A", layoutEntry("a.kt"))))
    private val v2 = snapshotOf(definitionModel(roleModel("A", layoutEntry("a.kt")), roleModel("B", layoutEntry("b.kt"))))

    private fun snapshotOf(definition: me.tbsten.katachi.intellij.model.DefinitionModel) =
        DescriptionSnapshot(arch, "0.3.0", emptyList(), DEFINITION_INSTANT, definition)

    private fun source(
        snapshots: List<DescriptionSnapshot> = emptyList(),
        loading: Boolean = false,
        failure: LoadFailure? = null,
        started: Boolean = true,
        phase: ScreenPhase = if (snapshots.isEmpty()) ScreenPhase.Initializing else ScreenPhase.Empty(EmptyReason.NoTemplates),
        loadsWithoutUser: Boolean = true,
        modules: List<me.tbsten.katachi.intellij.model.KatachiModule> = listOf(arch),
    ) = DefinitionStateSource(phase, modules, snapshots, loading, failure, started, loadsWithoutUser)

    @Test
    fun `検出から読み込み・保存・再読み込みまでの列で答えを消さない`() {
        var state: DefinitionState = DefinitionState.NotLoaded
        var last: DefinitionStateSource? = null
        val history = mutableListOf<DefinitionState>()
        fun see(source: DefinitionStateSource, at: Long) {
            state = nextDefinitionState(state, last, source, Instant.ofEpochSecond(at))
            last = source
            history += state
        }
        see(source(), 0)                                                // detecting
        see(source(loading = true), 1)                                  // Gradle runs
        see(source(listOf(v1)), 2)                                      // read
        state = state.markStale(Instant.ofEpochSecond(3), StaleReason.DefinitionChanged).also { history += it } // a save
        see(source(listOf(v1), loading = true), 4)                      // the automatic reload
        see(source(listOf(v2)), 5)                                      // read again

        assertEquals(DefinitionState.NotLoaded, history[0])
        assertEquals(DefinitionState.Loading(null), history[1])
        assertEquals(Freshness.Fresh, (history[2] as DefinitionState.Ready).freshness)
        assertEquals(Freshness.Stale(Instant.ofEpochSecond(3), StaleReason.DefinitionChanged), (history[3] as DefinitionState.Ready).freshness)
        assertEquals(history[3], (history[4] as DefinitionState.Loading).previous)
        val reloaded = history[5] as DefinitionState.Ready
        assertEquals(Freshness.Fresh, reloaded.freshness)
        assertEquals(listOf("A", "B"), reloaded.definitions.single().definition?.roles?.map { it.qualifiedName })
    }

    @Test
    fun `再読み込みの失敗は前の答えを ReloadFailed で古くする`() {
        val failure = LoadFailure.Gradle(GradleFailure.CompilationFailed(listOf("e: x")))
        val ready = nextDefinitionState(DefinitionState.NotLoaded, null, source(listOf(v1)), Instant.EPOCH)
        val failed = nextDefinitionState(ready, source(listOf(v1)), source(listOf(v1), failure = failure), Instant.ofEpochSecond(9))
        assertEquals(Freshness.Stale(Instant.ofEpochSecond(9), StaleReason.ReloadFailed(failure)), (failed as DefinitionState.Ready).freshness)
        assertEquals(ready.answer?.definitions, failed.definitions)
    }

    @Test
    fun `何も無いまま失敗すれば LoadFailed`() {
        val failure = LoadFailure.JsonMissing(arch.definitionJson)
        val state = nextDefinitionState(DefinitionState.NotLoaded, null, source(failure = failure, phase = ScreenPhase.LoadError(failure, emptyList())), Instant.EPOCH)
        assertEquals(DefinitionState.Unavailable(DefinitionUnavailableReason.LoadFailed, null), state)
    }

    @Test
    fun `読み込みを止めると前の答えに戻り古さの印も残る`() {
        // The ViewModel keeps the same list instance on screen while a load runs and when it is cancelled.
        val shown = listOf(v1)
        val ready = nextDefinitionState(DefinitionState.NotLoaded, null, source(shown), Instant.EPOCH)
            .markStale(Instant.ofEpochSecond(1), StaleReason.DefinitionChanged)
        val loading = nextDefinitionState(ready, source(shown), source(shown, loading = true), Instant.ofEpochSecond(2))
        val cancelled = nextDefinitionState(loading, source(shown, loading = true), source(shown), Instant.ofEpochSecond(3))
        assertSame(ready, cancelled)
    }

    @Test
    fun `katachi 0_2 の定義モジュールだけなら KatachiTooOld`() {
        val state = nextDefinitionState(DefinitionState.NotLoaded, null, source(modules = listOf(old)), Instant.EPOCH)
        assertEquals(DefinitionState.Unavailable(DefinitionUnavailableReason.KatachiTooOld, null), state)
    }

    @Test
    fun `同期データの空の理由を Unavailable に写す`() {
        fun of(reason: EmptyReason) = nextDefinitionState(DefinitionState.NotLoaded, null, source(phase = ScreenPhase.Empty(reason), modules = emptyList()), Instant.EPOCH)
        assertEquals(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null), of(EmptyReason.NotSynced))
        assertEquals(DefinitionState.Unavailable(DefinitionUnavailableReason.NoDefinition, null), of(EmptyReason.NotInstalled()))
        assertEquals(DefinitionState.Unavailable(DefinitionUnavailableReason.NoDefinition, null), of(EmptyReason.Outdated("0.1.0")))
    }

    @Test
    fun `利用者の操作なしの読み込みが切れていればキャッシュを古いものとして見せ無ければ Unavailable`() {
        val cached = nextDefinitionState(DefinitionState.NotLoaded, null, source(listOf(v1), started = false, loadsWithoutUser = false), Instant.ofEpochSecond(4))
        assertTrue((cached as DefinitionState.Ready).freshness is Freshness.Stale)
        val nothing = nextDefinitionState(DefinitionState.NotLoaded, null, source(started = false, loadsWithoutUser = false), Instant.EPOCH)
        assertEquals(DefinitionState.Unavailable(DefinitionUnavailableReason.LoadingWithoutUserDisabled, null), nothing)
    }

    @Test
    fun `同じ中身の読み込みは同じ一覧の instance を使い回す`() {
        val first = nextDefinitionState(DefinitionState.NotLoaded, null, source(listOf(v1)), Instant.EPOCH) as DefinitionState.Ready
        val again = nextDefinitionState(first, source(listOf(v1)), source(listOf(v1.copy())), Instant.EPOCH) as DefinitionState.Ready
        assertSame(first.definitions, again.definitions)
        assertSame(first, again)
    }

    /**
     * A seeded random walk over what the ViewModel can do (detect, load, read, fail, cancel, save): once an
     * answer exists it never goes away, a running load is always Loading, and a step that changes nothing
     * keeps the same instance (a state flow does not emit).
     */
    @Test
    fun `どんな操作の列でも一度出た答えは消えず読み込み中は Loading`() {
        val random = Random(20261005)
        repeat(300) {
            var state: DefinitionState = DefinitionState.NotLoaded
            var last: DefinitionStateSource? = null
            var everAnswered = false
            repeat(random.nextInt(1, 25)) { step ->
                val snapshots = listOf(emptyList(), listOf(v1), listOf(v2), listOf(v1.copy())).random(random)
                val failure = listOf(null, null, LoadFailure.Cancelled, LoadFailure.JsonMissing(arch.definitionJson)).random(random)
                val next = if (random.nextInt(6) == 0) {
                    state.markStale(Instant.ofEpochSecond(step.toLong()), StaleReason.DefinitionChanged)
                } else {
                    val source = source(snapshots, loading = random.nextBoolean(), failure = failure, started = random.nextInt(4) != 0)
                    val repeated = nextDefinitionState(state, last, source, Instant.ofEpochSecond(step.toLong()))
                    if (source.loading) assertTrue("$source -> $repeated", repeated is DefinitionState.Loading)
                    // The same source again changes nothing.
                    assertEquals(repeated, nextDefinitionState(repeated, source, source, Instant.ofEpochSecond(step.toLong())))
                    last = source
                    repeated
                }
                if (everAnswered) assertNotNull("answer lost: $state -> $next", next.answer)
                everAnswered = everAnswered || next.answer != null
                state = next
            }
        }
    }
}
