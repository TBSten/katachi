package me.tbsten.katachi.intellij.ide.notification

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InplaceButton
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import javax.swing.JLabel

internal class NotificationPanelActions(
    val create: () -> Unit,
    val viewTemplate: () -> Unit,
    val openSettings: () -> Unit,
    val copyCommand: () -> Unit,
    val dismiss: () -> Unit,
)

internal fun katachiNotificationPanel(editor: FileEditor, decision: NotificationDecision, actions: NotificationPanelActions): EditorNotificationPanel? {
    if (decision == NotificationDecision.Hidden) return null
    val status = if (decision is NotificationDecision.FailedWithCommand) EditorNotificationPanel.Status.Warning else EditorNotificationPanel.Status.Info
    val panel = KatachiNotificationPanel(editor, status)
    when (decision) {
        NotificationDecision.Hidden -> Unit
        is NotificationDecision.CreateFromTemplate -> {
            panel.text = decision.title
                ?.let { KatachiBundle.message("notification.editor.empty.one", it) }
                ?: KatachiBundle.message("notification.editor.empty.many", decision.count)
            panel.createActionLabel(KatachiBundle.message("notification.editor.create")) { actions.create() }
        }
        is NotificationDecision.ViewTemplate -> {
            panel.text = decision.title
                ?.let { KatachiBundle.message("notification.editor.matched.one", it) }
                ?: KatachiBundle.message("notification.editor.matched.many", decision.count)
            panel.createActionLabel(KatachiBundle.message("notification.editor.view")) { actions.viewTemplate() }
        }
        is NotificationDecision.Generating -> panel.text = decision.command
            ?.let { KatachiBundle.message("notification.editor.generating.command", it) }
            ?: KatachiBundle.message("notification.editor.generating")
        is NotificationDecision.FailedWithCommand -> {
            panel.text = KatachiBundle.message(
                "notification.editor.failed",
                KatachiBundle.message("notification.editor.failed.command", decision.command),
            )
            panel.createActionLabel(KatachiBundle.message("notification.editor.failed.copy")) { actions.copyCommand() }
        }
    }
    panel.addSettingsButton(actions.openSettings)
    panel.setCloseAction { actions.dismiss() }.toolTipText = KatachiBundle.message("notification.editor.dismiss.tooltip")
    return panel
}

internal open class KatachiNotificationPanel(editor: FileEditor, status: Status) : EditorNotificationPanel(editor, status) {
    // The tooltip is read as HTML by the label: the caller escapes.
    fun setTextTooltip(html: String) {
        // The platform's text label (`myLabel`) is deprecated for removal; the links are HyperlinkLabels, not JLabels.
        UIUtil.findComponentsOfType(this, JLabel::class.java).forEach { it.toolTipText = html }
    }

    fun addSettingsButton(open: () -> Unit) {
        val tooltip = KatachiBundle.message("notification.editor.settings.tooltip")
        myLinksPanel.add(InplaceButton(tooltip, AllIcons.General.GearPlain) { open() })
    }
}
