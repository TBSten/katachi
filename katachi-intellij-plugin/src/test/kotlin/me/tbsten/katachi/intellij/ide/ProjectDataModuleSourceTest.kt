package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.TaskData
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.model.DetectionResult
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradlePath
import java.nio.file.Path

internal class ProjectDataModuleSourceTest : AnalysisTestBase() {
    private val system = GradleConstants.SYSTEM_ID
    private val rootPath = "/work/project"
    private val projectNode = DataNode(ProjectKeys.PROJECT, ProjectData(system, "project", rootPath, rootPath), null)

    private fun module(gradlePath: String, vararg tasks: String): DataNode<ModuleData> {
        val directory = if (gradlePath == ":") rootPath else rootPath + "/" + gradlePath.trim(':').replace(':', '/')
        val data = ModuleData("project$gradlePath", system, "JAVA_MODULE", gradlePath.trim(':').ifEmpty { "project" }, directory, directory)
        data.gradlePath = gradlePath
        val node = projectNode.createChild(ProjectKeys.MODULE, data)
        // A task without a group, as katachi registers katachiInternalTemplatesJson.
        tasks.forEach { node.createChild(ProjectKeys.TASK, TaskData(system, it, directory, null)) }
        return node
    }

    private fun DataNode<ModuleData>.dependsOnKatachi(version: String, underSourceSet: Boolean) {
        val library = LibraryData(system, "Gradle: me.tbsten.katachi:katachi:$version").apply {
            setGroup("me.tbsten.katachi")
            setArtifactId("katachi")
            setVersion(version)
        }
        val owner = if (underSourceSet) {
            val sourceSet = GradleSourceSetData("${data.id}:test", "${data.externalName}:test", "${data.internalName}.test", data.moduleFileDirectoryPath, data.linkedExternalProjectPath)
            createChild(GradleSourceSetData.KEY, sourceSet)
        } else {
            this
        }
        owner.createChild(ProjectKeys.LIBRARY_DEPENDENCY, LibraryDependencyData(owner.data, library, LibraryLevel.PROJECT))
    }

    fun `test 同期データのモジュールから Gradle パス・ディレクトリ・タスク名・katachi の版を取り出す`() {
        module(":arch-a", "katachiInternalTemplatesJson", "katachiTemplate", "build").dependsOnKatachi("0.3.0", underSourceSet = true)
        module(":app", "build")

        val root = syncedRootOf(projectNode)

        assertEquals(Path.of(rootPath), root.rootPath)
        assertEquals("project", root.rootName)
        assertEquals(
            listOf(
                SyncedModule(":arch-a", Path.of("$rootPath/arch-a"), setOf("katachiInternalTemplatesJson", "katachiTemplate", "build"), "0.3.0"),
                SyncedModule(":app", Path.of("$rootPath/app"), setOf("build"), null),
            ),
            root.modules,
        )
    }

    fun `test katachiInternalTemplatesJson を持つモジュールだけを定義モジュールとして見つける`() {
        module(":arch-a", "katachiInternalTemplatesJson").dependsOnKatachi("0.3.0", underSourceSet = false)
        module(":app", "build")

        val result = detectDefinitionModules(syncedProjectOf(listOf(projectNode)))

        val found = result as? DetectionResult.Found ?: throw AssertionError("not found: $result")
        assertEquals(listOf(":arch-a"), found.modules.map { it.gradlePath })
        assertEquals(Path.of("/work/project/arch-a"), found.modules.single().directory)
    }

    fun `test ルートに載るサブプロジェクトから継いだタスクではルートを定義モジュールにしない`() {
        // What the IDE's sync gives the root of sample/jvm: its subprojects' tasks, marked inherited.
        module(":", "build").createChild(
            ProjectKeys.TASK,
            TaskData(system, "katachiInternalTemplatesJson", rootPath, null).apply { isInherited = true },
        )
        module(":architecture-test", "katachiInternalTemplatesJson")

        val result = detectDefinitionModules(syncedProjectOf(listOf(projectNode)))

        val found = result as? DetectionResult.Found ?: throw AssertionError("not found: $result")
        assertEquals(listOf(":architecture-test"), found.modules.map { it.gradlePath })
    }

    fun `test タスク一覧の無い同期では katachi に依存するモジュールを候補にする`() {
        module(":arch-a").dependsOnKatachi("0.3.0", underSourceSet = true)
        module(":app")

        val result = detectDefinitionModules(syncedProjectOf(listOf(projectNode)))

        val missing = result as? DetectionResult.TaskListMissing ?: throw AssertionError("not TaskListMissing: $result")
        assertEquals(listOf(":arch-a"), missing.candidates.map { it.gradlePath })
    }

    fun `test 同期データが1つも無ければ同期前とする`() {
        assertEquals(SyncedProject.NotSynced, syncedProjectOf(emptyList()))
    }

    fun `test Gradle をリンクしていないプロジェクトは Gradle でないとする`() {
        assertEquals(SyncedProject.NotGradle, ProjectDataModuleSource(project).read())
    }
}
