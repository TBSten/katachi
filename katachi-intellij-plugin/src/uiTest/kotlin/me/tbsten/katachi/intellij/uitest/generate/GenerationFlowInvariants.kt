package me.tbsten.katachi.intellij.uitest.generate

import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.FileContentState
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.presentation.entry.decideNotification
import me.tbsten.katachi.intellij.testing.expectedNotification
import me.tbsten.katachi.intellij.testing.notificationInput

/**
 * What must hold after every step of [GenerationFlowMachine]. (What one step must do is checked in the
 * machine; content is never overwritten, at the provisional write and at Gradle's start, in the fakes.)
 *
 * - one run per file at most, and it is exactly what the ledger calls "generating"
 * - the ledger's kind is the model's; the notification decided from the ledger is the one the spec
 *   says for the model's ledger (A3), for a file that can hold a comment and one that cannot
 * - a run waits at one gate at a time; no gate stays open for a file without a run
 * - Gradle and the template re-read start at most once per accepted run: requests do not overlap
 * - after a failure, or a cancel, the guidance (the provisional text) stays in the file, on disk and
 *   in the editor, until something else changes the file; after a success the file is the generated text
 */
internal fun checkFlowInvariants(machine: GenerationFlowMachine) {
    for (file in 0 until FILE_COUNT) {
        val target = machine.targets[file]
        val entry = machine.ledger.entryOf(target)
        val status = machine.status[file]
        val label = "file $file (${machine.status}, ledger $entry)"
        fun fail(message: String): Nothing = throw IllegalStateException("$label: $message")

        if ((machine.runs[file] != null) != (entry is LedgerEntry.Generating)) fail("a run and the 'generating' ledger entry must go together")
        if (kindOf(entry) != status) fail("the ledger says ${kindOf(entry)}, the model says $status")
        if (entry != null && entry.template != machine.controllerId()) fail("the ledger names another template")
        if (entry is LedgerEntry.Generating || entry is LedgerEntry.Failed) {
            val command = if (entry is LedgerEntry.Generating) entry.command else (entry as LedgerEntry.Failed).command
            val wanted = listOf("--arg template=api.Controller", "--arg onExisting=overwrite", "--arg name=${machine.names[file]}")
            if (!wanted.all(command::contains)) fail("the command to run by hand is '$command'")
        }

        val inCatalog = file in machine.catalog.gates
        val inGradle = file in machine.runner.gates
        if (inCatalog && inGradle) fail("waits at the re-read and at Gradle at once")
        if (machine.runs[file] == null && (inCatalog || inGradle)) fail("a gate is open without a run")
        if (machine.catalog.callsByFile[file] > machine.acceptedRuns[file] || machine.runner.startsByFile[file] > machine.acceptedRuns[file]) {
            fail("more re-reads (${machine.catalog.callsByFile[file]}) or Gradle requests (${machine.runner.startsByFile[file]}) than accepted runs (${machine.acceptedRuns[file]})")
        }

        checkNotification(machine, file, entry, ::fail)
        if (!machine.touched[file]) checkGuidance(machine, file, ::fail)
    }
}

private fun kindOf(entry: LedgerEntry?): FlowStatus = when (entry) {
    null -> FlowStatus.None
    is LedgerEntry.Generating -> FlowStatus.Generating
    is LedgerEntry.Failed -> FlowStatus.Failed
    is LedgerEntry.Succeeded -> FlowStatus.Succeeded
}

/** The notification from the real ledger equals the spec applied to the model's ledger (A3). */
private fun checkNotification(machine: GenerationFlowMachine, file: Int, entry: LedgerEntry?, fail: (String) -> Nothing) {
    val target = machine.targets[file]
    val text = machine.textOf(file)
    val content = if (text != null && isContent(text)) FileContentState.HasContent else FileContentState.Empty
    val modelLedger = when (machine.status[file]) {
        FlowStatus.None -> null
        FlowStatus.Generating -> LedgerEntry.Generating(machine.controllerId(), (entry as LedgerEntry.Generating).command)
        FlowStatus.Failed -> LedgerEntry.Failed(machine.controllerId(), (entry as LedgerEntry.Failed).command)
        FlowStatus.Succeeded -> LedgerEntry.Succeeded(machine.controllerId())
    }
    val commentable = machine.commentable[file]
    val real = decideNotification(notificationInput(target, content, EntryAvailability.Ready, ledgerEntry = entry, commentable = commentable))
    val wanted = expectedNotification(notificationInput(target, content, EntryAvailability.Ready, ledgerEntry = modelLedger, commentable = commentable))
    if (real != wanted) fail("the notification is $real, the spec says $wanted")
    when (machine.status[file]) {
        FlowStatus.Generating -> if (real !is NotificationDecision.Generating) fail("a running generation shows $real")
        FlowStatus.Succeeded -> if (real != NotificationDecision.Hidden) fail("a generated file shows $real")
        FlowStatus.Failed, FlowStatus.None -> Unit
    }
}

/** Nothing else touched the file since the run began: the provisional text (running, failed) or the generated text (succeeded) is in it. */
private fun checkGuidance(machine: GenerationFlowMachine, file: Int, fail: (String) -> Nothing) {
    val target = machine.targets[file]
    val expected = when (machine.status[file]) {
        FlowStatus.Generating, FlowStatus.Failed -> machine.spy.provisionals[target]
        FlowStatus.Succeeded -> machine.generated[file]
        FlowStatus.None -> return
    }
    val disk = machine.fileSystem.readText(target)
    val shown = machine.effects.documents[target]
    if (disk != expected || shown != expected || target in machine.effects.unsaved) {
        fail("the file must hold $expected on disk and in the editor and be saved; disk=$disk editor=$shown unsaved=${target in machine.effects.unsaved}")
    }
}
