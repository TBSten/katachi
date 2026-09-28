package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.EmptyFileRule
import me.tbsten.katachi.intellij.data.placement.ProvisionalContent
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.data.placement.generateCommandOf
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.templatesOf
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.entry.EntryGenerationLedger
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** One template to generate from the dialog of an entry: one click makes one file (issue 2). */
internal data class SingleFileGenerationRequest(
    val template: ModuleTemplate,
    val origin: EntryOrigin,
    /** Captures and parameters as `--arg name=value`, in field order. */
    val args: List<Pair<String, String>>,
    /**
     * The file katachi will write, absolute; `null` when a module-derived `<x>` keeps it unknown until
     * katachi runs: then no provisional file, `onExisting=fail`, and the file comes from the output (spike S1 §5).
     */
    val target: Path?,
)

/** What is at the target before anything is written. The dialog's notice and the pre-check use the same answer (issue 6). */
internal enum class TargetState {
    Absent,

    /** Empty by the empty-file rule (unsaved Document first): katachi will overwrite it. */
    Empty,

    /** Still exactly the provisional content this plugin wrote (a retry after a failure). */
    OwnProvisional,

    /** Has content: not generated, never overwritten (issue 6). */
    HasContent,

    /** The target is not known before katachi runs ([SingleFileGenerationRequest.target] is `null`). */
    Undecided,
}

/** Why a generation did not start; nothing was written and the dialog stays open with the reason (decision 2). */
internal sealed interface EntryGenerationRefusal {
    data class TargetHasContent(val target: Path) : EntryGenerationRefusal

    /** The module a module capture names does not exist; katachi does not create modules (spike S1 §3). */
    data class ModuleMissing(val directory: Path) : EntryGenerationRefusal
}

/** Why a generation failed after the provisional file was written: a balloon, and the notice stays in the file (decision 2). */
internal sealed interface EntryGenerationFailure {
    /** Re-reading the templates failed. */
    data class CatalogReloadFailed(val failure: LoadFailure) : EntryGenerationFailure

    /** The template is gone from the re-read templates. */
    data object TemplateGone : EntryGenerationFailure

    /** The re-read template writes elsewhere now. */
    data class TargetMoved(val newTarget: Path?) : EntryGenerationFailure

    /** The provisional file was changed (on disk or unsaved) before Gradle ran: not overwritten. */
    data class ChangedMeanwhile(val target: Path) : EntryGenerationFailure

    data class Katachi(val failure: GenerationFailure) : EntryGenerationFailure

    /** An IDE call (write action, editor) failed; [action] is what it was doing. */
    data class Ide(val action: String) : EntryGenerationFailure
}

internal sealed interface SingleFileGenerationResult {
    /** [files]: what katachi wrote, the target first. */
    data class Generated(val files: List<Path>) : SingleFileGenerationResult

    data class Refused(val refusal: EntryGenerationRefusal) : SingleFileGenerationResult

    data class Failed(val failure: EntryGenerationFailure) : SingleFileGenerationResult
}

/** What re-reading the templates for a generation gave. */
internal sealed interface GenerationCatalogReload {
    /** The new list, already published to the tool window and the index. */
    data class Reloaded(val snapshots: List<DescriptionSnapshot>, val index: TemplatePlacementIndex) : GenerationCatalogReload

    data class Failed(val failure: LoadFailure) : GenerationCatalogReload
}

/**
 * The shared template list as a generation needs it. `KatachiProjectService` implements it (C1)
 * through the one serial Gradle runner, so the reload never overlaps a tool window load.
 */
internal interface GenerationCatalogPort {
    /** Re-runs `katachiInternalTemplatesJson` of [module] and publishes the result (JSON → shared state → index). */
    suspend fun reloadForGeneration(module: KatachiModule): GenerationCatalogReload

    /** [path] is about to be written by this plugin: its VFS event is not a definition change (E-44). */
    fun registerOwnWrite(path: Path)
}

/**
 * Generates one file from an entry's dialog (issue 16, plan chapter 1 "生成の流れ", decisions 1, 2, 12, 13, 18):
 * check the target → create missing directories → write, open and save the provisional file (ledger:
 * generating) → re-read the templates and resolve the template and target again → check the file is
 * still the provisional content, on disk and in the Document → `katachiTemplate` with
 * `onExisting=overwrite` → reload → ledger: succeeded, or failed with the notice left in the file.
 *
 * IntelliJ-free: the IDE is [effects], Gradle is [runner] (the project's one serial runner).
 *
 * ```kotlin
 * when (generation.checkTarget(request.target)) { TargetState.HasContent -> showReason(); else -> generation.run(request) }
 * ```
 */
