package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.sarif.FakeViolationsService
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * [HighlightingRefresher] through sequences of what happens around it: the definition and the violations change, a setting
 * draws every file, the followed services are replaced, time passes, the restart is slow. The oracle is the test's own list
 * of what it did, apart from the refresher: once the refresher is idle its last draw is for the service followed last, in
 * the last state it was given (or one that came together with it, since a collector may see only the last of those), a
 * change that is owed a draw gets one, no draw is for `Loading`, and a draw whose restart was cancelled never shows
 * (counted per reason). The draws are read from [HighlightingRefresher.redraws], not from a collector of `lastRedraw`, which would drop
 * records that follow each other. `-Dkatachi.gutter.refresherSequences=N` runs N sequences (30 by default).
 */
internal class HighlightingRefresherSequenceTest : AnalysisTestBase() {
    private lateinit var scope: CoroutineScope
    private lateinit var refresher: HighlightingRefresher

    /** Restarts that were still wanted when they ended: a draw of a restart that was cancelled must never show up as a record. */
    private val finished = CopyOnWriteArrayList<String>()

    @Volatile
    private var restartMillis = 0L

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        refresher = HighlightingRefresher(project, scope)
        refresher.restarter = { reason, _ ->
            delay(restartMillis)
            if (currentCoroutineContext().isActive) finished += reason
        }
    }

    override fun tearDown() {
        try {
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    /** What the test did to the services, kept apart from the refresher. */
    private class World(var definitions: FakeDefinitionModelService, val violations: FakeViolationsService) {
        var states = 0L
        var lastNonLoading: DefinitionState? = null
        var startState: DefinitionState = DefinitionState.NotLoaded
        var violationsChanged = false

        /** Every state given since the last pause: a collector may see only the last of states that came together. */
        val burst = mutableListOf<DefinitionState>()
    }

    private val snapshot = definitionSnapshot(definitionModel())

    private fun nextState(random: Random, world: World): DefinitionState = when (random.nextInt(5)) {
        0 -> DefinitionState.Loading(null)
        1 -> DefinitionState.NotLoaded
        2 -> DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null)
        3 -> DefinitionState.Ready(listOf(snapshot), Freshness.Fresh)
        else -> DefinitionState.Ready(listOf(snapshot), Freshness.Stale(DEFINITION_INSTANT.plusSeconds(++world.states), StaleReason.DefinitionChanged))
    }

    private fun step(random: Random, world: World): String = when (random.nextInt(7)) {
        0, 1 -> {
            val state = nextState(random, world)
            if (state == world.definitions.state.value) "emit ${state.javaClass.simpleName} (already so)"
            else "emit ${state.javaClass.simpleName}".also {
                world.definitions.emit(state)
                world.burst += state
                if (state !is DefinitionState.Loading) world.lastNonLoading = state
            }
        }
        2 -> "violations changed".also {
            world.violations.emit(ViolationsState.Ready(emptyList(), DEFINITION_INSTANT.plusSeconds(++world.states), stale = false, paths = emptyList()))
            world.violationsChanged = true
        }
        3 -> "redrawNow".also { refresher.redrawNow(SETTING) }
        4 -> "follow other services".also {
            world.definitions = FakeDefinitionModelService()
            refresher.follow(world.definitions, world.violations)
            world.lastNonLoading = null
            world.startState = world.definitions.state.value
            world.violationsChanged = false
            world.burst.clear()
        }
        5 -> random.nextLong(0, 400).let { millis -> "wait $millis ms".also { Thread.sleep(millis); if (millis >= PAUSE_MILLIS) world.burst.clear() } }
        else -> random.nextLong(0, 600).let { millis -> "restart takes $millis ms".also { restartMillis = millis } }
    }

    private fun runSequence(seed: Long) {
        val random = Random(seed)
        val world = World(FakeDefinitionModelService(), FakeViolationsService())
        refresher.follow(world.definitions, world.violations)
        val log = mutableListOf("follow")
        val startSeq = redrawSeq(project, refresher)
        repeat(random.nextInt(5, 26)) { log += step(random, world) }
        val why = "seed=$seed ${log.joinToString(" → ")}"

        // Idle means the settling time and the restart are over; the refresher says so itself. The one thing to wait out is the settling
        // time of a change that its collector has not turned into a job yet, so a quiet moment must last longer than one collection.
        var quietSince = System.currentTimeMillis()
        PlatformTestUtil.waitWithEventsDispatching(
            { "$why: the refresher stays busy (${refresher.lastRedraw.value})" },
            {
                if (refresher.isBusy) quietSince = System.currentTimeMillis()
                System.currentTimeMillis() - quietSince > 100
            },
            30,
        )

        // A setting's draw reads the state when it starts and may finish after a later definition draw: judge the definition draws.
        val observed = refresher.redraws.filter { it.seq > startSeq }
        val last = observed.lastOrNull { it.reason != SETTING }
        val reasons = observed.map { it.reason }
        assertTrue("$why: a draw for Loading was made: $reasons", reasons.none { it.endsWith("is Loading") })
        assertEquals("$why: seq only goes up", observed.map { it.seq }.sorted(), observed.map { it.seq })
        val recorded = reasons.groupingBy { it }.eachCount()
        val ended = finished.groupingBy { it }.eachCount()
        recorded.forEach { (reason, count) ->
            assertTrue("$why: \"$reason\" was recorded $count times but its restart ended wanted only ${ended[reason] ?: 0} times", count <= (ended[reason] ?: 0))
        }
        // A draw is owed when the last state that is not Loading differs from the one the follow began with, or the violations changed.
        val due = world.violationsChanged || (world.lastNonLoading != null && GutterFreshness.shouldRedraw(world.startState, world.lastNonLoading!!))
        if (due) {
            assertNotNull("$why: a change was owed a draw and none was made", last)
        }
        if (last != null && (due || last.definitions === world.definitions)) {
            assertSame("$why: the last draw is for the service followed last", world.definitions, last.definitions)
            val expected = world.definitions.state.value
            val accepted = (world.burst + expected + listOfNotNull(world.lastNonLoading)).distinct()
            assertTrue("$why: the last draw saw ${last.definition}, wanted one of $accepted", last.definition in accepted)
        }
        finished.clear()
    }

    private companion object {
        const val SETTING = "the setting changed"
        const val PAUSE_MILLIS = 50L
    }

    fun `test 変化と待ちと差し替えの列を流しても 最後の描き直しは最後に follow した相手の最後の状態で 終わらない描き直しは現れない`() {
        val sequences = System.getProperty("katachi.gutter.refresherSequences")?.toInt() ?: 30
        repeat(sequences) { runSequence(20261017L + it) }
    }
}
