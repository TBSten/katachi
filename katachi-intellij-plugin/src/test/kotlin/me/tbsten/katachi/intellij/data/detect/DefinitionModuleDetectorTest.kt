package me.tbsten.katachi.intellij.data.detect

import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.model.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class DefinitionModuleDetectorTest {
    private val root = Paths.get("/work/project")

    private fun synced(vararg modules: SyncedModule, rootPath: java.nio.file.Path = root, name: String = "project") =
        SyncedRoot(rootPath, name, modules.toList())

    private fun mod(path: String, vararg tasks: String, version: String? = null, rootPath: java.nio.file.Path = root) =
        SyncedModule(path, rootPath.resolve(path.trim(':')), tasks.toSet(), version)

    private val internalTask = "katachiInternalTemplatesJson"
    private val templatesTask = "katachiTemplates"

    @Test
    fun `内部タスクを持つモジュールを名前に関係なく定義モジュールにする`() {
        val result = detectDefinitionModules(
            SyncedProject.Synced(listOf(synced(mod(":app", "build"), mod(":foo:bar", internalTask, templatesTask, version = "0.3.0")))),
        )
        val found = result as? DetectionResult.Found ?: throw AssertionError("not found: $result")
        assertEquals(listOf(":foo:bar"), found.modules.map { it.gradlePath })
        assertEquals(mapOf(ModuleId(root, ":foo:bar") to "0.3.0"), found.katachiVersions)
    }

    @Test
    fun `複数の定義モジュールはGradleパスの順に並べる`() {
        val result = detectDefinitionModules(
            SyncedProject.Synced(listOf(synced(mod(":arch-b", internalTask), mod(":app"), mod(":arch-a", internalTask)))),
        )
        assertEquals(listOf(":arch-a", ":arch-b"), (result as? DetectionResult.Found)?.modules?.map { it.gradlePath })
    }

    @Test
    fun `定義モジュールでないappは検出に出ない`() {
        val result = detectDefinitionModules(SyncedProject.Synced(listOf(synced(mod(":app", "assemble"), mod(":arch", internalTask)))))
        assertEquals(listOf(":arch"), (result as? DetectionResult.Found)?.modules?.map { it.gradlePath })
    }

    @Test
    fun `複数のGradleルートはルートごとにまとめ同じパスでも別のモジュールにする`() {
        val other = Paths.get("/work/another")
        val result = detectDefinitionModules(
            SyncedProject.Synced(
                listOf(
                    synced(mod(":arch", internalTask, rootPath = root), rootPath = root, name = "project"),
                    synced(mod(":arch", internalTask, rootPath = other), rootPath = other, name = "another"),
                ),
            ),
        )
        val modules = (result as? DetectionResult.Found)?.modules.orEmpty()
        assertEquals(listOf("another", "project"), modules.map { it.rootName })
        assertEquals(2, modules.map { it.id }.toSet().size)
    }

    @Test
    fun `内部タスクが無くkatachiTemplatesがあれば古い版にする`() {
        val result = detectDefinitionModules(SyncedProject.Synced(listOf(synced(mod(":arch", templatesTask, "katachiCheck", version = "0.2.0")))))
        assertEquals(DetectionResult.Outdated("0.2.0"), result)
    }

    @Test
    fun `どちらのタスクも無ければ未導入にする`() {
        val result = detectDefinitionModules(SyncedProject.Synced(listOf(synced(mod(":app", "build"), mod(":lib", "jar")))))
        assertEquals(DetectionResult.NotInstalled(taskListMissing = false), result)
    }

    @Test
    fun `同期データが無ければ同期前にする`() {
        assertEquals(DetectionResult.NotSynced, detectDefinitionModules(SyncedProject.NotSynced))
        assertEquals(DetectionResult.NotSynced, detectDefinitionModules(SyncedProject.Synced(emptyList())))
    }

    @Test
    fun `Gradleがリンクされていなければ Gradle でないにする`() {
        assertEquals(DetectionResult.NotGradle, detectDefinitionModules(SyncedProject.NotGradle))
    }

    @Test
    fun `タスク一覧が同期データに無ければkatachiに依存するモジュールを候補にする`() {
        val result = detectDefinitionModules(
            SyncedProject.Synced(listOf(synced(mod(":app"), mod(":arch", version = "0.3.0"), mod(":lib")))),
        )
        assertEquals(listOf(":arch"), (result as? DetectionResult.TaskListMissing)?.candidates?.map { it.gradlePath })
    }

    @Test
    fun `タスク一覧が無くkatachiにも依存していなければタスク一覧が無いことを添えて未導入にする`() {
        assertEquals(
            DetectionResult.NotInstalled(taskListMissing = true),
            detectDefinitionModules(SyncedProject.Synced(listOf(synced(mod(":app"))))),
        )
    }

    @Test
    fun `ほかのモジュールがタスク一覧を持っていてもタスクの無いkatachiに依存するモジュールは候補にする`() {
        // KATACHI-363: Android Studio's sync without a task list, where some other module still came with tasks.
        val result = detectDefinitionModules(
            SyncedProject.Synced(listOf(synced(mod(":", "help"), mod(":app"), mod(":static-analysis:katachi", version = "0.4.0")))),
        )
        assertEquals(listOf(":static-analysis:katachi"), (result as? DetectionResult.TaskListMissing)?.candidates?.map { it.gradlePath })
    }

    @Test
    fun `タスク一覧を持つkatachiに依存するだけのモジュールは候補にしない`() {
        // A module that uses katachi without the Gradle plugin: its task list says it has no katachi task.
        val result = detectDefinitionModules(SyncedProject.Synced(listOf(synced(mod(":lib", "build", version = "0.4.0")))))
        assertEquals(DetectionResult.NotInstalled(taskListMissing = false), result)
    }
}
