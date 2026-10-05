package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.testFramework.TestActionEvent

/** What a user reads in the open menu: a text per row (`(disabled) ` before one that cannot be chosen, ` | ` before its right side). */
internal fun GutterMenu.shown(): List<String> = shownOf(actionGroup(DataContext.EMPTY_CONTEXT).getChildren(null).toList())

private fun shownOf(children: List<AnAction>, indent: String = ""): List<String> = children.flatMap { child ->
    when {
        child is Separator -> listOf(child.text?.takeIf { it.isNotEmpty() }?.let { "== $it" } ?: "-----")
        child is ActionGroup -> listOf("$indent${child.templatePresentation.text} >") + shownOf(child.getChildren(null).toList(), "$indent  ")
        else -> listOf(indent + rowText(child))
    }
}

private fun rowText(action: AnAction): String {
    val event = TestActionEvent.createTestEvent(action)
    ActionUtil.updateAction(action, event)
    val trailing = event.presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)
    return (if (event.presentation.isEnabled) "" else "(disabled) ") + action.templatePresentation.text + (trailing?.let { " | $it" } ?: "")
}

/** Every row of the menu (the sub menus' too), in order. */
internal fun GutterMenu.rows(): List<AnAction> = flat(actionGroup(DataContext.EMPTY_CONTEXT).getChildren(null).toList())

private fun flat(children: List<AnAction>): List<AnAction> = children.flatMap { child ->
    when {
        child is Separator -> emptyList()
        child is ActionGroup -> flat(child.getChildren(null).toList())
        else -> listOf(child)
    }
}

/** The row whose text starts with [prefix]; a test that names none fails. */
internal fun GutterMenu.row(prefix: String): AnAction =
    rows().firstOrNull { it.templatePresentation.text.startsWith(prefix) }
        ?: throw AssertionError("No row starts with \"$prefix\" in ${shown()}")

/** Chooses the row as the popup does: its update decides whether it is enabled, then it is performed. */
internal fun choose(action: AnAction) {
    val event = TestActionEvent.createTestEvent(action)
    ActionUtil.updateAction(action, event)
    if (event.presentation.isEnabled) action.actionPerformed(event)
}

internal fun isEnabled(action: AnAction): Boolean {
    val event = TestActionEvent.createTestEvent(action)
    ActionUtil.updateAction(action, event)
    return event.presentation.isEnabled
}

/** Stands for the foundation's `katachi.RunCheck` / `katachi.RefreshDefinition`: records what it was performed with. */
internal class RecordingAction(text: String, private val onPerformed: () -> Unit = {}) : AnAction(text), DumbAware {
    val events: MutableList<AnActionEvent> = mutableListOf()

    override fun actionPerformed(event: AnActionEvent) {
        events += event
        onPerformed()
    }
}
