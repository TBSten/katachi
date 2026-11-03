package me.tbsten.katachi.intellij.ide.newmenu

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.notification.NotificationPorts
import me.tbsten.katachi.intellij.ide.notification.shouldEnsureLoaded
import me.tbsten.katachi.intellij.ide.sdkCall
import java.nio.file.Path

// update and getChildren run on the BGT and read only `IdeView.getDirectories()` and the index; never show a popup or run Gradle here.
internal class KatachiNewGroup(
    private val portsOf: (Project) -> NotificationPorts,
) : ActionGroup(), DumbAware {
    @Suppress("unused") // Created by the platform from plugin.xml.
    constructor() : this(NotificationPorts::of)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val context = contextOf(e)
        val visible = context != null && !context.resolution.isEmpty
        sdkCall("show or hide New > katachi") { e.presentation.isEnabledAndVisible = visible }
        // Asking is not waiting: the load starts on the EDT later, and the menu shows the loading item now.
        if (context != null && context.directories.isNotEmpty() && shouldEnsureLoaded(context.state, context.loadWithoutUser)) {
            context.service.ensureLoaded()
        }
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val context = e?.let(::contextOf) ?: return EMPTY_ARRAY
        return sdkCall("build the items of New > katachi") { newMenuActionsOf(context.resolution.tree, context.resolution, context.service.entryEffects) }
            .getOrDefault(EMPTY_ARRAY)
    }

    private class Context(
        val service: KatachiProjectService,
        val state: PlacementIndexState,
        val loadWithoutUser: Boolean,
        val directories: List<Path>,
        val resolution: NewMenuResolution,
    )

    private fun contextOf(e: AnActionEvent): Context? = sdkCall("read the context of New > katachi") {
        val project = e.project ?: return@sdkCall null
        val directories = selectedDirectories(e) ?: return@sdkCall null
        if (directories.isEmpty()) return@sdkCall null
        val ports = portsOf(project)
        val service = ports.serviceForEntries() ?: return@sdkCall null
        val state = service.placementIndex.value
        Context(service, state, ports.settings().loadWithoutUser, directories, NewMenuResolution.of(state, directories))
    }.getOrNull()

    private fun selectedDirectories(e: AnActionEvent): List<Path>? {
        val view = e.getData(LangDataKeys.IDE_VIEW) ?: return emptyList()
        return view.directories.mapNotNull { directory ->
            val file = directory.virtualFile
            file.fileSystem.getNioPath(file)
        }
    }

    companion object {
        const val ID: String = "katachi.NewGroup"
    }
}
