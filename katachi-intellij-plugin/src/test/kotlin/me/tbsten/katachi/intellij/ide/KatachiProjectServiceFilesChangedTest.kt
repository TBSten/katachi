package me.tbsten.katachi.intellij.ide

import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.placementRootOf
import me.tbsten.katachi.intellij.data.watch.FileChanges
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import java.nio.file.Files

/**
 * The file watcher's `FileChanges` reach the tool window's capture candidates (design ide-review-screens 3.3): a change under
 * the project reads the directories behind the fields on screen again, a change that is not under it does not.
 */
internal class KatachiProjectServiceFilesChangedTest : EntryServiceTestBase() {
    private fun candidatesOf(feature: FieldId) = service.viewModel.state.value.captureCandidates[feature]?.values

    // covers: 論点11
    fun `test projectChanged の変化で、出ている capture の欄の候補を読み直す`() {
        gradle.fixtureOf["arch-a"] = "capture"
        installEntryService()
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        val viewModel = service.viewModel
        val arch = viewModel.state.value.modules.single()
        val row = viewModel.state.value.rows.first { it.template.template == "feature.ViewModel" }
        val feature = FieldId(row.id, "feature")
        val featureDir = placementRootOf(arch, NioProjectFileSystem).resolve("feature")
        Files.createDirectories(featureDir.resolve("home"))

        viewModel.dispatch(KatachiIntent.ToggleCheck(row.id))
        PlatformTestUtil.waitWithEventsDispatching({ "first read: ${viewModel.state.value.captureCandidates}" }, { candidatesOf(feature) == listOf("home") }, 10)

        Files.createDirectories(featureDir.resolve("news"))
        service.onFilesChanged(FileChanges(projectChanged = true))
        PlatformTestUtil.waitWithEventsDispatching({ "read again: ${viewModel.state.value.captureCandidates}" }, { candidatesOf(feature) == listOf("home", "news") }, 10)
    }

    // covers: 論点11
    fun `test projectChanged でない変化では候補を読み直さない`() {
        gradle.fixtureOf["arch-a"] = "capture"
        installEntryService()
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        val viewModel = service.viewModel
        val arch = viewModel.state.value.modules.single()
        val row = viewModel.state.value.rows.first { it.template.template == "feature.ViewModel" }
        val feature = FieldId(row.id, "feature")
        val featureDir = placementRootOf(arch, NioProjectFileSystem).resolve("feature")
        Files.createDirectories(featureDir.resolve("home"))
        viewModel.dispatch(KatachiIntent.ToggleCheck(row.id))
        PlatformTestUtil.waitWithEventsDispatching({ "first read: ${viewModel.state.value.captureCandidates}" }, { candidatesOf(feature) == listOf("home") }, 10)

        Files.createDirectories(featureDir.resolve("news"))
        service.onFilesChanged(FileChanges(baselineChanged = true))
        Thread.sleep(600)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(listOf("home"), candidatesOf(feature))
    }
}
