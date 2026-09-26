package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedProjectSource
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull
import org.jetbrains.plugins.gradle.util.gradlePathOrNull
import java.nio.file.Path

/**
 * Reads the Gradle builds linked to [project] and their synced data (`ProjectDataManager`), without
 * running Gradle (spec 01).
 */
internal class ProjectDataModuleSource(private val project: Project) : SyncedProjectSource {
    override fun read(): SyncedProject {
        val linked = GradleSettings.getInstance(project).linkedProjectsSettings.map { it.externalProjectPath }
        if (linked.isEmpty()) return SyncedProject.NotGradle
        val data = ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
        val structures = linked.mapNotNull { path ->
            data.firstOrNull { it.externalProjectPath == path }?.externalProjectStructure
        }
        return syncedProjectOf(structures)
    }
}

/** [structures] (one per linked root that has synced) as the detector reads them. */
internal fun syncedProjectOf(structures: List<DataNode<ProjectData>>): SyncedProject =
    if (structures.isEmpty()) SyncedProject.NotSynced else SyncedProject.Synced(structures.map(::syncedRootOf))

internal fun syncedRootOf(projectNode: DataNode<ProjectData>): SyncedRoot {
    val project = projectNode.data
    val modules = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE).map(::syncedModuleOf)
    return SyncedRoot(Path.of(project.linkedExternalProjectPath), project.externalName, modules)
}

private fun syncedModuleOf(moduleNode: DataNode<ModuleData>): SyncedModule {
    val module = moduleNode.data
    // The root module also lists its subprojects' tasks as inherited ones (running
    // `katachiInternalTemplatesJson` from the root runs it in every subproject); those are not its own.
    val tasks = ExternalSystemApiUtil.findAll(moduleNode, ProjectKeys.TASK)
        .filterNot { it.data.isInherited }
        .map { it.data.name.substringAfterLast(':') }
    // Source sets (main, test) sit below the module node and carry the dependencies: katachi is
    // usually a test dependency of the definition module.
    val katachi = ExternalSystemApiUtil.findAllRecursively(moduleNode, ProjectKeys.LIBRARY_DEPENDENCY)
        .map { it.data.target }
        .firstOrNull { it.groupId == KATACHI_GROUP && it.artifactId == KATACHI_ARTIFACT }
    return SyncedModule(
        gradlePath = gradlePathOf(module),
        directory = Path.of(module.linkedExternalProjectPath),
        taskNames = tasks.toSet(),
        katachiVersion = katachi?.version,
    )
}

/**
 * The path `runTask` accepts from the linked root: the identity path, which in a composite build
 * prefixes the included build (`:included:arch`). Falls back to the module id before Gradle 8.
 */
private fun gradlePathOf(module: ModuleData): String =
    module.gradleIdentityPathOrNull ?: module.gradlePathOrNull ?: module.id

private const val KATACHI_GROUP = "me.tbsten.katachi"
private const val KATACHI_ARTIFACT = "katachi"
