package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.GeneratedFile
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.WrittenKind
import java.nio.file.Path

/** What replaces a row's form after a generation: the files written, or why not (spec 03). */
internal fun rowResultUiOf(
    row: ModuleTemplate,
    result: GenerationItemResult,
    finished: GenerationState.Finished,
    view: ViewState,
    strings: KatachiStrings,
): RowResultUi {
    val opened = finished.openedFiles.toSet()
    fun existing(paths: List<Path>, badge: String) = paths.map { ResultFileUi(it.fileName.toString(), badge, null, KatachiIntent.OpenFile(it)) }
    return when (result) {
        is GenerationItemResult.Generated -> RowResultUi(
            files = result.files.map { writtenFileUiOf(it, it.path in opened, strings) } +
                existing(result.existingExpected, strings.alreadyExists),
            message = listOfNotNull(
                strings.writesUnknown.takeIf { result.writesUnknown },
                strings.outputIncomplete.takeIf { result.outputIncomplete && !result.writesUnknown },
            ),
            isError = false,
            actions = emptyList(),
            details = null,
        )
        is GenerationItemResult.Skipped -> RowResultUi(existing(result.existing, strings.fileSkipped), listOf(strings.rowSkipped), false, emptyList(), null)
        is GenerationItemResult.StoppedAtConflict ->
            RowResultUi(existing(result.existing, strings.alreadyExists), listOf(strings.rowStopped), false, emptyList(), null)
        is GenerationItemResult.Interrupted ->
            RowResultUi(existing(result.existingExpected, strings.alreadyExists), listOf(strings.rowInterrupted), true, emptyList(), null)
        GenerationItemResult.NotRun -> RowResultUi(emptyList(), listOf(strings.rowNotRun), false, emptyList(), null)
        is GenerationItemResult.Failed -> {
            val key = DetailsKey.ResultRow(row.id)
            RowResultUi(
                files = emptyList(),
                message = listOf(failureMessageOf(result.failure, strings)),
                isError = true,
                actions = listOf(ActionUi(strings.showLog, KatachiIntent.ShowLog)),
                details = result.output.takeIf { it.isNotEmpty() }?.let {
                    DetailsUi(strings.details, it, key in view.openDetails, KatachiIntent.ToggleDetails(key))
                },
            )
        }
    }
}

private fun writtenFileUiOf(file: GeneratedFile, opened: Boolean, strings: KatachiStrings) = ResultFileUi(
    name = file.path.fileName.toString(),
    badge = if (file.kind == WrittenKind.New) strings.fileNew else strings.fileOverwritten,
    note = strings.openedMarker.takeIf { opened },
    open = KatachiIntent.OpenFile(file.path),
)

/** The one line under a failed row. Which field caused it is not known, so no field is marked (E-20). */
private fun failureMessageOf(failure: GenerationFailure, strings: KatachiStrings): String = when (failure) {
    is GenerationFailure.Katachi -> failure.body.firstOrNull { it.isNotBlank() } ?: strings.gradleFailed
    is GenerationFailure.NotReached -> when (val gradle = failure.failure) {
        is GradleFailure.CompilationFailed -> strings.definitionCompileFailed
        is GradleFailure.TaskNotFound -> strings.generationTaskNotFound
        is GradleFailure.ArchitectureNotSet -> strings.architectureNotSet
        is GradleFailure.ProcessorRejected -> gradle.details.firstOrNull { it.isNotBlank() } ?: strings.gradleFailed
        is GradleFailure.Other -> strings.gradleFailed
    }
}

