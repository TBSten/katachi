package me.tbsten.katachi.intellij.ide.newmenu

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.EntryEffects
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.presentation.entry.NewMenuNode

/** The actions of [nodes], for the children of New › katachi. Groups nest as submenus. */
internal fun newMenuActionsOf(nodes: List<NewMenuNode>, resolution: NewMenuResolution, effects: EntryEffects): Array<AnAction> =
    nodes.mapNotNull { node -> actionOf(node, resolution, effects) }.toTypedArray()

private fun actionOf(node: NewMenuNode, resolution: NewMenuResolution, effects: EntryEffects): AnAction? = when (node) {
    NewMenuNode.Loading -> LoadingItemAction()
    is NewMenuNode.Definition -> submenu(node.definition.gradlePath, node.children, resolution, effects)
    is NewMenuNode.Group -> submenu(node.name, node.children, resolution, effects)
    is NewMenuNode.Role -> submenu(node.name, node.children, resolution, effects)
    is NewMenuNode.Template -> resolution.originOf(node.match)?.let { origin ->
        TemplateItemAction(
            node.label,
            GenerateDialogRequest(EntryOrigin.NewMenuDirectory(origin), node.match.id, node.match.decided),
            effects,
        )
    }
}

private fun submenu(name: String, children: List<NewMenuNode>, resolution: NewMenuResolution, effects: EntryEffects): AnAction =
    DefaultActionGroup(name, true).also { group ->
        // The names are the user's: "_" and "&" are not mnemonics.
        group.templatePresentation.setText(name, false)
        group.addAll(newMenuActionsOf(children, resolution, effects).toList())
    }

/**
 * One template: picking it opens the generate dialog on it, starting from the directory it fit
 * first. It does not ask the IdeView to choose a directory (that may open a popup):
 * the directories were read by `update`, and the origin is already decided.
 */
internal class TemplateItemAction(
    label: String,
    private val request: GenerateDialogRequest,
    private val effects: EntryEffects,
) : AnAction(), DumbAware {
    init {
        templatePresentation.setText(label, false)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        sdkCall("open the generate dialog for ${request.initialTemplate}") { effects.openGenerateDialog(request) }
    }
}

/** "Loading templates...": shown before the list is loaded, and never enabled (issue 18). */
internal class LoadingItemAction : AnAction(), DumbAware {
    init {
        templatePresentation.setText(KatachiBundle.message("newMenu.loading"), false)
        templatePresentation.description = KatachiBundle.message("newMenu.loading.description")
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Not on the template presentation: the platform forbids changing it.
        e.presentation.isEnabled = false
    }

    override fun actionPerformed(e: AnActionEvent) = Unit
}
