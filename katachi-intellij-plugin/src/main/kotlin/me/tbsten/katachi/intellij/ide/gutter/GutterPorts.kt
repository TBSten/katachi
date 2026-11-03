package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.DefinitionModulePresence
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.sarif.ViolationsService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.dialog.CaptureSeedPort
import me.tbsten.katachi.intellij.presentation.entry.EntryEffects
import java.time.Instant

// Every function is called in the background under a read lock and may throw: callers run them inside `sdkCall`.
// The last four (effects, seeds, action, notify) are called on the EDT when a row is chosen, never before.
internal class GutterPorts(
    val hasDefinitionModule: () -> Boolean,
    val definitions: () -> DefinitionModelService?,
    val violations: () -> ViolationsService?,
    val fileState: (VirtualFile) -> FileState,
    val effects: () -> EntryEffects?,
    val seeds: () -> CaptureSeedPort,
    val action: (id: String) -> AnAction?,
    val notify: (String) -> Unit,
    val settings: () -> GutterSettings,
) {
    data class GutterSettings(
        val showViolationsInEditor: Boolean,
        val autoLoadTemplates: Boolean,
    )

    data class FileState(val modifiedAt: Instant?, val unsaved: Boolean)

    companion object {
        fun of(project: Project): GutterPorts = GutterPorts(
            hasDefinitionModule = { DefinitionModulePresence.getInstance(project).hasDefinitionModule() },
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