/** The footer under the list, by what the list is doing. */
internal fun footerUiOf(state: KatachiScreenState, strings: KatachiStrings): FooterUi = when (val generation = state.generation) {
    is GenerationState.Running -> FooterUi.Generating(
        label = when {
            generation.conflict != null -> strings.waitingForConflict
            generation.waitingForLoad -> strings.waitingForLoad
            else -> strings.generatingProgress(generation.position, generation.rows.size)
        },
        progress = if (generation.waitingForLoad || generation.rows.isEmpty()) {
            null
        } else {
            generation.rows.count { generation.statuses[it] is GenerationRowStatus.Finished }.toFloat() / generation.rows.size
        },
        cancel = ActionUi(strings.cancel, KatachiIntent.CancelGeneration),
    )
    is GenerationState.Finished -> {
        val report = generation.report
        FooterUi.Result(
            summary = when {
                report.isComplete -> strings.resultAll(report.succeededCount)
                // An interrupted run may have written, so only a report without either says "nothing".
                report.succeededCount == 0 && report.items.none { it.result is GenerationItemResult.Interrupted } ->
                    strings.resultNothingWritten(report.templateIds.size)
                else -> strings.resultPartial(report.succeededCount, report.templateIds.size)
            },
            isComplete = report.isComplete,
            undo = generation.localHistoryLabel?.let(strings::undoHint),
            actions = listOfNotNull(
                ActionUi(strings.retryRemaining, KatachiIntent.RetryRemaining).takeIf { report.retryTargets.isNotEmpty() },
                ActionUi(strings.continueGenerating, KatachiIntent.ContinueGenerating),
                ActionUi(strings.uncheckAll, KatachiIntent.UncheckAll),
            ),
        )
    }
    null -> formFooterOf(state, strings)
}

private fun formFooterOf(state: KatachiScreenState, strings: KatachiStrings): FooterUi.Form {
    val blocker = state.generateBlocker
    val reason = blocker?.let { reasonOf(it, state, strings) }
    val choices = OnExistingChoice.entries
    return FooterUi.Form(
        // "Check a template" is what the empty list says anyway; the line stays for real problems.
        reason = reason.takeIf { blocker != GenerateBlocker.NothingSelected },
        reasonTarget = (blocker as? GenerateBlocker.InvalidField)?.let { FieldId(it.templateId, it.parameterName) },
        onExistingLabel = strings.onExisting,
        onExistingOptions = choices.map { choice ->
            val label = when (choice) {
                OnExistingChoice.Fail -> strings.onExistingAsk
                OnExistingChoice.Skip -> strings.onExistingSkip
                OnExistingChoice.Overwrite -> strings.onExistingOverwrite
            }
            ActionUi(label, KatachiIntent.SetOnExisting(choice))
        },
        onExistingIndex = choices.indexOf(state.form.onExisting),
        overwriteWarning = strings.overwriteWarning.takeIf { state.form.onExisting == OnExistingChoice.Overwrite },
        generate = ActionUi(strings.generate, KatachiIntent.Generate),
        generateEnabled = blocker == null,
        generateTooltip = reason,
    )
}

private fun reasonOf(blocker: GenerateBlocker, state: KatachiScreenState, strings: KatachiStrings): String {
    fun role(id: TemplateId) =
        state.rows.firstOrNull { it.id == id }?.template?.title ?: id.template.substringAfterLast('.')
    return when (blocker) {
        GenerateBlocker.NothingSelected -> strings.nothingSelected
        is GenerateBlocker.Unavailable -> strings.reasonUnavailable(role(blocker.templateId))
        is GenerateBlocker.InvalidField -> when (blocker.error) {
            FieldError.Required -> strings.reasonRequired(role(blocker.templateId), blocker.parameterName)
            is FieldError.NotAnInt -> strings.reasonNotAnInt(role(blocker.templateId), blocker.parameterName)
            is FieldError.NotAcceptedValue -> strings.reasonNotAccepted(role(blocker.templateId), blocker.parameterName)
            is FieldError.InvalidCapture -> strings.reasonInvalidCapture(role(blocker.templateId), blocker.parameterName)
        }
        is GenerateBlocker.UnresolvedPath -> strings.reasonUnresolved(role(blocker.templateId), blocker.fileName)
        GenerateBlocker.Generating -> strings.reasonGenerating
        GenerateBlocker.InitialLoading -> strings.reasonLoading
    }
}
