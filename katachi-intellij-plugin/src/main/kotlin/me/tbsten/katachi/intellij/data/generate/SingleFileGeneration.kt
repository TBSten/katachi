package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.presentation.entry.EntryGenerationLedger
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import java.nio.file.Path

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
    /** The state of [target], unsaved Documents first. The dialog's notice (D1) and [run] call the same check. */
    suspend fun checkTarget(target: Path?): TargetState {
        // TODO(E3): A2's EmptyFileRule over effects.currentText, and the provisional content check.
        return TargetState.Absent
    }

    /** Runs the whole flow; cancelling the caller stops Gradle. */
    suspend fun run(request: SingleFileGenerationRequest): SingleFileGenerationResult {
        // TODO(E3)
        return SingleFileGenerationResult.Generated(emptyList())
    }
}