internal class SingleFileGeneration(
    private val effects: IdeEffects,
    private val runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
    private val catalog: GenerationCatalogPort,
    private val ledger: EntryGenerationLedger,
) {
    /** The provisional content written to each target, to tell "still ours" on a retry (decision 11). */
    private val provisionals = ConcurrentHashMap<Path, String>()

    /**
     * The state of [target], unsaved Documents first (decision 12). The dialog's notice (D1) and [run]
     * call the same check. A target a generation is running on counts as content: one generation per file.
     */
    suspend fun checkTarget(target: Path?): TargetState {
        if (target == null) return TargetState.Undecided
        val entry = ledger.entryOf(target)
        if (entry is LedgerEntry.Generating) return TargetState.HasContent
        val text = effects.currentText(target) ?: return TargetState.Absent
        val provisional = provisionals[target]
        if (entry is LedgerEntry.Failed && provisional != null && ProvisionalContent.isStillProvisional(text, provisional)) {
            return TargetState.OwnProvisional
        }
        return if (EmptyFileRule.isEmpty(text, target.fileName.toString())) TargetState.Empty else TargetState.HasContent
    }

    /** Runs the whole flow; cancelling the caller (the project closing) stops Gradle. */
    suspend fun run(request: SingleFileGenerationRequest): SingleFileGenerationResult {
        val target = request.target ?: return runUndecided(request)
        if (checkTarget(target) == TargetState.HasContent) return SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(target))
        missingModuleOf(request.template, target, fileSystem)?.let { return SingleFileGenerationResult.Refused(EntryGenerationRefusal.ModuleMissing(it)) }

        val args = entryArgsOf(request)
        val command = generateCommandOf(templateInvocationOf(request.template.module, args.withOverwrite()))
        val fileName = target.fileName.toString()
        val packageName = if (needsPackage(fileName)) effects.packageNameOf(target.parent) else null
        val provisional = ProvisionalContent.of(fileName, command, effects.provisionalNotice(request.template.template.title), packageName)

        // The directories it creates too: under a definition's src/ their creation is a VFS event of its own.
        missingDirectoriesOf(target.parent, fileSystem).plusElement(target).forEach(catalog::registerOwnWrite)
        // An open Document with unsaved (still empty) edits is saved first: writing under it would ask the user about a conflict (S2 (a3)).
        if (effects.hasUnsavedChanges(target) && !effects.saveDocument(target)) return failedBeforeWriting(EntryGenerationFailure.Ide(EntryIdeAction.SAVE))
        if (!effects.createDirectories(target.parent)) return failedBeforeWriting(EntryGenerationFailure.Ide(EntryIdeAction.CREATE_DIRECTORIES))
        if (!effects.writeProvisionalFile(target, provisional)) return failedBeforeWriting(EntryGenerationFailure.Ide(EntryIdeAction.WRITE_PROVISIONAL))
        provisionals[target] = provisional
        val written = Written(request, target, command, provisional)
        ledger.markGenerating(target, request.template.id, command)
        try {
            return afterProvisional(written, args)
        } catch (e: Throwable) {
            // The project is closing, or something unforeseen: the provisional content stays as the instruction,
            // and the file is not left "generating" for good (which would refuse every retry).
            ledger.markFailed(target, request.template.id, command)
            throw e
        }
    }

    /** What was written before Gradle runs. */
    private data class Written(val request: SingleFileGenerationRequest, val target: Path, val command: String, val provisional: String)

    private suspend fun afterProvisional(written: Written, args: List<Pair<String, String>>): SingleFileGenerationResult {
        val (request, target) = written
        val template = when (val reload = catalog.reloadForGeneration(request.template.module)) {
            is GenerationCatalogReload.Failed -> return failed(written, EntryGenerationFailure.CatalogReloadFailed(reload.failure))
            is GenerationCatalogReload.Reloaded -> reresolve(reload.snapshots, request) ?: return failed(written, EntryGenerationFailure.TemplateGone)
        }
        val newTarget = entryTargetOf(template, request, fileSystem)
        if (newTarget != target) return failed(written, EntryGenerationFailure.TargetMoved(newTarget))
        if (!isStillProvisional(target, written.provisional)) return failed(written, EntryGenerationFailure.ChangedMeanwhile(target))

        return when (val result = generate(template, args, target, OnExistingChoice.Overwrite)) {
            is GenerationItemResult.Generated -> {
                val files = (listOf(target) + result.files.map { it.path }).distinct()
                effects.refreshFiles(files)
                effects.reloadFromDisk(files)
                ledger.markSucceeded(target, request.template.id)
                provisionals.remove(target)
                SingleFileGenerationResult.Generated(files)
            }
            is GenerationItemResult.Failed -> failed(written, EntryGenerationFailure.Katachi(result.failure))
            // Not under overwrite; would it happen, the file is as the provisional content left it.
            else -> failed(written, EntryGenerationFailure.Katachi(GenerationFailure.Katachi(emptyList())))
        }
    }

    /**
     * Still exactly the provisional content, on disk and in the Document, and nothing unsaved (decision 12):
     * otherwise the user typed into it or something wrote it meanwhile, and Gradle must not overwrite it.
     */
    private suspend fun isStillProvisional(target: Path, provisional: String): Boolean {
        val disk = fileSystem.readText(target) ?: return false
        if (!ProvisionalContent.isStillProvisional(disk, provisional)) return false
        if (effects.hasUnsavedChanges(target)) return false
        val shown = effects.currentText(target) ?: return false
        return ProvisionalContent.isStillProvisional(shown, provisional)
    }

    /**
     * A target only katachi knows (a module-derived `<x>`, spike S1 §5): no provisional file, `onExisting=fail`,
     * and the files come from the output. They open whatever the "open after generation" setting says, as the
     * provisional file of [run] does (decision 13).
     */
    private suspend fun runUndecided(request: SingleFileGenerationRequest): SingleFileGenerationResult {
        val template = when (val reload = catalog.reloadForGeneration(request.template.module)) {
            is GenerationCatalogReload.Failed -> return notified(EntryGenerationFailure.CatalogReloadFailed(reload.failure))
            is GenerationCatalogReload.Reloaded -> reresolve(reload.snapshots, request) ?: return notified(EntryGenerationFailure.TemplateGone)
        }
        return when (val result = generate(template, entryArgsOf(request), expected = null, OnExistingChoice.Fail)) {
            is GenerationItemResult.Generated -> {
                val files = result.files.map { it.path }
                effects.refreshFiles(files)
                effects.openFiles(files)
                files.firstOrNull()?.let { ledger.markSucceeded(it, request.template.id) }
                SingleFileGenerationResult.Generated(files)
            }
            is GenerationItemResult.StoppedAtConflict -> SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(result.existing.first()))
            is GenerationItemResult.Failed -> notified(EntryGenerationFailure.Katachi(result.failure))
            else -> notified(EntryGenerationFailure.Katachi(GenerationFailure.Katachi(emptyList())))
        }
    }

    /** One `katachiTemplate` build of [template]; a conflict stops it (only `onExisting=fail` has one). */
    private suspend fun generate(template: ModuleTemplate, args: List<Pair<String, String>>, expected: Path?, onExisting: OnExistingChoice): GenerationItemResult {
        val session = GenerationSession(runner, fileSystem) { ConflictChoice.Stop }
        val item = GenerationItem(listOf(template.id), template.module, args, listOfNotNull(expected))
        return session.run(listOf(item), onExisting, object : GenerationListener {}).items.single().result
    }

    /** The request's template in the re-read list; `null` when it is gone or cannot be generated any more. */
    private fun reresolve(snapshots: List<DescriptionSnapshot>, request: SingleFileGenerationRequest): ModuleTemplate? =
        templatesOf(snapshots).firstOrNull { it.id == request.template.id }?.takeIf { it.template.isAvailable }

    /** After the provisional file: the ledger keeps "failed" (the notification offers the command, decision 14), and a balloon. */
    private fun failed(written: Written, failure: EntryGenerationFailure): SingleFileGenerationResult {
        ledger.markFailed(written.target, written.request.template.id, written.command)
        return notified(failure)
    }

    /** Before anything was written: only the balloon. */
    private fun failedBeforeWriting(failure: EntryGenerationFailure): SingleFileGenerationResult = notified(failure)

    private fun notified(failure: EntryGenerationFailure): SingleFileGenerationResult {
        effects.notifyEntryGenerationFailed(failure)
        return SingleFileGenerationResult.Failed(failure)
    }

    private fun needsPackage(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in PACKAGE_EXTENSIONS

    private companion object {
        val PACKAGE_EXTENSIONS = setOf("kt", "java")
    }
}

/** What the IDE was doing when a generation from an entry failed there: [EntryGenerationFailure.Ide.action]. */
internal object EntryIdeAction {
    const val SAVE: String = "save the file"
    const val CREATE_DIRECTORIES: String = "create the directories"
    const val WRITE_PROVISIONAL: String = "write the provisional file"
}
