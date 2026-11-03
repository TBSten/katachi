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

// Call on the EDT, inside `sdkCall`.
internal object GutterActions {
    fun contextOf(project: Project, file: VirtualFile, parent: DataContext): DataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.VIRTUAL_FILE, file)
        .add(CommonDataKeys.PROJECT, project)
        .setParent(parent)
        .build()

    fun perform(project: Project, ports: GutterPorts, id: String, context: DataContext) {
        val action = ports.action(id) ?: return
        val event = AnActionEvent.createEvent(action, context, null, ActionPlaces.POPUP, ActionUiKind.POPUP, null)
        val result = ActionUtil.performAction(action, event)
        if (result.isIgnored && !DumbService.isDumb(project)) {
            ports.notify(KatachiBundle.message("gutter.actionIgnored", action.templatePresentation.text.orEmpty()))
        }
    }
}
