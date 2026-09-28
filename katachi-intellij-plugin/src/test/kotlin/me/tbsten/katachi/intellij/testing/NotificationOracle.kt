package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.FileContentState
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.presentation.entry.NotificationInput

/**
 * The editor notification written out as the design's rules, one line each, over plain values (no
 * memory or ledger classes), so the table and sequence tests compare `decideNotification` with a
 * second reading of the same spec. Platform-free: compiled into uiTest too.
 */
internal fun expectedNotification(
    input: NotificationInput,
    dismissed: Boolean = input.memory.isDismissed(input.file),
    contentShownBefore: Boolean = input.memory.hasShownContentNotice(input.file),
): NotificationDecision {
    val s = input.settings
    val ledger = input.ledgerEntry
    val usable = input.availability == EntryAvailability.Ready && input.matches.isNotEmpty()
    return when {
        !s.notificationsEnabled -> NotificationDecision.Hidden
        dismissed -> NotificationDecision.Hidden
        ledger is LedgerEntry.Succeeded -> NotificationDecision.Hidden
        ledger is LedgerEntry.Generating -> NotificationDecision.Generating(if (input.commentable) null else ledger.command)
        ledger is LedgerEntry.Failed && !input.commentable -> NotificationDecision.FailedWithCommand(ledger.command)
        !usable -> NotificationDecision.Hidden
        input.content == FileContentState.Empty && s.emptyFileNotification -> NotificationDecision.CreateFromTemplate(input.matches)
        input.content == FileContentState.HasContent && s.contentFileNotification && !(s.contentFirstTimeOnly && contentShownBefore) ->
            NotificationDecision.ViewTemplate(input.matches)
        else -> NotificationDecision.Hidden
    }
}
