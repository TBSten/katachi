package me.tbsten.katachi.intellij.ide.newmenu

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DynamicActionGroup
import com.intellij.openapi.project.DumbAware

/**
 * New › katachi (issue 12): [definition ›] group › role › template, for the templates that fit the
 * selected directories. Before the list is loaded, one disabled "katachi (loading…)" item (issue 18).
 *
 * [update] and [getChildren] run in the background ([ActionUpdateThread.BGT]) and read only
 * `IdeView.getDirectories()` and the project service's index; never Gradle (issue 4).
 * `getOrChooseDirectory` may show a popup, so only `actionPerformed` of a leaf calls it.
 *
 * ```xml
 * <group id="katachi.NewGroup" class="me.tbsten.katachi.intellij.ide.newmenu.KatachiNewGroup" popup="true"/>
 * ```
 */
internal class KatachiNewGroup : ActionGroup(), DynamicActionGroup, DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // TODO(E2): visible when something fits, or while loading.
        e.presentation.isEnabledAndVisible = false
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        // TODO(E2): the tree of newMenuTreeOf.
        return EMPTY_ARRAY
    }

    companion object {
        /** The group's id in plugin.xml; its text is the bundle key `group.katachi.NewGroup.text`. */
        const val ID: String = "katachi.NewGroup"
    }
}
