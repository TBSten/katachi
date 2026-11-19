package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo
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
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.data.detect.katachiVersionOf
import me.tbsten.katachi.intellij.model.DetectionResult
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull
import org.jetbrains.plugins.gradle.util.gradlePathOrNull
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

internal class ProjectDataModuleSource(private val project: Project) : SyncedProjectSource {
    override fun read(): SyncedProject = sdkCall("read the Gradle sync data") {
        val linked = GradleSettings.getInstance(project).linkedProjectsSettings.map { it.externalProjectPath }
        val data: Collection<ExternalProjectInfo> =
            if (linked.isEmpty()) emptyList() else ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
        val structures = linked.mapNotNull { path ->
            data.firstOrNull { it.externalProjectPath == path }?.externalProjectStructure
        }
        val fromIdeModules by lazy { katachiVersionsOfIdeModules() }
        val synced = if (linked.isEmpty()) SyncedProject.NotGradle else syncedProjectOf(structures) { directory -> fromIdeModules[directory] }
        logWhenChanged { describe(synced, linked, data) }
        synced
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

    // What the IDE handed over decides whether the gutter and the tool window find a module; idea.log is the only place a user can see it.
    private fun describe(synced: SyncedProject, linked: List<String>, data: Collection<ExternalProjectInfo>): String {
        val detected = when (val result = detectDefinitionModules(synced)) {
            is DetectionResult.NotInstalled -> result.toString()
            else -> result::class.simpleName
        }
        val held = data.map { "${it.externalProjectPath}${if (it.externalProjectStructure == null) " (no structure)" else ""}" }
        val modules = (synced as? SyncedProject.Synced)?.roots?.flatMap { it.modules }.orEmpty().joinToString { module ->
            val tasks = module.taskNames.sorted().let { names -> if (names.size <= 10) names else names.filter { it.startsWith("katachi") } }
            "${module.gradlePath} (tasks=${module.taskNames.size} $tasks, katachi=${module.katachiVersion})"
        }
        val libraries = katachiLibrariesOfIdeModules().joinToString { (directory, names) -> "$directory $names" }
        return "Gradle sync data of ${project.name}: ${synced::class.simpleName} -> $detected; linked=$linked; held=$held; " +
            "modules=[$modules]; the IDE's modules with a katachi library: [$libraries]"
    }

    private fun logWhenChanged(message: () -> String) {
        val text = message()
        if (lastLogged.getAndSet(text) != text) LOG.info(text)
    }

    private companion object {
        val LOG = logger<ProjectDataModuleSource>()

        // The gutter reads again on every pass while nothing is found; one line per change keeps idea.log readable.
        val lastLogged = AtomicReference<String?>(null)
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
