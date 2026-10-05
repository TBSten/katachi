package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.rules.NoRoleDecision
import me.tbsten.katachi.intellij.presentation.rules.shortDirectory
import javax.swing.JComponent

private const val ASCII_LIMIT = 128

/** What the links and buttons of the no-role panel do. Each is called on the EDT when clicked. */
internal class NoRolePanelActions(
    /** Opens the popup of nearby roles under the given component (the link that was clicked). */
    val showNearby: (JComponent) -> Unit,
    val openDefinition: () -> Unit,
    val openSettings: () -> Unit,
    val dismiss: () -> Unit,
)

/**
 * The two lines of the panel (design 2.2): what is wrong and, when there is a nearby role, which one. Plain text; the
 * panel escapes it for the label, which reads HTML.
 *
 * ```kotlin
 * val lines = noRoleLines(decision)   // "No role allows this file. ...", "Nearest role: domain.UseCase (...)"
 * ```
 */
internal class NoRoleLines(val first: String, val second: String?, val tooltip: String?)

internal fun noRoleLines(decision: NoRoleDecision.NoRole): NoRoleLines {
    val main = KatachiBundle.message(if (decision.untracked) "notification.rules.noRole.untracked" else "notification.rules.noRole")
    // A sentence ending in "." needs a space before the note, one ending in "。" does not.
    val stale = if (decision.stale) (if (main.last().code < ASCII_LIMIT) " " else "") + KatachiBundle.message("notification.rules.noRole.stale") else ""
    val nearest = decision.nearby.firstOrNull()
    return NoRoleLines(
        first = main + stale,
        second = nearest?.let { KatachiBundle.message("notification.rules.noRole.nearby", it.roleName, shortDirectory(it.directory)) },
        tooltip = decision.unexpectedUnder?.let { KatachiBundle.message("notification.rules.noRole.unexpectedUnder", it) },
    )
}

/** The panel of the no-role notification, which keeps the plain [lines] it shows: [KatachiDebugBridge.describeNoRole] reads them, not the escaped HTML of the label. */
internal class NoRolePanel(val owner: FileEditor, val lines: NoRoleLines) : KatachiNotificationPanel(owner, Status.Warning)

/**
 * The panel of the "no role holds this file" notification: a warning, the lines of [noRoleLines], then
 * [Nearby roles...] (always: the draft of a role is in its popup), [Open definition] (only when `architecture { }` can be
 * opened), ⚙ and ×. Built on the EDT from a decision made in the background; it decides nothing itself.
 *
 * Role names and paths come from the user's definition: they are escaped in the label and the tooltip, which read HTML.
 *
 * ```kotlin
 * val panel = noRoleNotificationPanel(editor, found.decision, actions)
 * ```
 */
internal fun noRoleNotificationPanel(editor: FileEditor, decision: NoRoleDecision.NoRole, actions: NoRolePanelActions): EditorNotificationPanel {
    val lines = noRoleLines(decision)
    val panel = NoRolePanel(editor, lines)
    NoRolePanels.register(panel)
    panel.text = if (lines.second == null) lines.first else "<html>${StringUtil.escapeXmlEntities(lines.first)}<br>${StringUtil.escapeXmlEntities(lines.second)}</html>"
    lines.tooltip?.let { panel.setTextTooltip("<html>${StringUtil.escapeXmlEntities(it)}</html>") }

    var nearbyLink: HyperlinkLabel? = null
    nearbyLink = panel.createActionLabel(KatachiBundle.message("notification.rules.nearbyRoles")) { nearbyLink?.let(actions.showNearby) }
    if (decision.canOpenDefinition) panel.createActionLabel(KatachiBundle.message("notification.rules.openDefinition")) { actions.openDefinition() }
    panel.addSettingsButton(actions.openSettings)
    panel.setCloseAction { actions.dismiss() }.toolTipText = KatachiBundle.message("notification.rules.dismiss.tooltip")
    return panel
}
