package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.generate.EntryGenerationFailure
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GenerationReport
import java.nio.file.Path

/**
 * What the ViewModel asks of the IDE. Implemented over the IntelliJ API in `ide/`, faked in tests.
 *
 * Implementations drop the effect once the project is closing (E-49): no dialog, notification or
 * editor appears for a project that is gone.
 */
internal interface IdeEffects {
    /** Before any Gradle run, so it reads the definitions as edited (E-51). */
    suspend fun saveAllDocuments()

    /**
     * A Local History label before generating (E-47); returns the label's name for the result
     * footer, or `null` when the IDE could not put it, so that the footer names no label to undo to.
     */
    suspend fun putLocalHistoryLabel(titles: List<String>): String?

    /** Makes the VFS see files Gradle wrote behind its back; reloads open editors of overwritten ones. */
    suspend fun refreshFiles(paths: List<Path>)

    /**
     * Opens [paths] in editors, focusing the first, and returns the ones that did open: paths the
     * VFS cannot find, or that the IDE failed to open, are left out.
     */
    suspend fun openFiles(paths: List<Path>): List<Path>

    /** Shows the conflict dialog with the files katachi reported existing, and waits for the answer (E-17). */
    suspend fun askConflict(question: ConflictQuestion): ConflictChoice

    fun openAfterGeneration(): OpenAfterGeneration

    /** A balloon when the tool window is hidden (E-50); nothing when it is visible. */
    fun notifyGenerationFinished(report: GenerationReport)

    /** A balloon when the tool window is hidden (E-50); nothing when it is visible. */
    fun notifyLoadFailed()

    /** Brings the tool window that shows the latest Gradle run to the front. */
    fun showLog()

    fun syncGradle()

    fun openDocs(page: DocsPage)

    fun copyToClipboard(text: String)

    // Generating one file from the editor notification or the New menu (SingleFileGeneration, E3).
    // Defaults do nothing so that the tool window's fakes need not know them. IdeEffectsImpl overrides
    // every one, in a write action where it writes, through sdkCall.

    /** Creates [directory] and its missing parents; `false` when the IDE could not. */
    suspend fun createDirectories(directory: Path): Boolean = false

    /**
     * Writes [text] to [path] (creating or replacing the file), saves it and opens it in an editor
     * (issue 16: the provisional file); `false` when it was not written.
     */
    suspend fun writeProvisionalFile(path: Path, text: String): Boolean = false

    /** The text the user sees: the open Document's, unsaved edits included, else the disk's; `null` when there is no file. */
    suspend fun currentText(path: Path): CharSequence? = null

    /** Whether [path]'s Document has edits not saved to disk. */
    suspend fun hasUnsavedChanges(path: Path): Boolean = false

    /** Saves [path]'s Document; `false` when it could not. */
    suspend fun saveDocument(path: Path): Boolean = false

    /**
     * The package of [directory] without PSI: the package of its nearest existing ancestor in a
     * source root plus the remaining segments; `null` outside source roots.
     */
    suspend fun packageNameOf(directory: Path): String? = null

    /** Reloads the open Documents of [paths] from disk after Gradle overwrote them. */
    suspend fun reloadFromDisk(paths: List<Path>) = Unit

    /** A balloon: why a generation from an entry failed (decision 2). */
    fun notifyEntryGenerationFailed(failure: EntryGenerationFailure) = Unit

    /** A balloon: why a generation from an entry did not start once its dialog had closed (issue 6). */
    fun notifyEntryGenerationRefused(refusal: EntryGenerationRefusal) = Unit

    /**
     * The notice the provisional file carries above the command, in the IDE's language: that katachi is
     * generating it from [templateTitle], and what to run if it stays so. May span lines.
     */
    fun provisionalNotice(templateTitle: String): String = "katachi: generating this file from the template $templateTitle\nIf it stays like this, run:"
}
