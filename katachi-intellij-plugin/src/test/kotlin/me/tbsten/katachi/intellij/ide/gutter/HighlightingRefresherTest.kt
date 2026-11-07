package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.sarif.FakeViolationsService
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

/** [HighlightingRefresher] (design 2.6, U-13) over the fakes' state sequences: what it restarts, and when not. */
internal class HighlightingRefresherTest : AnalysisTestBase() {
    private lateinit var scope: CoroutineScope
    private lateinit var refresher: HighlightingRefresher
    private val restarts = CopyOnWriteArrayList<String>()
    private val definitions = FakeDefinitionModelService()
    private val violations = FakeViolationsService()
    private val snapshot = definitionSnapshot(definitionModel())

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        refresher = HighlightingRefresher(project, scope)
        refresher.restarter = { reason, roots -> restarts += "$reason @ ${roots.joinToString()}" }
    }

    override fun tearDown() {
        try {
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    /** Waits past the settling time and returns the restarts made meanwhile. */
    private fun settled(): List<String> {
        // A draw that is owed ends on the refresher's own record (isBusy); the window is only for "nothing is drawn".
        val deadline = System.currentTimeMillis() + HighlightingRefresher.SETTLE_MILLIS * 3
        PlatformTestUtil.waitWithEventsDispatching("settle", { System.currentTimeMillis() > deadline && !refresher.isBusy }, 5)
        return restarts.toList().also { restarts.clear() }
    }

    private fun seq() = redrawSeq(project, refresher)

    /** For a change that is owed a draw: waits for the refresher's own record of it (no clock), then the window shows no second one. */
    private fun settledAfter(since: Long): List<String> {
        awaitRedraw(since, definitions, refresher)
        return settled()
    }

    fun `test 状態の列で 答えが替わるたびに 1 回 Loading へ移っただけでは描き直さない`() {
        refresher.follow(definitions, violations)
        var since = seq()
        definitions.play(DefinitionStateScripts.standardCycle(snapshot).drop(1)) { state ->
            val made = if (state is DefinitionState.Loading) settled() else settledAfter(since)
            since = seq()
            if (state is DefinitionState.Loading) {
                assertEquals("$state", emptyList<String>(), made)
            } else {
                assertEquals("$state: $made", 1, made.size)
                assertTrue(made.single(), made.single().contains("/w/repo"))
            }
        }
    }

    fun `test 続けて替わった状態は まとめて 1 回だけ描き直す`() {
        refresher.follow(definitions, violations)
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null))
        definitions.emit(DefinitionState.NotLoaded)
        // A StateFlow may drop the states between; whatever it delivered settles into one restart.
        assertTrue(settled().size <= 1)
    }

    fun `test 違反の状態が替わっても描き直す`() {
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        refresher.follow(definitions, violations)
        assertEquals(emptyList<String>(), settled())
        val since = seq()
        violations.emit(ViolationsState.Ready(emptyList(), DEFINITION_INSTANT, stale = false, paths = emptyList()))
        assertEquals(listOf("the violations changed @ /w/repo"), settledAfter(since))
    }

    fun `test 同じサービスを 2 回 follow しても描き直しは 1 回`() {
        refresher.follow(definitions, violations)
        refresher.follow(definitions, violations)
        val since = seq()
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        assertEquals(1, settledAfter(since).size)
    }

    fun `test 答えが無くなる遷移は 直前に描いたルートを描き直し 答えが戻れば また描く`() {
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        refresher.follow(definitions, violations)
        assertEquals(emptyList<String>(), settled())

        var since = seq()
        definitions.emit(DefinitionState.NotLoaded)
        assertEquals("the ◆ and menus of the open files must go at once", listOf("the definition is NotLoaded @ /w/repo"), settledAfter(since))

        since = seq()
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        assertEquals(listOf("the definition is Ready @ /w/repo"), settledAfter(since))

        since = seq()
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null))
        assertEquals(listOf("the definition is Unavailable @ /w/repo"), settledAfter(since))
    }

    fun `test restart を頼み終えるまで isBusy が真で 終えたら lastRedraw が増える`() {
        val release = CompletableDeferred<Unit>()
        val reached = CompletableDeferred<Unit>()
        refresher.restarter = { reason, _ -> reached.complete(Unit); release.await(); restarts += reason }
        refresher.follow(definitions, violations)
        val since = seq()
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        PlatformTestUtil.waitWithEventsDispatching("the restart is reached", { reached.isCompleted }, 5)
        assertTrue("the draw is not over while the restart is held", refresher.isBusy)
        assertEquals(since, seq())
        release.complete(Unit)
        awaitRedraw(since, definitions, refresher)
        assertFalse(refresher.isBusy)
        assertEquals(since + 1, seq())
        assertTrue(refresher.lastRedraw.value?.definition is DefinitionState.Ready)
    }

    fun `test restarter が遅くても 次の変化が前の描き直しを取り消し lastRedraw は新しい理由の 1 回だけ`() {
        val reached = CompletableDeferred<Unit>()
        refresher.restarter = { reason, roots -> reached.complete(Unit); delay(1_000); restarts += "$reason @ ${roots.joinToString()}" }
        refresher.follow(definitions, violations)
        val since = seq()
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        PlatformTestUtil.waitWithEventsDispatching("the first draw is in its restart", { reached.isCompleted }, 5)
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null))
        awaitRedraw(since, definitions, refresher) { it is DefinitionState.Unavailable }
        assertEquals(since + 1, seq())
        assertEquals("the definition is Unavailable", refresher.lastRedraw.value?.reason)
        // The cancelled draw was never recorded, so its roots are not "the last draw's": with none named, every open file is drawn.
        assertEquals(listOf("the definition is Unavailable @ "), restarts.toList())
    }

    fun `test 同じサービスで違反の状態だけが替わる follow は 待っている描き直しを取り消さない`() {
        refresher.follow(definitions, violations)
        val since = seq()
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        PlatformTestUtil.waitWithEventsDispatching("the draw waits", { refresher.isBusy }, 5)
        refresher.follow(definitions, FakeViolationsService())
        awaitRedraw(since, definitions, refresher) { it is DefinitionState.Ready }
    }

    fun `test follow の相手が替わると 前の相手のために待っていた描き直しは lastRedraw に残らない`() {
        refresher.follow(definitions, violations)
        val since = seq()
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        // The draw is waiting for its settling time: only then does switching the followed service have something to cancel.
        PlatformTestUtil.waitWithEventsDispatching("the draw waits", { refresher.isBusy }, 5)
        refresher.follow(FakeDefinitionModelService(), violations)
        assertEquals("nothing is drawn for the service that is no longer followed", emptyList<String>(), settled())
        assertEquals(since, seq())
        assertFalse(refresher.isBusy)
    }

    fun `test 描いたルートがどこにも無いときは 開いているファイル全部を対象にする`() {
        refresher.follow(definitions, violations)
        val since = seq()
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null))
        assertEquals("no root to name: every open file may hold a ◆", listOf("the definition is Unavailable @ "), settledAfter(since))
    }

    fun `test 描き直しが restart の途中で取り消されても 前の描き直しのルートは次の描き直しに残る`() {
        val other = definitionSnapshot(definitionModel(projectRoot = java.nio.file.Paths.get("/w/other")))
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        refresher.follow(definitions, violations)
        val held = CompletableDeferred<Unit>()
        val reached = CompletableDeferred<Unit>()
        refresher.restarter = { reason, roots ->
            if (reason.endsWith("Unavailable")) { reached.complete(Unit); held.await() }
            restarts += "$reason @ ${roots.joinToString()}"
        }
        // The answer goes away while the restart of its roots is held; a newer change cancels that draw before it ends.
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null))
        PlatformTestUtil.waitWithEventsDispatching("the first draw is in its restart", { reached.isCompleted }, 5)
        val since = seq()
        definitions.emit(DefinitionState.Ready(listOf(other), Freshness.Fresh))
        awaitRedraw(since, definitions, refresher) { it is DefinitionState.Ready }
        val made = restarts.single()
        assertTrue("the root drawn before the cancelled draw is covered by the next: $made", made.contains("/w/repo") && made.contains("/w/other"))
    }

    fun `test 設定の描き直しと定義の描き直しが同時に終わっても 記録は番号順に並び lastRedraw は最後の番号`() {
        var together = CyclicBarrier(2)
        // Blocks the thread, not the coroutine: both draws leave their restart at the same moment and race to record.
        refresher.restarter = { _, _ -> runCatching { together.await(5, TimeUnit.SECONDS) } }
        refresher.follow(definitions, violations)
        repeat(RACE_ROUNDS) { round ->
            together = CyclicBarrier(2)
            val since = seq()
            definitions.emit(if (round % 2 == 0) DefinitionState.Ready(listOf(snapshot), Freshness.Fresh) else DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null))
            refresher.redrawNow("the setting changed")
            PlatformTestUtil.waitWithEventsDispatching(
                { "round $round: two draws after #$since: ${refresher.redraws.filter { it.seq > since }} isBusy=${refresher.isBusy}" },
                { refresher.redraws.count { it.seq > since } == 2 && !refresher.isBusy },
                10,
            )
            val seqs = refresher.redraws.map { it.seq }
            assertEquals("round $round: the records are in the order of their numbers", seqs.sorted(), seqs)
            assertEquals("round $round: lastRedraw is the newest record", seqs.last(), refresher.lastRedraw.value?.seq)
        }
    }

    private companion object {
        // One round in a few dozen inverts the records without the lock, so a run without it fails most of the time.
        const val RACE_ROUNDS = 60
    }
}
