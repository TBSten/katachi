package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateUnavailability

/**
 * The list's entries: module bands (two or more modules only, E-05), a group header before each
 * group's rows, and the rows the search leaves (E-16). The rows come in [templatesOf]'s order,
 * which keeps each group together, so a header shows once; root roles come first, without one.
 * A nested group's header is its whole path, `domain › model`, not indented (spec 02 "見出し").
 */
internal fun listItemsOf(state: KatachiScreenState, strings: KatachiStrings): List<ListItemUi> {
    val hits = searchTemplates(state.rows, state.searchQuery, state.form)
    val modules = state.snapshots.map { it.module }.distinctBy { it.id }
    val showModules = modules.size >= 2
    val showRoots = modules.map { it.linkedRootPath }.distinct().size >= 2
    val searching = state.searchQuery.isNotBlank()
    return buildList {
        for (module in modules) {
            val moduleHits = hits.filter { it.row.module.id == module.id }
            if (showModules) {
                if (searching && moduleHits.isEmpty()) continue
                val all = state.rows.filter { it.module.id == module.id }
                add(
                    ListItemUi.ModuleHeader(
                        moduleId = module.id,
                        title = moduleTitleOf(module, showRoots),
                        counter = "${all.count { state.form.isSelected(it.id) }}/${all.size}",
                        isCollapsed = module.id in state.view.collapsedModules,
                    ),
                )
                if (module.id in state.view.collapsedModules) continue
            }
            var group: String? = null
            for (hit in moduleHits) {
                val groupPath = hit.row.template.groupPath
                if (groupPath != group && groupPath.isNotEmpty()) {
                    add(ListItemUi.GroupHeader("group:${module.linkedRootPath}:${module.gradlePath}:$groupPath", groupTitleOf(groupPath)))
                }
                group = groupPath
                add(ListItemUi.Row(rowUiOf(hit, state, strings)))
            }
        }
    }
}

/** `domain/model` as `domain › model`: where a nested group sits, without indenting it. */
internal fun groupTitleOf(groupPath: String): String = groupPath.split('/').joinToString(" › ")

private fun moduleTitleOf(module: KatachiModule, showRoot: Boolean): String =
    if (showRoot) "${module.rootName} › ${module.gradlePath}" else module.gradlePath

private fun rowUiOf(hit: SearchHit, state: KatachiScreenState, strings: KatachiStrings): TemplateRowUi {
    val row = hit.row
    val generation = state.generation
    val base = baseRowOf(hit, state, strings)
    return when (generation) {
        null -> base
        is GenerationState.Running -> if (row.id in generation.rows) runningRowOf(base, row, generation, state, strings) else base.frozen()
        is GenerationState.Finished -> {
            val item = generation.report.items.firstOrNull { it.templateId == row.id }
            if (item == null) base.frozen() else resultRowOf(base, row, item.result, generation, state, strings)
        }
    }
}

/** A row outside the running or finished generation: it stays, but cannot be checked. */
private fun TemplateRowUi.frozen(): TemplateRowUi =
    copy(lead = RowLeadUi.Check(checked = false, enabled = false), isExpanded = null, body = null, note = null, filled = null)

/** The row as it looks while the list is editable. */
private fun baseRowOf(hit: SearchHit, state: KatachiScreenState, strings: KatachiStrings): TemplateRowUi {
    val row = hit.row
    val template = row.template
    val detail = template.detail
    val inputs = state.form.inputsOf(row.id)
    val checked = state.form.isSelected(row.id)
    val unavailability = template.unavailability
    val hasUnresolved = detail != null && expectedFilesOf(detail, inputs).any { it.location is ExpectedLocation.Unresolved }
    val formVisible = checked && detail != null && unavailability == null &&
        (!hit.isOutsideSearch || row.id in state.view.revealedRows)
    val expanded = formVisible && row.id in state.form.expanded
    val cause = state.view.causes[row.id]
    return TemplateRowUi(
        id = row.id,
        name = template.simpleName,
        title = template.summary.title,
        tooltip = template.summary.summary,
        lead = RowLeadUi.Check(checked = checked, enabled = unavailability == null),
        marker = when {
            unavailability != null -> RowMarker.Blocked
            hasUnresolved -> RowMarker.Warning
            else -> RowMarker.None
        },
        markerTooltip = when (unavailability) {
            TemplateUnavailability.PreviewFailed -> strings.previewFailed
            is TemplateUnavailability.UnknownParameterKind -> strings.unknownKinds(unavailability.kindNames)
            null -> strings.unresolvedTarget.takeIf { hasUnresolved }
        },
        trailing = strings.fileCount(rowFileCountOf(row, inputs)),
        isFaint = unavailability != null || hit.isOutsideSearch,
        note = strings.outsideSearch.takeIf { hit.isOutsideSearch },
        // Outside the search the "outside search, selected" note needs the room more.
        filled = filledNoteOf(row, state, strings).takeIf { checked && !expanded && detail != null && !hit.isOutsideSearch },
        isExpanded = expanded.takeIf { formVisible },
        menu = listOfNotNull(
            detail?.let { ActionUi(strings.copyCommand, KatachiIntent.CopyCommand(row.id)) },
            detail?.let { ActionUi(strings.showContents, KatachiIntent.ShowExpectedContents(row.id)) },
            ActionUi(strings.showCause, KatachiIntent.ShowCause(row.id)).takeIf { unavailability == TemplateUnavailability.PreviewFailed },
        ),
        body = when {
            cause is CauseState.Loading -> RowBodyUi.Cause(listOf(strings.causeLoading))
            cause is CauseState.Loaded -> RowBodyUi.Cause(cause.lines)
            expanded -> RowBodyUi.Form(formUiOf(row, detail, state, strings))
            else -> null
        },
    )
}

