package me.tbsten.katachi.intellij.ide.gutter

import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.LoadAnswer
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness

/**
 * The gutter asking for the definition (design 2.6, `NotLoaded`) through the services the platform creates: a file opened before
 * anyone loaded the definition makes the real provider ask the real service, which runs the fake Gradle, and the ◆ appears
 * when the state changes, drawn again by the real [HighlightingRefresher]. Nothing in these tests calls `ensureLoaded` itself.
 */
internal class GutterWiringLoadTest : GutterWiringTestBase() {
    private var savedAutoLoad = true

    override fun setUp() {
        super.setUp()
        savedAutoLoad = KatachiSettings.getInstance(project).autoLoadTemplates
    }

    override fun tearDown() {
        try {
            KatachiSettings.getInstance(project).autoLoadTemplates = savedAutoLoad
        } finally {
            super.tearDown()
        }
    }

    fun `test まだ読んでいない定義は ファイルを開くと ◆ が頼んで 読み終わると 描き直されて ◆ が出る`() {
        installReal()
        assertEquals(DefinitionState.NotLoaded, definition.state.value)
        // The fake Gradle answers in a few milliseconds, which is faster than the draw that asks for it ends: hold the first load,
        // so that "the first draw has no ◆" does not depend on who is faster.
        val gate = CompletableDeferred<Unit>()
        gradle.loads += LoadAnswer(gate = gate)
        open()

        assertEquals("nothing is read yet, so there is no ◆ to draw", emptyList<DrawnMark>(), redrawn())
        waitState("asked by the gutter's draw") { it is DefinitionState.Loading }
        assertEquals("the gutter asked once however often it drew", 1, gradle.loadRequests.size)
        gate.complete(Unit)

        val ready = waitState("loaded by the gutter's request") { it is DefinitionState.Ready && !projectService.viewModel.isLoadRunning } as DefinitionState.Ready
        assertEquals(Freshness.Fresh, ready.freshness)
        assertEquals("the gutter asked once however often it drew", 1, gradle.loadRequests.size)

        // No restart from the test: the refresher draws the open file again when the state became Ready.
        val drawn = redrawnByRefresher().single()
        assertEquals(3, drawn.line)
        assertTrue(drawn.tooltip.orEmpty(), drawn.tooltip.orEmpty().startsWith("katachi: Service"))
        assertEquals("a ◆ drawn again does not read the definition again", 1, gradle.loadRequests.size)
    }

    fun `test 自動で読まない設定のあいだは Gradle を走らせず 設定を戻して描き直すと読む`() {
        KatachiSettings.getInstance(project).autoLoadTemplates = false
        installReal()
        open()

        assertEquals(emptyList<DrawnMark>(), redrawn())
        idle(HighlightingRefresher.SETTLE_MILLIS + 500)
        assertEquals(emptyList<DrawnMark>(), redrawn())
        assertEquals("the user did not let katachi load by itself", 0, gradle.loadRequests.size)

        KatachiSettings.getInstance(project).autoLoadTemplates = true
        redrawn()
        waitState("loaded once the setting was back") { it is DefinitionState.Ready && !projectService.viewModel.isLoadRunning }
        assertEquals(1, gradle.loadRequests.size)
        assertEquals(3, redrawnByRefresher().single().line)
    }
}
