package me.tbsten.katachi.intellij.ide.newmenu

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DynamicActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAware
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import java.nio.file.Path

/**
 * New › katachi (issue 12): [definition ›] group › role › template, for the templates that fit the
 * selected directories. Before the list is loaded, one disabled "Loading templates..." item and a
 * load started in the background, never waited for (issue 18). Hidden when katachi cannot be used
 * (not synced, not installed, the JSON failed, loading without the user turned off: issue 3).
 *
 * [update] and [getChildren] run in the background ([ActionUpdateThread.BGT]) and read only
 * `IdeView.getDirectories()` and the project service's index; never Gradle (issue 4).
 * Choosing a directory may show a popup, so nothing here does it: a leaf starts the dialog from
 * the directory `update` already found (decision 23).
 *
 * ```xml
 * <group id="katachi.NewGroup" class="me.tbsten.katachi.intellij.ide.newmenu.KatachiNewGroup" popup="true"/>
 * ```
 *
 * TODO: search inside the menu (issue 12, if there is time).
 */
internal class KatachiNewGroup : ActionGroup(), DynamicActionGroup, DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val context = contextOf(e)
        val visible = context != null && !context.resolution.isEmpty
        e.presentation.isEnabledAndVisible = visible
        // Asking is not waiting: the load starts on the EDT later, and the menu shows the loading item now.
        if (context != null && context.state.availability == EntryAvailability.NotLoaded && context.directories.isNotEmpty()) {
            context.service.ensureLoaded()
        }
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val context = e?.let(::contextOf) ?: return EMPTY_ARRAY
        return newMenuActionsOf(context.resolution.tree, context.resolution, context.service.entryEffects)
    }

    private class Context(
        val service: KatachiProjectService,
        val state: PlacementIndexState,
        val directories: List<Path>,
        val resolution: NewMenuResolution,
    )

    /** `null` when nothing can be shown; an SDK call that fails hides the group instead of failing the update. */
    private fun contextOf(e: AnActionEvent): Context? {
        val project = e.project ?: return null
        val directories = selectedDirectories(e) ?: return null
        if (directories.isEmpty()) return null
        val service = sdkCall("get the katachi project service") { KatachiProjectService.getInstance(project) }.getOrNull() ?: return null
        val state = service.placementIndex.value
        return Context(service, state, directories, NewMenuResolution.of(state, directories))
    }

    /** The selected directories on disk, in the IdeView's order; `null` if the IdeView failed. */
    private fun selectedDirectories(e: AnActionEvent): List<Path>? {
        val view = e.getData(LangDataKeys.IDE_VIEW) ?: return emptyList()
        return sdkCall("read the directories of the IdeView") {
            view.directories.mapNotNull { directory ->
                val file = directory.virtualFile
                file.fileSystem.getNioPath(file)
            }
        }.getOrNull()
    }

    companion object {
        /** The group's id in plugin.xml; its text is the bundle key `group.katachi.NewGroup.text`. */
        const val ID: String = "katachi.NewGroup"
    }
}
