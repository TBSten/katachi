package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.data.gradle.GradleActivity
import me.tbsten.katachi.intellij.ide.KatachiIdeTestBase
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The world of the definition service's headless tests (design V4): a project whose synced data has the
 * definition module `:arch`, a fake Gradle that writes a generated `definition.json` (its project root
 * replaced by the test's), the real project service over it, and the real [KatachiDefinitionService].
 */
internal abstract class DefinitionServiceTestBase : KatachiIdeTestBase() {
    protected lateinit var projectService: KatachiProjectService
    protected lateinit var definition: KatachiDefinitionService
    protected val activity = GradleActivity()

    /** Every state [definition] published, in order, as a collector saw them. */
    protected val history = CopyOnWriteArrayList<DefinitionState>()
    private val testRoots = mutableListOf<VirtualFile>()
    private var savedAutoReload = true

    override fun setUp() {
        super.setUp()
        savedAutoReload = KatachiSettings.getInstance(project).autoReloadOnSave
        synced = syncedWithDefinition(":arch")
        gradle.definitionOf["arch"] = definitionJson("synthetic-definition-explained")
    }

    override fun tearDown() {
        try {
            KatachiSettings.getInstance(project).autoReloadOnSave = savedAutoReload
            testRoots.filter { it.isValid }.forEach { PsiTestUtil.removeContentEntry(module, it) }
        } finally {
            super.tearDown()
        }
    }

    /** The generated fixture [name] as `:arch` would write it under [projectRoot]. */
    protected fun definitionJson(name: String, projectRoot: Path = root): String =
        DefinitionFixtures.definitionText(name).replace("\"projectRoot\": \"/repo\"", "\"projectRoot\": \"$projectRoot\"")

    /** Installs the project service over the fakes and the definition service over it. */
    protected fun install(ignoredByVcs: (VirtualFile) -> Boolean = { false }, autoReloadDelayMillis: Long = 300) {
        projectService = installProjectService(gradleActivity = activity, autoReloadDelayMillis = autoReloadDelayMillis)
        definition = KatachiDefinitionService(projectService, ignoredByVcs)
        history += definition.state.value
        scope.launch { definition.state.collect { if (history.lastOrNull() != it) history += it } }
    }

    /** Pumps the EDT until the state satisfies [condition]. */
    protected fun waitState(what: String, condition: (DefinitionState) -> Boolean): DefinitionState {
        PlatformTestUtil.waitWithEventsDispatching({ "$what: ${definition.state.value}" }, { condition(definition.state.value) }, 10)
        return definition.state.value
    }

    /** Loads the definition and waits for the answer. */
    protected fun loaded(): DefinitionState.Ready {
        definition.ensureLoaded()
        return waitState("loaded") { it is DefinitionState.Ready && !projectService.viewModel.isLoadRunning } as DefinitionState.Ready
    }

    /** [relative] under the test root, written on disk and through the VFS so that the file watcher sees it. */
    protected fun writeFile(relative: String, text: String): VirtualFile {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        val dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path.parent) ?: throw AssertionError("no dir $path")
        return WriteAction.computeAndWait<VirtualFile, Throwable> {
            val file = dir.findChild(path.fileName.toString()) ?: dir.createChildData(this, path.fileName.toString())
            VfsUtil.saveText(file, text)
            file
        }
    }

    /** Makes [dir] a test source root of the test module, as Gradle's `<name>.test` module would have it. */
    protected fun addTestSourceRoot(dir: Path): VirtualFile {
        Files.createDirectories(dir)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir) ?: throw AssertionError("Not in the VFS: $dir")
        PsiTestUtil.addSourceRoot(module, file, true)
        testRoots += file
        return file
    }
}
