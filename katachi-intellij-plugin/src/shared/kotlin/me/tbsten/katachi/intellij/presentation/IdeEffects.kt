package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.generate.EntryGenerationFailure
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.KatachiModule
import java.nio.file.Path

// Implementations drop the effect once the project is closing: no dialog, notification or editor appears for a project that is gone.
internal interface IdeEffects {
    suspend fun saveAllDocuments()

    suspend fun putLocalHistoryLabel(titles: List<String>): String?

    /** A Local History label named [name] as it is; `null` when it could not be put. */
    suspend fun putLocalHistoryLabelNamed(name: String): String? = null

    suspend fun refreshFiles(paths: List<Path>)

    suspend fun openFiles(paths: List<Path>): List<Path>

    suspend fun askConflict(question: ConflictQuestion): ConflictChoice

    fun openAfterGeneration(): OpenAfterGeneration

    fun notifyGenerationFinished(report: GenerationReport)

    fun notifyLoadFailed()

    fun showLog()

    fun syncGradle()

    fun openDocs(page: DocsPage)

    fun copyToClipboard(text: String)

    suspend fun openDeclaration(module: KatachiModule, roleName: String, site: DeclarationSiteRef?) = Unit

    // Generating one file from the editor notification or the New menu (SingleFileGeneration, E3).
    // Defaults do nothing so that the tool window's fakes need not know them. IdeEffectsImpl overrides
    // every one, in a write action where it writes, through sdkCall.

    suspend fun createDirectories(directory: Path): Boolean = false

    suspend fun writeProvisionalFile(path: Path, text: String): Boolean = false

    suspend fun currentText(path: Path): CharSequence? = null

    suspend fun hasUnsavedChanges(path: Path): Boolean = false

    suspend fun saveDocument(path: Path): Boolean = false

    suspend fun packageNameOf(directory: Path): String? = null

    suspend fun reloadFromDisk(paths: List<Path>) = Unit

    fun notifyEntryGenerationFailed(failure: EntryGenerationFailure) = Unit

    fun notifyEntryGenerationRefused(refusal: EntryGenerationRefusal) = Unit

    fun provisionalNotice(templateTitle: String): String = "katachi: generating this file from the template $templateTitle\nIf it stays like this, run:"
}
