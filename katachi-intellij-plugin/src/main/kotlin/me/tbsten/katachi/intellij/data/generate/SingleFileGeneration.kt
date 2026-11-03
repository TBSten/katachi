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

internal data class SingleFileGenerationRequest(
    val template: ModuleTemplate,
    val origin: EntryOrigin,
    val args: List<Pair<String, String>>,
    val target: Path?,
)

internal enum class TargetState {
    Absent,

    Empty,

    OwnProvisional,

    HasContent,

    Undecided,
}

internal sealed interface EntryGenerationRefusal {
    data class TargetHasContent(val target: Path) : EntryGenerationRefusal

    data class ModuleMissing(val directory: Path) : EntryGenerationRefusal
}

internal sealed interface EntryGenerationFailure {
    data class CatalogReloadFailed(val failure: LoadFailure) : EntryGenerationFailure

    data object TemplateGone : EntryGenerationFailure

    data class TargetMoved(val newTarget: Path?) : EntryGenerationFailure

    data class ChangedMeanwhile(val target: Path) : EntryGenerationFailure

    data class Katachi(val failure: GenerationFailure) : EntryGenerationFailure

    data class Ide(val action: String) : EntryGenerationFailure
}

internal sealed interface SingleFileGenerationResult {
    data class Generated(val files: List<Path>) : SingleFileGenerationResult

    data class Refused(val refusal: EntryGenerationRefusal) : SingleFileGenerationResult

    data class Failed(val failure: EntryGenerationFailure) : SingleFileGenerationResult
}

internal sealed interface GenerationCatalogReload {
    data class Reloaded(val snapshots: List<DescriptionSnapshot>, val index: TemplatePlacementIndex) : GenerationCatalogReload

    data class Failed(val failure: LoadFailure) : GenerationCatalogReload
}

internal interface GenerationCatalogPort {
    suspend fun reloadForGeneration(module: KatachiModule): GenerationCatalogReload

    fun registerOwnWrite(path: Path)
}

internal class SingleFileGeneration(
    private val effects: IdeEffects,
    private val runner: GradleTaskRunner,
    private val fileSystem: ProjectFileSystem,
    private val catalog: GenerationCatalogPort,
    private val ledger: EntryGenerationLedger,
) {
    private val provisionals = ConcurrentHashMap<Path, String>()

    // Claimed from the first check: a second entry must not slip in before the ledger says generating.
    private val running: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    suspend fun checkTarget(target: Path?): TargetState {
        if (target == null) return TargetState.Undecided
        val entry = ledger.entryOf(target)
        if (entry is LedgerEntry.Generating || target in running) return TargetState.HasContent
        val text = effects.currentText(target) ?: return TargetState.Absent
        val provisional = provisionals[target]
        if (entry is LedgerEntry.Failed && provisional != null && ProvisionalContent.isStillProvisional(text, provisional)) {
            return TargetState.OwnProvisional
        }
        return if (EmptyFileRule.isEmpty(text, target.fileName.toString())) TargetState.Empty else TargetState.HasContent
    }

    suspend fun run(request: SingleFileGenerationRequest): SingleFileGenerationResult {
        val target = request.target ?: return runUndecided(request)
        if (checkTarget(target) == TargetState.HasContent || !running.add(target)) {
            return SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(target))
        }
        try {
            return runOn(request, target)
        } finally {
            running.remove(target)
        }
    }

    private suspend fun runOn(request: SingleFileGenerationRequest, target: Path): SingleFileGenerationResult {
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

    // Fails if the user typed into the file or something wrote it meanwhile: Gradle must not overwrite it.
    private suspend fun isStillProvisional(target: Path, provisional: String): Boolean {
        val disk = fileSystem.readText(target) ?: return false
        if (!ProvisionalContent.isStillProvisional(disk, provisional)) return false
        if (effects.hasUnsavedChanges(target)) return false
        val shown = effects.currentText(target) ?: return false
        return ProvisionalContent.isStillProvisional(shown, provisional)
    }

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

    private suspend fun generate(template: ModuleTemplate, args: List<Pair<String, String>>, expected: Path?, onExisting: OnExistingChoice): GenerationItemResult {
        val session = GenerationSession(runner, fileSystem) { ConflictChoice.Stop }
        val item = GenerationItem(listOf(template.id), template.module, args, listOfNotNull(expected))
        return session.run(listOf(item), onExisting, object : GenerationListener {}).items.single().result
    }

    private fun reresolve(snapshots: List<DescriptionSnapshot>, request: SingleFileGenerationRequest): ModuleTemplate? =
        templatesOf(snapshots).firstOrNull { it.id == request.template.id }?.takeIf { it.template.isAvailable }

    private fun failed(written: Written, failure: EntryGenerationFailure): SingleFileGenerationResult {
        ledger.markFailed(written.target, written.request.template.id, written.command)
        return notified(failure)
    }

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

internal object EntryIdeAction {
    const val SAVE: String = "save the file"
    const val CREATE_DIRECTORIES: String = "create the directories"
    const val WRITE_PROVISIONAL: String = "write the provisional file"
}