/** The row's own "N files": the preview's count moved by the branches the inputs take (E-09). */
private fun rowFileCountOf(row: ModuleTemplate, inputs: Map<String, String>): Int? {
    if (row.template.summary.fileCount == null) return null
    return expectedFileCountOf(listOf(fileCountSourceOf(row.template, inputs))).total
}

/** "filled 2/3" of a folded form: shown fields holding a value (Booleans always do). */
private fun filledNoteOf(row: ModuleTemplate, state: KatachiScreenState, strings: KatachiStrings): String {
    val detail = row.template.detail ?: return ""
    val inputs = state.form.inputsOf(row.id)
    val shown = shownParametersOf(detail, inputs)
    val filled = shown.count { it is ParameterModel.BooleanParam || !inputs[it.name].isNullOrBlank() }
    return strings.filledCount(filled, shown.size)
}

private fun runningRowOf(
    base: TemplateRowUi,
    row: ModuleTemplate,
    running: GenerationState.Running,
    state: KatachiScreenState,
    strings: KatachiStrings,
): TemplateRowUi {
    val status = running.statuses[row.id] ?: GenerationRowStatus.Waiting
    val (lead, trailing) = when (status) {
        GenerationRowStatus.Waiting -> RowStatus.Waiting to strings.rowWaiting
        is GenerationRowStatus.Running -> RowStatus.Running to strings.rowRunning(status.taskPath)
        GenerationRowStatus.AwaitingConflict -> RowStatus.AwaitingConflict to strings.rowAwaitingConflict
        is GenerationRowStatus.Finished -> resultStatusOf(status.result) to resultTrailingOf(status.result, strings)
    }
    val body = if (status is GenerationRowStatus.Running) {
        RowBodyUi.Running(
            taskLine = strings.runningTask(status.taskPath ?: row.module.taskPath(KatachiModule.TEMPLATE_TASK)),
            argsLine = argsSummaryOf(row, state, strings),
        )
    } else {
        null
    }
    return base.copy(lead = RowLeadUi.Status(lead), trailing = trailing, isExpanded = null, note = null, filled = null, body = body, isFaint = false)
}

/** `name = User, withImpl = false`: the typed values generation sends. */
private fun argsSummaryOf(row: ModuleTemplate, state: KatachiScreenState, strings: KatachiStrings): String {
    val detail = row.template.detail ?: return strings.noArguments
    val inputs = state.form.inputsOf(row.id)
    val sent = shownParametersOf(detail, inputs).mapNotNull { parameter ->
        inputs[parameter.name]?.takeIf { it.isNotBlank() }?.let { "${parameter.name} = ${argValueOf(parameter, it)}" }
    }
    return if (sent.isEmpty()) strings.noArguments else sent.joinToString(", ")
}

internal fun resultStatusOf(result: GenerationItemResult): RowStatus = when (result) {
    is GenerationItemResult.Generated -> RowStatus.Done
    is GenerationItemResult.Skipped -> RowStatus.Skipped
    is GenerationItemResult.Failed -> RowStatus.Failed
    is GenerationItemResult.StoppedAtConflict -> RowStatus.Stopped
    is GenerationItemResult.Interrupted -> RowStatus.Interrupted
    GenerationItemResult.NotRun -> RowStatus.NotRun
}

private fun resultTrailingOf(result: GenerationItemResult, strings: KatachiStrings): String = when (result) {
    is GenerationItemResult.Generated -> strings.rowWritten(result.files.size)
    is GenerationItemResult.Skipped -> strings.fileSkipped
    is GenerationItemResult.Failed -> ""
    is GenerationItemResult.StoppedAtConflict -> ""
    is GenerationItemResult.Interrupted -> ""
    GenerationItemResult.NotRun -> strings.rowNotRun
}

private fun resultRowOf(
    base: TemplateRowUi,
    row: ModuleTemplate,
    result: GenerationItemResult,
    finished: GenerationState.Finished,
    state: KatachiScreenState,
    strings: KatachiStrings,
): TemplateRowUi = base.copy(
    lead = RowLeadUi.Status(resultStatusOf(result)),
    // The result names the template by its title, as the spec's wireframe does; the body says the rest.
    trailing = "",
    isExpanded = null,
    note = null,
    filled = null,
    isFaint = false,
    menu = emptyList(),
    body = RowBodyUi.Result(rowResultUiOf(row, result, finished, state.view, strings)),
)
