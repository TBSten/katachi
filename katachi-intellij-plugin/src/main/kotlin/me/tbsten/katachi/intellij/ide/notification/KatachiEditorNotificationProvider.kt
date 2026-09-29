package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import me.tbsten.katachi.intellij.data.placement.CommentSyntax
import me.tbsten.katachi.intellij.data.placement.EmptyFileRule
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.FileContentState
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.presentation.entry.NotificationInput
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.presentation.entry.decideNotification
import java.nio.file.Path
import java.util.function.Function
import javax.swing.JComponent

/**
 * The editor notification on files a katachi template fits (design section 1): [Create] on an empty
 * one, [View template] on one with content, with ⚙ and ×.
 *
 * [collectNotificationData] runs in the background under a read lock (`@RequiresReadLock` in 261), so
 * the whole decision is made there: settings, the index state, the matches, the empty-file rule over
 * the cached Document or the disk, the memory and the ledger, then `decideNotification`. It returns
 * `null` for no notification. The returned function only builds the panel on the EDT. Without an index it calls `KatachiProjectService.ensureLoaded()` and shows nothing.
 *
 * A project whose synced data has no katachi definition module gets no project service at all
 * (decision 17). Whatever an SDK call throws on the way means no notification, never an IDE error;
 * control flow (`ProcessCanceledException` when the read action restarts) is thrown again.
 *
 * ```xml
 * <editorNotificationProvider implementation="me.tbsten.katachi.intellij.ide.notification.KatachiEditorNotificationProvider"/>
 * ```
 */
internal class KatachiEditorNotificationProvider(
    private val portsOf: (Project) -> NotificationPorts,
) : EditorNotificationProvider, DumbAware {
    @Suppress("unused") // Called by the platform.
    constructor() : this(NotificationPorts::of)

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val path = sdkCall("find the path of ${file.name}") { file.fileSystem.getNioPath(file) }.getOrNull()
            ?: return null
        val decided = sdkCall("decide the katachi notification of $path") { decide(project, file, path) }.getOrNull()
            ?: return null
        // A panel the IDE fails to build is no panel, not an IDE error.
        return Function { editor ->
            sdkCall("build the katachi notification of $path") { katachiNotificationPanel(editor, decided.decision, decided.actionsFor(project, file, path)) }.getOrNull()
        }
    }

    /** `null`: no notification. Runs in the background under the read lock. */
    private fun decide(project: Project, file: VirtualFile, path: Path): Decided? {
        val ports = portsOf(project)
        val settings = ports.settings()
        if (!settings.notificationsEnabled) {
            // A panel turned off while open was not "shown" when its file closes.
            if (ports.existingService() != null) ports.memory().recordDecision(path, NotificationDecision.Hidden)
            return null
        }
        // Only a project with a definition module gets the service (decision 17).
        val service = ports.serviceForEntries() ?: return null
        val memory = ports.memory()
        memory.followGenerations(service)

        val index = service.placementIndex.value
        // Read actions restart: ensureLoaded loads once however often it is called.
        if (shouldEnsureLoaded(index, settings.loadWithoutUser)) service.ensureLoaded()
        val matches = (index as? PlacementIndexState.Ready)?.index?.matchesForFile(path).orEmpty()
        val ledgerEntry = service.ledger.entryOf(path)
        if (matches.isEmpty() && ledgerEntry == null) {
            memory.recordDecision(path, NotificationDecision.Hidden)
            return null
        }
        if (matches.isNotEmpty()) memory.followEdits(path)

        val input = NotificationInput(
            file = path,
            // Only read when it matters: without a match, the content decides nothing.
            content = if (matches.isEmpty()) FileContentState.HasContent else contentOf(file),
            availability = index.availability,
            matches = matches,
            settings = settings,
            memory = memory.current,
            ledgerEntry = ledgerEntry,
            commentable = CommentSyntax.forFileName(file.name) != null,
        )
        val decision = decideNotification(input)
        memory.recordDecision(path, decision)
        if (decision == NotificationDecision.Hidden) return null
        return Decided(decision, service, memory)
    }

    /** The unsaved text wins over the disk (decision 12); a file without a Document is empty only when it has no bytes. */
    private fun contentOf(file: VirtualFile): FileContentState {
        val documents = FileDocumentManager.getInstance()
        val text = (documents.getCachedDocument(file) ?: documents.getDocument(file))?.immutableCharSequence
        val empty = if (text != null) EmptyFileRule.isEmpty(text, file.name) else file.length == 0L
        return if (empty) FileContentState.Empty else FileContentState.HasContent
    }

    /** A decision to show, and what its clicks reach, fixed when it was made. */
    private class Decided(val decision: NotificationDecision, val service: KatachiProjectService, val memory: EditorNotificationMemoryService) {
        fun actionsFor(project: Project, file: VirtualFile, path: Path): NotificationPanelActions {
            // The first of the index order (decision 9); a file decides every capture (decision 21).
            val first: PlacementMatch? = when (decision) {
                is NotificationDecision.CreateFromTemplate -> decision.matches.firstOrNull()
                is NotificationDecision.ViewTemplate -> decision.matches.firstOrNull()
                else -> null
            }
            val effects = service.entryEffects
            return NotificationPanelActions(
                create = {
                    first?.let { match ->
                        val request = GenerateDialogRequest(EntryOrigin.EditorFile(path), match.id, match.decided)
                        sdkCall("open the generate dialog") { effects.openGenerateDialog(request) }
                    }
                },
                viewTemplate = { first?.let { match -> sdkCall("show the template in the tool window") { effects.revealTemplateInToolWindow(match.id) } } },
                openSettings = { sdkCall("open the katachi settings") { effects.openSettings() } },
                dismiss = {
                    memory.dismiss(path)
                    sdkCall("update the katachi notification of $path") { EditorNotifications.getInstance(project).updateNotifications(file) }
                },
            )
        }
    }
}
