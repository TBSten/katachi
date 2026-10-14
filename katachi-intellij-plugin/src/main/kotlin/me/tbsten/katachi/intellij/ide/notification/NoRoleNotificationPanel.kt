package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.rules.BaselineVerdict
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
 * The two lines of the panel (design 2.2): what is wrong and, when there is a nearby role, which one; for a file held
 * back by its captures, what each level read and where the file belongs. Plain text; the panel escapes it for the label,
 * which reads HTML.
 *
 * ```kotlin
 * val lines = noRoleLines(decision)   // "No role allows this file. ...", "Nearest role: domain.UseCase (...)"
 * ```
 */
internal class NoRoleLines(val first: String, val second: String?, val tooltip: String?)

internal fun noRoleLines(decision: NoRoleDecision.NoRole): NoRoleLines {
    val mismatch = decision.captureMismatch
    val verdict = decision.baseline
    // Held by the baseline: a weak note, not the warning's two lines. The test does not fail on this file.
    if (verdict is BaselineVerdict.Held) {
        return NoRoleLines(
            first = KatachiBundle.message("notification.rules.baselineHeld", verdict.rule),
            second = null,
            tooltip = decision.unexpectedUnder?.let { KatachiBundle.message("notification.rules.noRole.unexpectedUnder", it) },
        )
    }
    // The baseline was read (or there is none) and does not hold the file: the test does fail, so no "unless".
    val sure = verdict == BaselineVerdict.NotHeld
    val main = when {
        // The same first sentence as katachi's `[CaptureMismatch]` block, so the IDE and the test say one thing.
        mismatch != null -> KatachiBundle.message(if (sure) "notification.rules.captureMismatch.sure" else "notification.rules.captureMismatch", mismatch.roleName, mismatch.disagreeingNames.joinToString(", "))
        decision.untracked -> KatachiBundle.message(if (sure) "notification.rules.noRole.untracked.sure" else "notification.rules.noRole.untracked")
        else -> KatachiBundle.message(if (sure) "notification.rules.noRole.sure" else "notification.rules.noRole")
    }
    // A sentence ending in "." needs a space before the note, one ending in "。" does not.
    val stale = if (decision.stale) (if (main.last().code < ASCII_LIMIT) " " else "") + KatachiBundle.message("notification.rules.noRole.stale") else ""
    val nearest = decision.nearby.firstOrNull()
    val second = when {
        mismatch != null -> listOfNotNull(mismatch.lines.joinToString("; ").ifEmpty { null }, mismatch.expectedPath?.let { KatachiBundle.message("notification.rules.captureMismatch.moveTo", it) })
            .joinToString(" ").ifEmpty { null }
        else -> nearest?.let { KatachiBundle.message("notification.rules.noRole.nearby", it.roleName, shortDirectory(it.directory)) }
    }
    return NoRoleLines(
        first = main + stale,
        second = second,
        tooltip = decision.unexpectedUnder?.let { KatachiBundle.message("notification.rules.noRole.unexpectedUnder", it) },
    )
}

/** The panel of the no-role notification, which keeps the plain [lines] it shows: [KatachiDebugBridge.describeNoRole] reads them, not the escaped HTML of the label. */
internal class NoRolePanel(val owner: FileEditor, val lines: NoRoleLines, status: EditorNotificationPanel.Status = EditorNotificationPanel.Status.Warning) : KatachiNotificationPanel(owner, status)

/**
 * The panel of the "no role holds this file" notification: a warning (Info when the baseline holds the file back), the lines of [noRoleLines], then
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
    // A file the baseline holds back is not a violation: Info, not Warning.
    val panel = NoRolePanel(editor, lines, if (decision.baseline is BaselineVerdict.Held) EditorNotificationPanel.Status.Info else EditorNotificationPanel.Status.Warning)
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
