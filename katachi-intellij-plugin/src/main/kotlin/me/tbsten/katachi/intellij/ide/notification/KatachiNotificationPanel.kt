package me.tbsten.katachi.intellij.ide.notification

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InplaceButton
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision

/** What the panel's links and buttons do. Each is called on the EDT when clicked. */
internal class NotificationPanelActions(
    val create: () -> Unit,
    val viewTemplate: () -> Unit,
    val openSettings: () -> Unit,
    val dismiss: () -> Unit,
)

/**
 * The panel of a katachi notification: its text, at most one link ([Create] or [View Templates]),
 * then ⚙ and ×, both [InplaceButton]s told apart by their tooltips. Built on the EDT from a
 * decision made in the background; it decides nothing itself.
 *
 * ```kotlin
 * val panel = katachiNotificationPanel(editor, NotificationDecision.CreateFromTemplate(matches), actions)
 * ```
 */
internal fun katachiNotificationPanel(editor: FileEditor, decision: NotificationDecision, actions: NotificationPanelActions): EditorNotificationPanel? {
    if (decision == NotificationDecision.Hidden) return null
    val status = if (decision is NotificationDecision.FailedWithCommand) EditorNotificationPanel.Status.Warning else EditorNotificationPanel.Status.Info
    val panel = KatachiNotificationPanel(editor, status)
    when (decision) {
        NotificationDecision.Hidden -> Unit
        is NotificationDecision.CreateFromTemplate -> {
            panel.text = KatachiBundle.message("notification.editor.empty")
            panel.createActionLabel(KatachiBundle.message("notification.editor.create")) { actions.create() }
        }
        is NotificationDecision.ViewTemplate -> {
            panel.text = KatachiBundle.message("notification.editor.matched")
            panel.createActionLabel(KatachiBundle.message("notification.editor.view")) { actions.viewTemplate() }
        }
        is NotificationDecision.Generating -> panel.text = decision.command
            ?.let { KatachiBundle.message("notification.editor.generating.command", it) }
            ?: KatachiBundle.message("notification.editor.generating")
        is NotificationDecision.FailedWithCommand -> panel.text = KatachiBundle.message(
            "notification.editor.failed",
            KatachiBundle.message("notification.editor.failed.command", decision.command),
        )
    }
    panel.addSettingsButton(actions.openSettings)
    panel.setCloseAction { actions.dismiss() }.toolTipText = KatachiBundle.message("notification.editor.dismiss.tooltip")
    return panel
}

/** An [EditorNotificationPanel] with ⚙ after the links, where the platform's own links go. */
private class KatachiNotificationPanel(editor: FileEditor, status: Status) : EditorNotificationPanel(editor, status) {
    fun addSettingsButton(open: () -> Unit) {
        val tooltip = KatachiBundle.message("notification.editor.settings.tooltip")
        myLinksPanel.add(InplaceButton(tooltip, AllIcons.General.GearPlain) { open() })
    }
}
