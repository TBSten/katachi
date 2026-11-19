package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Computable
import me.tbsten.katachi.intellij.data.detect.SyncedLibrary
import me.tbsten.katachi.intellij.data.detect.SyncedModule
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.data.detect.SyncedProjectSource
import me.tbsten.katachi.intellij.data.detect.SyncedRoot
import me.tbsten.katachi.intellij.data.detect.katachiVersionOf
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull
import org.jetbrains.plugins.gradle.util.gradlePathOrNull
import java.nio.file.Path

internal class ProjectDataModuleSource(private val project: Project) : SyncedProjectSource {
    override fun read(): SyncedProject = sdkCall("read the Gradle sync data") {
        val linked = GradleSettings.getInstance(project).linkedProjectsSettings.map { it.externalProjectPath }
        if (linked.isEmpty()) return SyncedProject.NotGradle
        val data = ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
        val structures = linked.mapNotNull { path ->
            data.firstOrNull { it.externalProjectPath == path }?.externalProjectStructure
        }
        val fromIdeModules by lazy { katachiVersionsOfIdeModules() }
        syncedProjectOf(structures) { directory -> fromIdeModules[directory] }.also(::logWithoutTaskList)
    }.getOrElse { throw KatachiSyncedDataUnreadableException(it) }

    // Android Studio's sync sets up the dependencies of JVM modules on the IDE's modules and leaves them out of the sync data,
    // so a sync without task lists would find no module that depends on katachi (KATACHI-398).
    private fun katachiVersionsOfIdeModules(): Map<Path, String> =
        katachiLibrariesOfIdeModules().mapNotNull { (directory, names) ->
            val version = katachiVersionOf(SyncedLibrary(null, null, null, names)) ?: return@mapNotNull null
            Path.of(directory ?: return@mapNotNull null) to version
        }.toMap()

    /** The IDE's modules that have a library named after katachi: their Gradle project's directory, and those names. */
    private fun katachiLibrariesOfIdeModules(): List<Pair<String?, List<String>>> =
        ApplicationManager.getApplication().runReadAction(
            Computable {
                ModuleManager.getInstance(project).modules.map { module ->
                    val names = ModuleRootManager.getInstance(module).orderEntries
                        .filterIsInstance<LibraryOrderEntry>()
                        .flatMap { listOfNotNull(it.libraryName, it.presentableName) }
                        .filter { "katachi" in it }
                        .distinct()
                    ExternalSystemApiUtil.getExternalProjectPath(module) to names
                }.filter { (_, names) -> names.isNotEmpty() }
            },
        )

    // Without task lists the module is found only through its katachi dependency; the log shows which way it went.
    private fun logWithoutTaskList(synced: SyncedProject) {
        val modules = (synced as? SyncedProject.Synced)?.roots?.flatMap { it.modules }.orEmpty()
        if (modules.none { it.taskNames.isEmpty() }) return
        LOG.info(
            "Gradle sync data without task lists: " +
                modules.joinToString { "${it.gradlePath} (tasks=${it.taskNames.size}, katachi=${it.katachiVersion})" } +
                "; the IDE's modules with a katachi library: " +
                katachiLibrariesOfIdeModules().joinToString { (directory, names) -> "$directory $names" },
        )
    }

    private companion object {
        val LOG = logger<ProjectDataModuleSource>()
    }
}

internal class KatachiSyncedDataUnreadableException(
    override val cause: Throwable,
) : IllegalStateException(
    """
        The IDE could not hand over the Gradle sync data: $cause.
        Sync the Gradle project again (Gradle tool window, "Sync All Gradle Projects"), then reload the templates.
    """.trimIndent(),
    cause,
)

internal fun syncedProjectOf(
    structures: List<DataNode<ProjectData>>,
    katachiVersionOfIdeModule: (directory: Path) -> String? = { null },
): SyncedProject =
    if (structures.isEmpty()) SyncedProject.NotSynced else SyncedProject.Synced(structures.map { syncedRootOf(it, katachiVersionOfIdeModule) })

internal fun syncedRootOf(
    projectNode: DataNode<ProjectData>,
    katachiVersionOfIdeModule: (directory: Path) -> String? = { null },
): SyncedRoot {
    val project = projectNode.data
    val modules = ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE).map { syncedModuleOf(it, katachiVersionOfIdeModule) }
    return SyncedRoot(Path.of(project.linkedExternalProjectPath), project.externalName, modules)
}

private fun syncedModuleOf(moduleNode: DataNode<ModuleData>, katachiVersionOfIdeModule: (directory: Path) -> String?): SyncedModule {
    val module = moduleNode.data
    // The root module also lists its subprojects' tasks as inherited ones (running
    // `katachiInternalTemplatesJson` from the root runs it in every subproject); those are not its own.
    val tasks = ExternalSystemApiUtil.findAll(moduleNode, ProjectKeys.TASK)
        .filterNot { it.data.isInherited }
        .map { it.data.name.substringAfterLast(':') }
    // Source sets (main, test) sit below the module node and carry the dependencies: katachi is
    // usually a test dependency of the definition module.
    val directory = Path.of(module.linkedExternalProjectPath)
    val katachiVersion = ExternalSystemApiUtil.findAllRecursively(moduleNode, ProjectKeys.LIBRARY_DEPENDENCY)
        .map { it.data.target }
        .firstNotNullOfOrNull { library ->
            katachiVersionOf(SyncedLibrary(library.groupId, library.artifactId, library.version, listOf(library.externalName, library.internalName)))
        }
        ?: katachiVersionOfIdeModule(directory)
    return SyncedModule(
        gradlePath = gradlePathOf(module),
        directory = directory,
        taskNames = tasks.toSet(),
        katachiVersion = katachiVersion,
    )
}

private fun gradlePathOf(module: ModuleData): String =
    module.gradleIdentityPathOrNull ?: module.gradlePathOrNull ?: module.id
