package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        val deadline = System.currentTimeMillis() + HighlightingRefresher.SETTLE_MILLIS * 3
        PlatformTestUtil.waitWithEventsDispatching("settle", { System.currentTimeMillis() > deadline }, 5)
        return restarts.toList().also { restarts.clear() }
    }

    fun `test 状態の列で 答えが替わるたびに 1 回 Loading へ移っただけでは描き直さない`() {
        refresher.follow(definitions, violations)
        definitions.play(DefinitionStateScripts.standardCycle(snapshot).drop(1)) { state ->
            val made = settled()
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
        violations.emit(ViolationsState.Ready(emptyList(), DEFINITION_INSTANT, stale = false, paths = emptyList()))
        assertEquals(listOf("the violations changed @ /w/repo"), settled())
    }

    fun `test 同じサービスを 2 回 follow しても描き直しは 1 回`() {
        refresher.follow(definitions, violations)
        refresher.follow(definitions, violations)
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        assertEquals(1, settled().size)
    }

    fun `test 答えが無くなる遷移は 直前に描いたルートを描き直し 答えが戻れば また描く`() {
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        refresher.follow(definitions, violations)
        assertEquals(emptyList<String>(), settled())

        definitions.emit(DefinitionState.NotLoaded)
        assertEquals("the ◆ and menus of the open files must go at once", listOf("the definition is NotLoaded @ /w/repo"), settled())

        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        assertEquals(listOf("the definition is Ready @ /w/repo"), settled())

        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null))
        assertEquals(listOf("the definition is Unavailable @ /w/repo"), settled())
    }

    fun `test 描いたルートがどこにも無いときは 開いているファイル全部を対象にする`() {
        refresher.follow(definitions, violations)
        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null))
        assertEquals("no root to name: every open file may hold a ◆", listOf("the definition is Unavailable @ "), settled())
    }
}
