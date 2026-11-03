package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.rules.NoRoleFound
import me.tbsten.katachi.intellij.ide.rules.RuleEffects
import me.tbsten.katachi.intellij.ide.rules.RulesPorts
import me.tbsten.katachi.intellij.ide.rules.decideNoRole
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.presentation.rules.NotificationWinner
import me.tbsten.katachi.intellij.presentation.rules.arbitrateNotifications
import java.nio.file.Path
import java.util.function.Function
import javax.swing.JComponent

// collectNotificationData runs in the background under a read lock and returns null unless this notification wins `arbitrateNotifications`.
internal class NoRoleNotificationProvider(
    private val portsOf: (Project) -> RulesPorts,
    private val templateDecisionOf: (Project, VirtualFile, Path) -> NotificationDecision,
    private val showPopup: (JComponent, DefaultActionGroup) -> Unit = ::showNoRolePopup,
) : EditorNotificationProvider, DumbAware {
    @Suppress("unused") // Called by the platform.
    constructor() : this(RulesPorts::of, { project, file, path -> KatachiEditorNotificationProvider().templateDecisionOf(project, file, path) })

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val path = sdkCall("find the path of ${file.name}") { file.fileSystem.getNioPath(file) }.getOrNull() ?: return null
        val ports = portsOf(project)
        val found = sdkCall("decide the no-role notification of $path") { decide(project, ports, file, path) }.getOrNull()
        if (found == null) {
            NoRolePanels.clear(file)
            return null
        }
        // A panel the IDE fails to build is no panel, not an IDE error.
        return Function { editor ->
            sdkCall("build the no-role notification of $path") { noRoleNotificationPanel(editor, found.decision, actionsFor(project, ports, found, file, path)) }.getOrNull()
        }
    }

    private fun decide(project: Project, ports: RulesPorts, file: VirtualFile, path: Path): NoRoleFound? {
        val found = decideNoRole(ports, file, path) ?: return null
        // Only a file with something to show asks the template side, which reads the index and the Document.
        val template = sdkCall("decide the template notification of $path") { templateDecisionOf(project, file, path) }.getOrNull() ?: NotificationDecision.Hidden
        return found.takeIf { arbitrateNotifications(template, found.decision) == NotificationWinner.Rule }
    }

    private fun actionsFor(project: Project, ports: RulesPorts, found: NoRoleFound, file: VirtualFile, path: Path): NoRolePanelActions {
        val effects: RuleEffects = ports.effects()
        return NoRolePanelActions(
            showNearby = { link -> showPopup(link, noRolePopupGroup(found, effects)) },
            openDefinition = { found.architectureSite.let { site -> if (site is ResolvedSite.Found) effects.open(site) } },
            openSettings = { effects.openSettings() },
            dismiss = {
                ports.memory().dismissNoRole(path)
                sdkCall("update the no-role notification of $path") { EditorNotifications.getInstance(project).updateNotifications(file) }
            },
        )
    }
}
