package me.tbsten.katachi.intellij.uitest.pbt.entry

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.EntryGenerationLedger
import me.tbsten.katachi.intellij.presentation.entry.EntrySettings
import me.tbsten.katachi.intellij.presentation.entry.FileContentState
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.presentation.entry.NotificationInput
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.presentation.entry.decideNotification
import me.tbsten.katachi.intellij.testing.expectedNotification
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.placementMatch
import me.tbsten.katachi.intellij.testing.template
import me.tbsten.katachi.intellij.testing.underRoot
import java.nio.file.Path

/** One user or IDE action on the editor notifications of three files. */
internal sealed interface NotificationOp {
    data class Open(val file: Int) : NotificationOp
    data class Close(val file: Int) : NotificationOp
    data class Reopen(val file: Int) : NotificationOp
    data class SetContent(val file: Int, val content: FileContentState) : NotificationOp
    data class Dismiss(val file: Int) : NotificationOp
    data class Toggle(val setting: Int) : NotificationOp
    data class Availability(val value: EntryAvailability) : NotificationOp
    data class AddMatch(val file: Int) : NotificationOp
    data class RemoveMatch(val file: Int) : NotificationOp
    data class GenerationStarts(val file: Int) : NotificationOp
    data class GenerationSucceeds(val file: Int) : NotificationOp
    data class GenerationFails(val file: Int) : NotificationOp
    data object RecreateService : NotificationOp

    /** Whether the op can change only [file]'s notification, so the others must stay as they were. */
    val fileLocal: Int?
        get() = when (this) {
            is Open -> file
            is Close -> file
            is Reopen -> file
            is SetContent -> file
            is Dismiss -> file
            is AddMatch -> file
            is RemoveMatch -> file
            is GenerationStarts -> file
            is GenerationSucceeds -> file
            is GenerationFails -> file
            is Toggle, is Availability, RecreateService -> null
        }
}

/**
 * What the IDE keeps for three files (a Kotlin file, a .json file that cannot hold a comment, and
 * another Kotlin file), played two ways at once: the real [EditorNotificationMemory], [EntryGenerationLedger]
 * and [decideNotification] the way E1 wraps them, and a plain model read by [expectedNotification].
 *
 * A panel shows while a file is open and is recomputed after every action. "Shown once" is
 * recorded when the file is closed (a proposal for E1: recording at show time would hide the
 * panel on its next recomputation).
 */
internal class NotificationMachine {
    val files: List<Path> = listOf(underRoot("src/A.kt"), underRoot("config/B.json"), underRoot("src/C.kt"))
    private val commentable = listOf(true, false, true)

    // The real side.
    private var memory = EditorNotificationMemory.EMPTY
    private var ledger = EntryGenerationLedger()
    private val content = MutableList(3) { FileContentState.Empty }
    private val matches = MutableList(3) { listOf<PlacementMatch>() }
    private var settings = EntrySettings()
    private var availability = EntryAvailability.Ready
    private val open = MutableList(3) { false }
    private val panels = MutableList<NotificationDecision?>(3) { null }

    // The plain model, kept separately.
    private val modelDismissed = mutableSetOf<Int>()
    private val modelShown = mutableSetOf<Int>()
    private val modelLedger = mutableMapOf<Int, LedgerEntry>()
    private val modelPanels = MutableList<NotificationDecision?>(3) { null }

    private var matchCounter = 0
    val log = mutableListOf<String>()

    private fun input(i: Int): NotificationInput =
        NotificationInput(files[i], content[i], availability, matches[i], settings, memory, ledger.entryOf(files[i]), commentable[i])

    private fun modelDecision(i: Int): NotificationDecision =
        expectedNotification(
            input(i).copy(memory = EditorNotificationMemory.EMPTY, ledgerEntry = modelLedger[i]),
            dismissed = i in modelDismissed,
            contentShownBefore = i in modelShown,
        )

    private fun template(i: Int): TemplateId = TemplateId(module().id, "role$i")

    fun apply(op: NotificationOp) {
        val before = panels.toList()
        log += op.toString()
        when (op) {
            is NotificationOp.Open -> if (!open[op.file]) open[op.file] = true
            is NotificationOp.Close -> close(op.file)
            is NotificationOp.Reopen -> { close(op.file); open[op.file] = true }
            is NotificationOp.SetContent -> content[op.file] = op.content
            is NotificationOp.Dismiss -> if (open[op.file] && panels[op.file] != NotificationDecision.Hidden && panels[op.file] != null) {
                memory = memory.dismiss(files[op.file])
                modelDismissed += op.file
            }
            is NotificationOp.Toggle -> settings = settings.toggled(op.setting)
            is NotificationOp.Availability -> availability = op.value
            is NotificationOp.AddMatch -> matches[op.file] = matches[op.file] + placementMatch(template("role${matchCounter++}"))
            is NotificationOp.RemoveMatch -> matches[op.file] = matches[op.file].dropLast(1)
            is NotificationOp.GenerationStarts -> {
                content[op.file] = FileContentState.Empty
                ledger.markGenerating(files[op.file], template(op.file), "run ${op.file}")
                modelLedger[op.file] = LedgerEntry.Generating(template(op.file), "run ${op.file}")
            }
            is NotificationOp.GenerationSucceeds -> {
                content[op.file] = FileContentState.HasContent
                ledger.markSucceeded(files[op.file], template(op.file))
                modelLedger[op.file] = LedgerEntry.Succeeded(template(op.file))
            }
            is NotificationOp.GenerationFails -> {
                ledger.markFailed(files[op.file], template(op.file), "run ${op.file}")
                modelLedger[op.file] = LedgerEntry.Failed(template(op.file), "run ${op.file}")
            }
            NotificationOp.RecreateService -> {
                memory = EditorNotificationMemory.EMPTY
                ledger = EntryGenerationLedger()
                modelDismissed.clear()
                modelShown.clear()
                modelLedger.clear()
            }
        }
        refresh()
        check(op.fileLocal == null || files.indices.all { it == op.fileLocal || before[it] == panels[it] }) {
            "another file's panel changed after $op: $before -> $panels"
        }
        check(files.indices.all { panels[it] == modelPanels[it] }) { "panel differs from the model after $op: $panels vs $modelPanels" }
    }

    private fun close(i: Int) {
        if (!open[i]) return
        if (panels[i] is NotificationDecision.ViewTemplate) {
            memory = memory.markContentNoticeShown(files[i])
        }
        if (modelPanels[i] is NotificationDecision.ViewTemplate) modelShown += i
        open[i] = false
    }

    private fun refresh() {
        for (i in files.indices) {
            panels[i] = if (open[i]) decideNotification(input(i)) else null
            modelPanels[i] = if (open[i]) modelDecision(i) else null
        }
    }

    private fun EntrySettings.toggled(index: Int): EntrySettings = when (index) {
        0 -> copy(notificationsEnabled = !notificationsEnabled)
        1 -> copy(emptyFileNotification = !emptyFileNotification)
        2 -> copy(contentFileNotification = !contentFileNotification)
        else -> copy(contentFirstTimeOnly = !contentFirstTimeOnly)
    }
}
