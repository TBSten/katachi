package me.tbsten.katachi.intellij.presentation.entry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.tbsten.katachi.intellij.model.TemplateId
import java.nio.file.Path

/** Where a generation started from an entry (notification, New menu) stands for one target file. */
internal sealed interface LedgerEntry {
    val template: TemplateId

    /** The provisional file is written and Gradle has not finished (decision 3: the "generating" notice). */
    data class Generating(override val template: TemplateId, val command: String) : LedgerEntry

    /**
     * The generation failed after the provisional file was written; [command] is what to run by hand,
     * which the notification shows for an extension that cannot hold a comment (decision 14).
     */
    data class Failed(override val template: TemplateId, val command: String) : LedgerEntry

    /** katachi wrote the file in this IDE session: no notification, as if closed with × (decision 8). */
    data class Succeeded(override val template: TemplateId) : LedgerEntry
}

/**
 * The generations started from the editor notification and the New menu, by target file (absolute).
 * Owned by `KatachiProjectService` (one per project, memory only); written by `SingleFileGeneration`
 * (E3), read by the notification (E1) and its decision (A3).
 *
 * Thread-safe: generation writes from a coroutine, the notification reads in the background under
 * a read lock. [entries] lets E1 recompute the notification of a file whose entry changed.
 *
 * ```kotlin
 * ledger.markGenerating(file, template, command)
 * ledger.entryOf(file) // LedgerEntry.Generating
 * ```
 */
internal class EntryGenerationLedger {
    private val mutableEntries = MutableStateFlow<Map<Path, LedgerEntry>>(emptyMap())

    val entries: StateFlow<Map<Path, LedgerEntry>> = mutableEntries.asStateFlow()

    fun entryOf(file: Path): LedgerEntry? = mutableEntries.value[file]

    fun markGenerating(file: Path, template: TemplateId, command: String) = put(file, LedgerEntry.Generating(template, command))

    fun markFailed(file: Path, template: TemplateId, command: String) = put(file, LedgerEntry.Failed(template, command))

    fun markSucceeded(file: Path, template: TemplateId) = put(file, LedgerEntry.Succeeded(template))

    /** Forgets [file], as when the user deletes it or generates it again. */
    // A set, not the path itself: a Path is an Iterable<Path>, so `map - path` would remove its name segments.
    fun clear(file: Path) = mutableEntries.update { it - setOf(file) }

    private fun put(file: Path, entry: LedgerEntry) = mutableEntries.update { it + (file to entry) }
}
