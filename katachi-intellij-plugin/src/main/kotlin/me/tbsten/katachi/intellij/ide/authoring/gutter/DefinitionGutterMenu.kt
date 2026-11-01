package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.authoring.BundleAuthoringStrings
import me.tbsten.katachi.intellij.ide.gutter.GutterActions
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.gutter.GutterNavigation
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionGutterMark
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMenuAvailability
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMenuItem
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMenuModel
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMenuTarget
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import java.awt.event.MouseEvent

/**
 * The popup a definition ◆ opens (ide-authoring design 4.3): [DefinitionMenuModel]'s rows as an action group in a
 * speed-search list. Everything it shows was captured in the background ([DefinitionGutterMark]); the first call on the EDT is [navigate].
 *
 * Generating starts the dialog from the definition file ([EntryOrigin.Definition]), which decides no capture. Running the
 * check and reloading are the foundation's actions, looked up by id when the menu opens and never run by this class.
 *
 * ```kotlin
 * LineMarkerInfo(leaf, leaf.textRange, icon, tooltip, DefinitionGutterMenu(project, ports, mark, file), alignment) { tooltip }
 * ```
 */
internal class DefinitionGutterMenu(
    private val project: Project,
    private val ports: GutterPorts,
    private val mark: DefinitionGutterMark,
    private val file: VirtualFile,
) : GutterIconNavigationHandler<PsiElement> {
    private val navigation = GutterNavigation(project, ports.notify)

    override fun navigate(event: MouseEvent, element: PsiElement) {
        sdkCall("open the katachi definition gutter menu") {
            createPopup(DataManager.getInstance().getDataContext(event.component)).show(RelativePoint(event))
        }
    }

    /** The popup of the menu's current rows, not shown yet; [parent] is the data context of the gutter it opens from. */
    fun createPopup(parent: DataContext): ListPopup {
        val model = model()
        val context = GutterActions.contextOf(project, file, parent)
        return JBPopupFactory.getInstance()
            .createActionGroupPopup(model.title, groupOf(model, context), context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
    }

    /** The menu's current rows as an action group: what the popup shows, and what a test chooses from. */
    fun actionGroup(parent: DataContext): DefaultActionGroup = groupOf(model(), GutterActions.contextOf(project, file, parent))

    private fun model(): DefinitionMenuModel = mark.menu(
        DefinitionMenuAvailability(runCheck = ports.action(GutterMenu.RUN_CHECK) != null, refresh = ports.action(GutterMenu.REFRESH) != null),
        BundleAuthoringStrings,
    )

    private fun groupOf(model: DefinitionMenuModel, context: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        for (item in model.items) {
            when (item) {
                is DefinitionMenuItem.Row -> group.add(RowAction(item) { perform(it, context) })
                is DefinitionMenuItem.Section -> group.add(Separator.create(item.title))
                is DefinitionMenuItem.Submenu -> group.add(GutterMenu.SubMenu(item.text, item.rows.map { row -> RowAction(row) { perform(it, context) } }))
                DefinitionMenuItem.Separator -> group.add(Separator.getInstance())
            }
        }
        return group
    }

    private fun perform(target: DefinitionMenuTarget, context: DataContext) {
        sdkCall("do the katachi definition gutter menu's $target") {
            when (target) {
                is DefinitionMenuTarget.OpenFile -> navigation.openFile(target.path)
                is DefinitionMenuTarget.Generate -> generate(target)
                is DefinitionMenuTarget.Reveal -> ports.effects()?.revealTemplateInToolWindow(target.template)
                DefinitionMenuTarget.RunCheck -> GutterActions.perform(project, ports, GutterMenu.RUN_CHECK, context)
                DefinitionMenuTarget.Refresh -> GutterActions.perform(project, ports, GutterMenu.REFRESH, context)
            }
        }.onFailure { ports.notify(KatachiBundle.message("gutter.failed", it.message.orEmpty())) }
    }

    private fun generate(target: DefinitionMenuTarget.Generate) {
        val effects = ports.effects() ?: return
        val origin = EntryOrigin.Definition(target.file)
        effects.openGenerateDialog(GenerateDialogRequest(origin, target.template, ports.seeds().seedsFor(origin, target.template)))
    }

    /** One row: its text, the directory on the right, and whether it can be chosen. */
    class RowAction(val row: DefinitionMenuItem.Row, private val run: (DefinitionMenuTarget) -> Unit) : AnAction(row.text), DumbAware {
        init {
            // `AnAction(String)` reads `_` and `&` as a mnemonic: a file `activity_main.xml` keeps its `_`.
            templatePresentation.setText(row.text, false)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = row.enabled
            event.presentation.putClientProperty(ActionUtil.SECONDARY_TEXT, row.trailing)
        }

        override fun actionPerformed(event: AnActionEvent) = choose()

        /** What choosing the row does: nothing for a row that cannot be chosen. */
        fun choose() {
            row.target?.takeIf { row.enabled }?.let(run)
        }
    }
}
