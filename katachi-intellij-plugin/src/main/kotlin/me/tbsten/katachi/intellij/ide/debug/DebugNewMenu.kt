package me.tbsten.katachi.intellij.ide.debug

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import me.tbsten.katachi.intellij.ide.newmenu.KatachiNewGroup
import me.tbsten.katachi.intellij.ide.sdkCall
import java.nio.file.Paths

internal class DebugNewMenu(private val project: Project) {
    fun describeNewMenu(directory: String): String {
        val lines = sdkCall("read New > katachi of $directory") {
            val menu = menuOf(directory) ?: return@sdkCall emptyList<String>()
            menu.leaves.map { leaf -> "item=${leaf.path}|${if (leaf.enabled) "enabled" else "disabled"}" }
        }.getOrDefault(emptyList())
        return lines.ifEmpty { listOf("item=none") }.joinToString("\n")
    }

    fun chooseNewMenuItemLater(directory: String, path: String): String {
        val chosen = sdkCall("choose $path of New > katachi") {
            val menu = menuOf(directory) ?: return@sdkCall null
            val target = menu.leaves.firstOrNull { it.path == path && it.enabled } ?: return@sdkCall null
            ApplicationManager.getApplication().invokeLater(
                {
                    sdkCall("run $path of New > katachi") {
                        val result = ActionUtil.performAction(target.action, eventOf(menu.context, target.action))
                        if (result.isIgnored) LOG.warn("New > katachi item $path was not performed: $result")
                    }
                },
                ModalityState.any(),
            )
            target.label
        }.getOrNull()
        return "chosen=${chosen ?: "none"}"
    }

    private class Leaf(val label: String, val path: String, val action: AnAction, val enabled: Boolean)

    private class Menu(val context: DataContext, val leaves: List<Leaf>)

    private fun menuOf(directory: String): Menu? {
        val context = contextOf(directory) ?: return null
        val group = newGroup(context) ?: return null
        val event = eventOf(context, group)
        ActionUtil.updateAction(group, event)
        if (!event.presentation.isEnabledAndVisible) return null
        return Menu(context, leavesOf(group, context, event, listOf(ROOT)))
    }

    private fun newGroup(context: DataContext): KatachiNewGroup? {
        val platformNew = ActionManager.getInstance().getAction(IdeActions.GROUP_NEW) as? ActionGroup ?: return null
        val children = (platformNew as? DefaultActionGroup)?.childActionsOrStubs ?: return null
        // A group the platform has not created yet is a stub in its parent; only ours is turned into the real group.
        val ours = children.firstOrNull { it is KatachiNewGroup || (it is ActionStubBase && it.id == KatachiNewGroup.ID) } ?: return null
        return ours as? KatachiNewGroup ?: ActionManager.getInstance().getAction(KatachiNewGroup.ID) as? KatachiNewGroup
    }

    private fun childrenOf(group: ActionGroup, event: AnActionEvent): List<AnAction> = when (group) {
        is KatachiNewGroup -> group.getChildren(event).toList()
        is DefaultActionGroup -> group.childActionsOrStubs.toList()
        else -> emptyList()
    }

    private fun leavesOf(group: ActionGroup, context: DataContext, event: AnActionEvent, prefix: List<String>): List<Leaf> =
        childrenOf(group, event).flatMap { child ->
            val name = child.templatePresentation.text.orEmpty()
            if (child is ActionGroup) {
                leavesOf(child, context, event, prefix + name)
            } else {
                val childEvent = eventOf(context, child)
                ActionUtil.updateAction(child, childEvent)
                listOf(Leaf(name, (prefix + name).joinToString("/"), child, childEvent.presentation.isEnabledAndVisible))
            }
        }

    // The VFS lookup (it may refresh) stays outside the read lock, which a synchronous refresh must not be under.
    private fun contextOf(directory: String): DataContext? {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(directory)) ?: return null
        val psi = runReadActionBlocking { PsiManager.getInstance(project).findDirectory(file) } ?: return null
        val view = object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = arrayOf(psi)

            override fun getOrChooseDirectory(): PsiDirectory = psi
        }
        return SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(LangDataKeys.IDE_VIEW, view).build()
    }

    private fun eventOf(context: DataContext, action: AnAction): AnActionEvent =
        AnActionEvent.createEvent(action, context, null, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.NONE, null)

    private companion object {
        const val ROOT = "katachi"
        val LOG = logger<DebugNewMenu>()
    }
}
