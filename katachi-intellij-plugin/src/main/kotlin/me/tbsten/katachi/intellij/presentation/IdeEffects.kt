package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GenerationReport
import java.nio.file.Path

/** A file a template is expected to write, with the preview contents filled with the inputs. */
internal data class ExpectedContent(
    val path: Path,
    val fileName: String,
    /** `null` for a file only a branch adds, whose contents the JSON does not carry. */
    val content: String?,
)

/**
 * What the ViewModel asks of the IDE. Implemented over the IntelliJ API in `ide/`, faked in tests.
 *
 * Implementations drop the effect once the project is closing (E-49): no dialog, notification or
 * editor appears for a project that is gone.
 */
internal interface IdeEffects {
    /** Before any Gradle run, so it reads the definitions as edited (E-51). */
    suspend fun saveAllDocuments()

    /** A Local History label before generating (E-47); returns the label's name for the result footer. */
    suspend fun putLocalHistoryLabel(roleNames: List<String>): String

    /** Makes the VFS see files Gradle wrote behind its back; reloads open editors of overwritten ones. */
    suspend fun refreshFiles(paths: List<Path>)

    /** Opens [paths] in editors, focusing the first. Paths the VFS cannot find are skipped. */
    suspend fun openFiles(paths: List<Path>)

    /**
     * Shows the conflict dialog and waits for the answer (E-17). [expected] backs its
     * "diff (expected)" links: the existing file against the preview filled with the inputs.
     */
    suspend fun askConflict(question: ConflictQuestion, expected: List<ExpectedContent>): ConflictChoice

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

    /** Opens each file's expected contents read-only ("show contents (expected)"). */
    suspend fun showExpectedContents(files: List<ExpectedContent>)
}
