package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.ide.KatachiBundle

/**
 * Runs the foundation's platform actions (`katachi.RunCheck`, `katachi.RefreshDefinition`) for the gutter menu and the
 * quick fixes: by id, with the file and the project in the data context (`katachi.RunCheck` finds its module from the
 * file). Gutter code never starts Gradle itself.
 *
 * Call on the EDT, inside `sdkCall`.
 *
 * ```kotlin
 * GutterActions.perform(project, ports, GutterMenu.RUN_CHECK, GutterActions.contextOf(project, file, parent))
 * ```
 */
internal object GutterActions {
    /** [parent]'s data context with [file] and [project] in it. */
    fun contextOf(project: Project, file: VirtualFile, parent: DataContext): DataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.VIRTUAL_FILE, file)
        .add(CommonDataKeys.PROJECT, project)
        .setParent(parent)
        .build()

    /** Performs the action [id]; nothing when it is not registered. A balloon says so when the platform ignored it (it says why itself while indexing). */
    fun perform(project: Project, ports: GutterPorts, id: String, context: DataContext) {
        val action = ports.action(id) ?: return
        val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.POPUP, ActionUiKind.POPUP, null)
        val result = ActionUtil.performAction(action, event)
        if (result.isIgnored && !DumbService.isDumb(project)) {
            ports.notify(KatachiBundle.message("gutter.actionIgnored", action.templatePresentation.text.orEmpty()))
        }
    }
}
