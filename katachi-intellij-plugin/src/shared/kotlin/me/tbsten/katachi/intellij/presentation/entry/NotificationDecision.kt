package me.tbsten.katachi.intellij.presentation.entry

import java.nio.file.Path

/** A file's text as the empty-file rule sees it (issue 13, decision 11): unsaved edits count. */
internal enum class FileContentState { Empty, HasContent }

/**
 * The settings the entries read (B2 stores them in `KatachiSettingsState`; issue 3 of the design,
 * decision 5). Pure, so the decision does not depend on the IDE's settings service.
 */
internal data class EntrySettings(
    /** "Show editor notifications" (all of them). */
    val notificationsEnabled: Boolean = true,
    val emptyFileNotification: Boolean = true,
    val contentFileNotification: Boolean = true,
    /** "Files with content: only the first time" (memory only, issue 15). */
    val contentFirstTimeOnly: Boolean = true,
    /** "Load the templates without the user asking (runs Gradle)" (decision 16). */
    val loadWithoutUser: Boolean = true,
)

/** Everything the editor notification of one file depends on, gathered in the background (E1). */
internal data class NotificationInput(
    val file: Path,
    val content: FileContentState,
    val availability: EntryAvailability,
    /** The index's matches for [file], in index order (definition order, then JSON order). */
    val matches: List<PlacementMatch>,
    val settings: EntrySettings,
    val memory: EditorNotificationMemory,
    val ledgerEntry: LedgerEntry?,
    /** Whether [file]'s extension has a comment syntax (A2's `CommentSyntax`), so the provisional file carries the notice itself. */
    val commentable: Boolean,
)

/**
 * The one notification a file gets, or none (design section 1, decisions 3, 8, 14). Several matching
 * templates or definitions still make one notification with the same text key and no role names.
 */
internal sealed interface NotificationDecision {
    data object Hidden : NotificationDecision

    /** Empty file with a match: [Create] ⚙ ×. [Create] opens the dialog on the first of [matches]. */
    data class CreateFromTemplate(val matches: List<PlacementMatch>) : NotificationDecision

    /** File with content and a match: [View template] ⚙ ×. [View template] reveals the first of [matches]. */
    data class ViewTemplate(val matches: List<PlacementMatch>) : NotificationDecision

    /** The provisional file of a running generation (decision 3). [command] only when the file cannot hold the notice. */
    data class Generating(val command: String?) : NotificationDecision

    /** A generation failed on a file that cannot hold a comment: the command to run by hand (decision 14). */
    data class FailedWithCommand(val command: String) : NotificationDecision
}

/**
 * Decides the editor notification of [input]'s file. Pure; E1 calls it in `collectNotificationData`.
 *
 * ```kotlin
 * decideNotification(input.copy(content = FileContentState.Empty)) // CreateFromTemplate(matches) when something matches
 * ```
 */
internal fun decideNotification(input: NotificationInput): NotificationDecision {
    val settings = input.settings
    if (!settings.notificationsEnabled) return NotificationDecision.Hidden
    // A file katachi just generated counts as closed (decision 8), and so does one closed with x.
    if (input.ledgerEntry is LedgerEntry.Succeeded || input.memory.isDismissed(input.file)) return NotificationDecision.Hidden

    when (val entry = input.ledgerEntry) {
        is LedgerEntry.Generating ->
            return NotificationDecision.Generating(command = entry.command.takeUnless { input.commentable })
        // A file that holds a comment goes back to the ordinary decision: empty, so "create" again (decision 3).
        is LedgerEntry.Failed ->
            if (!input.commentable) return NotificationDecision.FailedWithCommand(entry.command)
        is LedgerEntry.Succeeded, null -> Unit
    }

    if (input.availability != EntryAvailability.Ready || input.matches.isEmpty()) return NotificationDecision.Hidden
    return when (input.content) {
        FileContentState.Empty ->
            if (settings.emptyFileNotification) NotificationDecision.CreateFromTemplate(input.matches) else NotificationDecision.Hidden
        FileContentState.HasContent -> {
            val firstTimeLeft = !settings.contentFirstTimeOnly || !input.memory.hasShownContentNotice(input.file)
            if (settings.contentFileNotification && firstTimeLeft) NotificationDecision.ViewTemplate(input.matches) else NotificationDecision.Hidden
        }
    }
}
