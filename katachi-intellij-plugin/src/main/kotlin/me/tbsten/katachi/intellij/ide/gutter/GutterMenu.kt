package me.tbsten.katachi.intellij.ide.gutter

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
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.presentation.gutter.MenuAvailability
import me.tbsten.katachi.intellij.presentation.gutter.MenuItem
import me.tbsten.katachi.intellij.presentation.gutter.MenuModel
import me.tbsten.katachi.intellij.presentation.gutter.MenuTarget
import java.awt.event.MouseEvent

internal class GutterMenu(
    private val project: Project,
    private val ports: GutterPorts,
    private val captured: CapturedMenu,
) : GutterIconNavigationHandler<PsiElement> {
    private val navigation = GutterNavigation(project, ports.notify)

    override fun navigate(event: MouseEvent, element: PsiElement) {
        sdkCall("open the katachi gutter menu") {
            createPopup(DataManager.getInstance().getDataContext(event.component)).show(RelativePoint(event))
        }
    }

    fun createPopup(parent: DataContext): ListPopup {
        val model = captured.model(availability())
        val context = contextOf(parent)
        return JBPopupFactory.getInstance()
            .createActionGroupPopup(model.title, groupOf(model, context), context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
    }

    fun actionGroup(parent: DataContext): DefaultActionGroup = contextOf(parent).let { groupOf(captured.model(availability()), it) }

    fun title(): String = captured.model(availability()).title

    private fun availability() = MenuAvailability(
        runCheck = ports.action(RUN_CHECK) != null,
        refresh = ports.action(REFRESH) != null,
        checkRunning = false,
    )

    private fun contextOf(parent: DataContext): DataContext = GutterActions.contextOf(project, captured.file, parent)

    private fun groupOf(model: MenuModel, context: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        for (item in model.items) {
            when (item) {
                is MenuItem.Row -> group.add(RowAction(item) { perform(it, context) })
                is MenuItem.Section -> group.add(Separator.create(item.title))
                is MenuItem.Submenu -> group.add(SubMenu(item.text, item.rows.map { row -> RowAction(row) { perform(it, context) } }))
                MenuItem.Separator -> group.add(Separator.getInstance())
            }
        }
        return group
    }

    private fun perform(target: MenuTarget, context: DataContext) {
        sdkCall("do the katachi gutter menu's $target") {
            when (target) {
                is MenuTarget.Site -> openSite(target)
                is MenuTarget.OpenFile -> navigation.openFile(target.path)
                is MenuTarget.Generate -> openGenerateDialog(target)
                MenuTarget.RunCheck -> performAction(RUN_CHECK, context)
                MenuTarget.Refresh -> performAction(REFRESH, context)
            }
        }.onFailure { ports.notify(KatachiBundle.message("gutter.failed", it.message.orEmpty())) }
    }

    private fun openSite(target: MenuTarget.Site) {
        when (val site = captured.resolved[target.site]) {
            is ResolvedSite.Found -> navigation.openLine(site.file, site.line)
            is ResolvedSite.Unresolved, null -> ports.notify(KatachiBundle.message("gutter.notFound", target.site.fileName))
        }
    }

    private fun openGenerateDialog(target: MenuTarget.Generate) {
        val effects = ports.effects() ?: return
        val origin = EntryOrigin.SiblingOf(target.file)
        effects.openGenerateDialog(GenerateDialogRequest(origin, target.template, ports.seeds().seedsFor(origin, target.template)))
    }

    private fun performAction(id: String, context: DataContext) = GutterActions.perform(project, ports, id, context)

    // Dumb aware, or the popup would leave the row out while indexing.
    class SubMenu(text: String, rows: List<AnAction>) : DefaultActionGroup(text, rows), DumbAware {
        init {
            isPopup = true
            // The text is a file name or a rule, not a label with a mnemonic: `activity_main.xml` keeps its `_`.
            templatePresentation.setText(text, false)
        }
    }

    class RowAction(val row: MenuItem.Row, private val run: (MenuTarget) -> Unit) : AnAction(row.text), DumbAware {
        init {
            // `AnAction(String)` reads `_` and `&` as a mnemonic and drops them: a sibling `activity_main.xml` or a rule `fun test_*` would be shown wrong.
            templatePresentation.setText(row.text, false)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = row.enabled
            event.presentation.putClientProperty(ActionUtil.SECONDARY_TEXT, row.trailing)
        }

        override fun actionPerformed(event: AnActionEvent) = choose()

        fun choose() {
            row.target?.takeIf { row.enabled }?.let(run)
        }
    }

    companion object {
        const val RUN_CHECK: String = "katachi.RunCheck"
        const val REFRESH: String = "katachi.RefreshDefinition"
    }
}
