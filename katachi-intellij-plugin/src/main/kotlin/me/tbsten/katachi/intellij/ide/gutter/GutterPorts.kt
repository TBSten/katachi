package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.sarif.ViolationsService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.dialog.CaptureSeedPort
import me.tbsten.katachi.intellij.presentation.entry.EntryEffects
import java.time.Instant

/**
 * What the gutter reads of one project. Tests swap the services for the fakes; the platform uses [of].
 *
 * Every function is called in the background under a read lock and may throw: the callers run them inside
 * `sdkCall`. The services are asked for only when [hasDefinitionModule] (the foundation's decision 17: a project
 * without katachi never gets the project service). The last four ([effects], [seeds], [action], [notify]) are for the
 * menu: they are called on the EDT when a row is chosen, and never before. [settings] is read by the annotator.
 *
 * ```kotlin
 * val ports = GutterPorts.of(project)
 * val definitions = ports.definitions().takeIf { ports.hasDefinitionModule() } ?: return emptyList()
 * ```
 */
internal class GutterPorts(
    /** Whether the synced data has a katachi definition module; reads it (kept until the next sync, see [DefinitionModuleCache]), never runs Gradle. */
    val hasDefinitionModule: () -> Boolean,
    /** The definition model, created on the first call. `null` while nothing provides one. */
    val definitions: () -> DefinitionModelService?,
    /** The violations of the last check. `null` while nothing provides one. */
    val violations: () -> ViolationsService?,
    /** When [VirtualFile] was last written, and whether its Document has edits not saved yet. */
    val fileState: (VirtualFile) -> FileState,
    /** What the menu does beyond opening files: the generate dialog. `null` while the project has no katachi service. */
    val effects: () -> EntryEffects?,
    /** The captures a place decides, for the generate dialog the menu opens. */
    val seeds: () -> CaptureSeedPort,
    /** The platform action of [id] (`katachi.RunCheck`, `katachi.RefreshDefinition`), or `null` while the foundation's actions are not registered. */
    val action: (id: String) -> AnAction?,
    /** Tells the user in a balloon that something could not be done. */
    val notify: (String) -> Unit,
    /** The settings the gutter and the annotations follow. Read on any thread. */
    val settings: () -> GutterSettings,
) {
    /** The part of the project's katachi settings the gutter reads. */
    data class GutterSettings(
        /** `Settings | Tools | katachi`: "Show katachi violations in the editor". */
        val showViolationsInEditor: Boolean,
        /** Whether the gutter may load the definition itself (design 2.6, `NotLoaded`). */
        val autoLoadTemplates: Boolean,
    )

    /** What the gutter needs of a file to tell whether the definition's answer is older than it. */
    data class FileState(val modifiedAt: Instant?, val unsaved: Boolean)

    companion object {
        fun of(project: Project): GutterPorts = GutterPorts(
            hasDefinitionModule = { DefinitionModuleCache.getInstance(project).hasDefinitionModule() },
            // The foundation's services (design 4.5): created on the first call, and only in a project with a definition module.
            definitions = { DefinitionPorts.of(project).service() },
            violations = { DefinitionPorts.of(project).violations() },
            fileState = ::fileStateOf,
            effects = { KatachiProjectService.getInstance(project).entryEffects },
            seeds = { seedPortOf(project) },
            action = { id -> ActionManager.getInstance().getAction(id) },
            notify = { text -> GutterNavigation.balloon(project, text) },
            settings = {
                val settings = KatachiSettings.getInstance(project)
                GutterSettings(settings.showViolationsInEditor, settings.autoLoadTemplates)
            },
        )

        private fun seedPortOf(project: Project): CaptureSeedPort {
            val service = KatachiProjectService.getInstance(project)
            return CaptureSeedPort { origin, template ->
                (service.placementIndex.value as? PlacementIndexState.Ready)?.index?.seedsFor(origin, template).orEmpty()
            }
        }

        fun fileStateOf(file: VirtualFile): FileState {
            val modifiedAt = sdkCall("read when ${file.path} was written") { Instant.ofEpochMilli(file.timeStamp) }.getOrNull()
            val unsaved = sdkCall("ask whether ${file.path} has unsaved edits") { FileDocumentManager.getInstance().isFileModified(file) }
                .getOrDefault(false)
            return FileState(modifiedAt, unsaved)
        }
    }
}
