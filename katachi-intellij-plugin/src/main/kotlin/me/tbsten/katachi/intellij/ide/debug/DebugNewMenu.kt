package me.tbsten.katachi.intellij.ide.debug

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
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

/**
 * New > katachi of a directory, read and chosen without the Project view: the Driver smoke cannot right-click a tree row
 * on a local macOS (the real mouse and keyboard do not reach the IDE under test), so this builds the context the menu would
 * get (the project and an [IdeView] of the directory) and asks the same group for its items.
 *
 * ```kotlin
 * DebugNewMenu(project).describeNewMenu("/work/kmp/feature")           // "item=katachi/feature/component/<name>.kt|enabled"
 * DebugNewMenu(project).chooseNewMenuItemLater("/work/kmp/feature", "katachi/feature/component/<name>.kt")
 * ```
 */
internal class DebugNewMenu(private val project: Project) {
    /**
     * The items of New > katachi on [directory], one `item=katachi/<group>/.../<label>|enabled` (or `|disabled`) per leaf in the
     * order shown (a group is not a line of its own), or `item=none` when the group is hidden there. A menu that is still loading
     * shows its disabled "Loading templates..." item, so the smoke waits until a wanted item appears.
     */
    fun describeNewMenu(directory: String): String {
        val lines = sdkCall("read New > katachi of $directory") {
            val menu = menuOf(directory) ?: return@sdkCall emptyList<String>()
            menu.leaves.map { leaf -> "item=${leaf.path}|${if (leaf.enabled) "enabled" else "disabled"}" }
        }.getOrDefault(emptyList())
        return lines.ifEmpty { listOf("item=none") }.joinToString("\n")
    }

    /**
     * Chooses the enabled item at [path] (`katachi/<group>/.../<label>`, as [describeNewMenu] says it) with the choosing left for
     * later on the EDT: an item opens the generate dialog, which is modal and would hold this call. `chosen=<label>` is answered once
     * the choice is queued; `chosen=none` for no such item or a disabled one (a real menu does not run it either).
     */
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

    /** The leaves of the group as New puts it on [directory], or `null` when the group is not there or hidden. */
    private fun menuOf(directory: String): Menu? {
        val context = contextOf(directory) ?: return null
        val group = newGroup(context) ?: return null
        val event = eventOf(context, group)
        group.update(event)
        if (!event.presentation.isEnabledAndVisible) return null
        return Menu(context, leavesOf(group, context, event, listOf(ROOT)))
    }

    /** The katachi group found by walking the platform's own New group, so removing it from there hides it here too. */
    private fun newGroup(context: DataContext): KatachiNewGroup? {
        val platformNew = ActionManager.getInstance().getAction(IdeActions.GROUP_NEW) as? ActionGroup ?: return null
        return platformNew.getChildren(eventOf(context, platformNew)).filterIsInstance<KatachiNewGroup>().firstOrNull()
    }

    /** The leaves under [group] with their path of names and whether each is enabled; a group (submenu) only adds its name to the path. */
    private fun leavesOf(group: ActionGroup, context: DataContext, event: AnActionEvent, prefix: List<String>): List<Leaf> =
        group.getChildren(event).flatMap { child ->
            val name = child.templatePresentation.text.orEmpty()
            if (child is ActionGroup) {
                leavesOf(child, context, event, prefix + name)
            } else {
                val childEvent = eventOf(context, child)
                child.update(childEvent)
                listOf(Leaf(name, (prefix + name).joinToString("/"), child, childEvent.presentation.isEnabledAndVisible))
            }
        }

    /** The directory's PSI: the VFS lookup (it may refresh) is outside the read lock, which a synchronous refresh must not be under. */
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
