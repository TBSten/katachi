package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClientProperty
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.RefreshReason
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterNavigation
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionErrorTargets
import me.tbsten.katachi.intellij.presentation.authoring.ErrorNotificationModel
import java.util.function.Function
import javax.swing.JComponent

/**
 * The editor notification "katachi could not read the definition: ..." (ide-authoring design 2.4), shown in every test source
 * file of the definition module while the last load found a mistake in it. It has [Go to the error] (the first mistake whose site the test
 * sources hold), [Reload Definition] and [Show details] (the whole message in a dialog). A mistake with no
 * site is visible only here. A file of any other module, and a project without a mistake, get nothing.
 *
 * [collectNotificationData] runs in the background: the decision and the lookup of the site are made there; the returned
 * function builds the panel on the EDT. Whatever an SDK call throws means no notification, never an IDE error.
 *
 * ```xml
 * <editorNotificationProvider implementation="me.tbsten.katachi.intellij.ide.authoring.errors.DefinitionErrorNotificationProvider"/>
 * ```
 */
internal class DefinitionErrorNotificationProvider(
    private val portsOf: (Project) -> GutterPorts,
    /** Puts the dialog of the whole message on screen; a test replaces it. */
    private val showDetails: (Project, String) -> Unit = ::showErrorDialog,
) : EditorNotificationProvider, DumbAware {
    @Suppress("unused") // Called by the platform.
    constructor() : this(GutterPorts::of)

    /** What the panel shows and does, worked out in the background. */
    private class Decision(val model: ErrorNotificationModel, val errors: DefinitionErrors, val definitions: DefinitionModelService, val target: ResolvedSite.Found?)

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val ports = portsOf(project)
        val decision = sdkCall("decide the katachi definition error notification of ${file.name}") { decide(project, ports, file) }.getOrNull() ?: return null
        return Function { editor ->
            sdkCall("build the katachi definition error notification of ${file.name}") { panelOf(project, ports, editor, decision) }.getOrNull()
        }
    }

    private fun decide(project: Project, ports: GutterPorts, file: VirtualFile): Decision? {
        if (!ports.hasDefinitionModule()) return null
        val definitions = ports.definitions() ?: return null
        // The banner is computed again by the platform only when asked: the refresher asks when the mistakes change, so it follows this service too.
        HighlightingRefresher.getInstance(project).follow(definitions, ports.violations())
        val errors = definitions.definitionErrors.value
        val model = DefinitionErrorTargets.notificationOf(errors) ?: return null
        val module = definitions.definitionModuleOf(file) ?: return null
        if (errors.errors.none { it.module == module }) return null
        // The first mistake whose place can be found: one whose file is not among the test sources does not hide the next.
        val target = model.targetCandidates.asSequence().map { errors.errors[it] }
            .mapNotNull { error -> error.site?.let { definitions.resolve(error.module, it) as? ResolvedSite.Found } }
            .firstOrNull()
        return Decision(model, errors, definitions, target)
    }

    private fun panelOf(project: Project, ports: GutterPorts, editor: FileEditor, decision: Decision): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Error)
        panel.text = textOf(decision.model)
        ClientProperty.put(panel, BANNER_KEY, true)
        decision.target?.let { target ->
            panel.createActionLabel(KatachiBundle.message("authoring.error.notification.goTo")) {
                GutterNavigation(project, ports.notify).openLine(target.file, target.line)
            }
        }
        panel.createActionLabel(KatachiBundle.message("authoring.error.notification.reload")) {
            sdkCall("reload the definition") { decision.definitions.refresh(RefreshReason.UserAction) }
        }
        panel.createActionLabel(KatachiBundle.message("authoring.error.notification.details")) {
            sdkCall("show the katachi definition errors") { showDetails(project, wholeTextOf(decision.errors)) }
        }
        return panel
    }

    internal companion object {
        /** The client property that marks a panel as this notification's, for the debug bridge (the text is translated, so it cannot say). */
        val BANNER_KEY: com.intellij.openapi.util.Key<Boolean> = com.intellij.openapi.util.Key.create("katachi.definitionErrorBanner")

        /** "katachi could not read the definition: <first line> (and n more)" and the note when it is from before a change. */
        fun textOf(model: ErrorNotificationModel): String =
            KatachiBundle.message("authoring.error.notification", model.firstLine) +
                (if (model.more > 0) KatachiBundle.message("authoring.error.notification.more", model.more) else "") +
                (if (model.stale) KatachiBundle.message("authoring.error.notification.stale") else "")

        /** Every mistake in full, separated by a blank line, with where each was written when it is known. */
        fun wholeTextOf(errors: DefinitionErrors): String = errors.errors.joinToString("\n\n") { error ->
            val where = error.site?.let { "${it.fileName}:${it.line}\n" }.orEmpty()
            where + error.message
        }
    }
}
