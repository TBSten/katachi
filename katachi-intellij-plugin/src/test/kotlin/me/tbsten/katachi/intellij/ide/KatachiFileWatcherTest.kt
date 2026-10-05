package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import me.tbsten.katachi.intellij.data.watch.WatchedModule
import me.tbsten.katachi.intellij.data.watch.classifyChanges
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import java.nio.file.Files
import java.nio.file.Path

/** The file watcher in the IDE: VFS events reach the project's service; the classification itself is in `FileChangesTest`. */
internal class KatachiFileWatcherTest : KatachiIdeTestBase() {
    private val module get() = KatachiModule(":arch-a", moduleDirOf(":arch-a"), root, "project")

    fun `test 定義モジュールの src と build スクリプトの変更は定義の変更になる`() {
        val dir = module.directory
        val changes = classifyChanges(listOf(dir.resolve("src/test/kotlin/Architecture.kt"), dir.resolve("build.gradle.kts")), listOf(WatchedModule(module, emptyList())), listOf(root)) { false }
        assertEquals(setOf(module.id), changes.definitionChanged)
        assertEquals(setOf(module.id), changes.testSourcesChanged)
    }

    fun `test build の下と定義モジュールでないモジュールの変更は定義の変更にしない`() {
        val dir = module.directory
        val paths = listOf(
            dir.resolve("build/katachi/internalTemplatesJson/templateDescription.json"),
            root.resolve("app/src/main/kotlin/Main.kt"),
            root.resolve("app/build.gradle.kts"),
        )
        assertEquals(emptySet<Any>(), classifyChanges(paths, listOf(WatchedModule(module, emptyList())), listOf(root)) { false }.definitionChanged)
    }

    fun `test 定義のファイルを書き換えると一覧に定義が変わった帯が出る`() {
        val source = moduleDirOf(":arch-a").resolve("src/test/kotlin/Architecture.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "// v1\n")
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source) ?: throw AssertionError("not in VFS: $source")
        val viewModel = installService()
        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        viewModel.waitFor("loaded") { it.phase == ScreenPhase.Ready && it.loading == null && !it.view.definitionChanged }

        WriteAction.runAndWait<Throwable> { VfsUtil.saveText(file, "// v2\n") }

        viewModel.waitFor("banner") { it.view.definitionChanged }
    }

    fun `test 生成物の出力先の変更では帯を出さない`() {
        val viewModel = installService()
        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        viewModel.waitFor("loaded") { it.phase == ScreenPhase.Ready && it.loading == null }
        val output: Path = root.resolve("data/src/main/kotlin/Generated.kt")
        Files.createDirectories(output.parent)
        Files.writeString(output, "// generated\n")

        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(output)
        com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertFalse(viewModel.state.value.view.definitionChanged)
    }
}
