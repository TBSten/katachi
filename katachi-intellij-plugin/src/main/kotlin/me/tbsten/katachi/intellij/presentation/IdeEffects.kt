package me.tbsten.katachi.intellij.presentation

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

    /** A Local History label before generating (E-47); returns the label's name for the result footer. */
    suspend fun putLocalHistoryLabel(roleNames: List<String>): String

    /** Makes the VFS see files Gradle wrote behind its back; reloads open editors of overwritten ones. */
    suspend fun refreshFiles(paths: List<Path>)

    /** Opens [paths] in editors, focusing the first. Paths the VFS cannot find are skipped. */
    suspend fun openFiles(paths: List<Path>)

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
}
