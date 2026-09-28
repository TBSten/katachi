package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.PlacementUnavailableReason
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** How the tool window's state becomes the placement index the entries read (issues 11, 18, decisions 16, 17). */
class PlacementIndexSourceTest {
    private val arch = module(":arch-a")
    private val snapshot = DescriptionSnapshot(arch, "0.3.0", listOf(template("data.Repository")), Instant.parse("2026-09-29T00:00:00Z"))
    private val built = mutableListOf<List<DescriptionSnapshot>>()

    private fun stateOf(
        phase: ScreenPhase = ScreenPhase.Initializing,
        modulesFound: Boolean = false,
        snapshots: List<DescriptionSnapshot> = emptyList(),
        loading: Boolean = false,
        loadStarted: Boolean = false,
    ): PlacementIndexState = placementIndexStateOf(PlacementIndexSource(phase, modulesFound, snapshots, loading, loadStarted)) {
        built += it
        TemplatePlacementIndex.EMPTY
    }

    // covers: 論点11
    @Test
    fun `一覧があれば読み込み中でも読み込みに失敗した後でも Ready でその一覧から索引を作る`() {
        for (loading in listOf(false, true)) {
            built.clear()
            val state = stateOf(ScreenPhase.Ready, modulesFound = true, snapshots = listOf(snapshot), loading = loading, loadStarted = true)
            assertTrue("loading=$loading: $state", state is PlacementIndexState.Ready)
            assertEquals(listOf(listOf(snapshot)), built)
        }
    }

    // covers: 論点18
    @Test
    fun `まだ誰も読み込みを頼んでいないか検出の途中なら NotLoaded で読み込み中は出さない`() {
        assertEquals(PlacementIndexState.NotLoaded, stateOf())
        assertEquals(PlacementIndexState.NotLoaded, stateOf(loadStarted = true))
        assertTrue(built.isEmpty())
    }

    // covers: 論点18
    @Test
    fun `定義モジュールが見つかって読み込みが始まったか走っている間は Loading`() {
        assertEquals(PlacementIndexState.Loading, stateOf(modulesFound = true, loadStarted = true))
        assertEquals(PlacementIndexState.Loading, stateOf(modulesFound = true, loading = true, loadStarted = true))
    }

    // covers: 論点11
    @Test
    fun `利用者の操作なしの読み込みが OFF でキャッシュも無ければ Unavailable`() {
        assertEquals(
            PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadingWithoutUserDisabled),
            stateOf(modulesFound = true, loadStarted = false),
        )
    }

    // covers: 論点11
    @Test
    fun `katachi の定義モジュールが無い同期データでは Unavailable で読み込み中にならない`() {
        val noDefinition = PlacementIndexState.Unavailable(PlacementUnavailableReason.NoDefinition)
        assertEquals(noDefinition, stateOf(ScreenPhase.Empty(EmptyReason.NotInstalled), loadStarted = true))
        assertEquals(noDefinition, stateOf(ScreenPhase.Empty(EmptyReason.NotGradle), loadStarted = true))
        assertEquals(noDefinition, stateOf(ScreenPhase.Empty(EmptyReason.Outdated("0.1.0")), loadStarted = true))
        assertEquals(PlacementIndexState.Unavailable(PlacementUnavailableReason.NotSynced), stateOf(ScreenPhase.Empty(EmptyReason.NotSynced)))
    }

    @Test
    fun `一覧の無いまま読み込みに失敗したら Unavailable でテンプレートが0個なら空の索引で Ready`() {
        val failed = ScreenPhase.LoadError(LoadFailure.Gradle(GradleFailure.Other(listOf("boom"))), emptyList())
        assertEquals(PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadFailed), stateOf(failed, modulesFound = true, loadStarted = true))
        assertTrue(stateOf(ScreenPhase.Empty(EmptyReason.NoTemplates), modulesFound = true, loadStarted = true) is PlacementIndexState.Ready)
        assertEquals(listOf(emptyList<DescriptionSnapshot>()), built)
    }

    @Test
    fun `フォームの入力だけが変わった状態は同じ索引の元になる`() {
        val state = KatachiScreenState(phase = ScreenPhase.Ready, modules = listOf(arch), snapshots = listOf(snapshot))
        val typed = state.copy(searchQuery = "Repo", form = state.form.copy(inputs = mapOf(TemplateId(arch.id, "data.Repository") to mapOf("name" to "User"))))
        assertEquals(PlacementIndexSource.of(state, loadStarted = true), PlacementIndexSource.of(typed, loadStarted = true))
    }
}
