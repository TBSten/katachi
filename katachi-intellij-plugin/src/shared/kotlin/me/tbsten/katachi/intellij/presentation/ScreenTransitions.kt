package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemReport
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.templatesOf

/*
 * The pure state transitions the ViewModel applies. Stage 2 grows these into KatachiReducer; they
 * live here so that the preview can build any state without coroutines.
 */

/**
 * Applies an intent that only touches the form or the search. Returns `null` for intents that need
 * the IDE or Gradle; those stay with the ViewModel. Inputs are frozen while generating.
 */
internal fun applyFormIntent(state: KatachiScreenState, intent: KatachiIntent): KatachiScreenState? {
    applyViewIntent(state.view, intent)?.let { return state.copy(view = it) }
    val rows = state.rows
    val editable = state.generation == null
    val form = state.form
    val next = when (intent) {
        is KatachiIntent.ToggleCheck -> if (editable) toggleCheck(form, rows, intent.templateId) else form
        is KatachiIntent.SetExpanded -> if (editable) setExpanded(form, intent.templateId, intent.expanded) else form
        is KatachiIntent.Input -> if (editable) inputField(form, rows, intent.field, intent.value) else form
        is KatachiIntent.Relink -> if (editable) relinkField(form, rows, intent.field) else form
        is KatachiIntent.SetOnExisting -> if (editable) form.copy(onExisting = intent.choice) else form
        is KatachiIntent.Search -> return state.copy(searchQuery = intent.query, view = state.view.copy(revealedRows = emptySet()))
        is KatachiIntent.RevealField -> {
            val id = intent.field.templateId
            return state.copy(
                form = setExpanded(form, id, expanded = true),
                view = state.view.copy(revealedRows = state.view.revealedRows + id),
            )
        }
        is KatachiIntent.RevealTemplate -> return revealTemplate(state, intent.templateId)
        KatachiIntent.DismissRemovedTemplates -> return state.copy(removedTemplates = emptyList())
        KatachiIntent.DismissLoadError -> return state.copy(loadErrorBanner = null)
        else -> return null
    }
    return state.copy(form = next)
}

/**
 * A load finished: swap the list, keep what still matches (E-45), and pick the phase. The list now
 * reflects the definition, so the "definition changed" banner goes away.
 *
 * A generation waiting for this load (E-41) keeps only the rows still checked: a removed template
 * is not generated, nor counted in "k / n". When no template is left, neither a waiting
 * generation nor a result stays: there is no list to show them on, and a result would come back,
 * stale, with the next list otherwise (provisional). The waiting generation's job then finds
 * nothing checked and ends without running anything.
 */
internal fun applyLoaded(state: KatachiScreenState, snapshots: List<DescriptionSnapshot>): KatachiScreenState {
    val merged = mergeAfterReload(state.form, templatesOf(snapshots))
    val phase = if (snapshots.all { it.templates.isEmpty() }) ScreenPhase.Empty(EmptyReason.NoTemplates) else ScreenPhase.Ready
    return state.copy(
        phase = phase,
        generation = when (val generation = state.generation) {
            is GenerationState.Running -> if (phase != ScreenPhase.Ready) {
                null
            } else {
                generation.copy(
                    rows = generation.rows.filter { merged.form.isSelected(it) },
                    statuses = generation.statuses.filterKeys { merged.form.isSelected(it) },
                )
            }
            is GenerationState.Finished -> generation.takeIf { phase == ScreenPhase.Ready }
            null -> null
        },
        snapshots = snapshots,
        loading = null,
        loadErrorBanner = null,
        removedTemplates = merged.removedTemplates,
        form = merged.form,
        // A cause shown for a broken preview belongs to the definition just replaced (provisional).
        view = state.view.copy(definitionChanged = false, causes = emptyMap()),
    )
}

/** A load failed: a banner over a cached list, else the error screen (spec 02 "エラー"). */
internal fun applyLoadFailed(state: KatachiScreenState, failure: LoadFailure, output: List<String>): KatachiScreenState =
    if (state.snapshots.isNotEmpty()) {
        state.copy(loading = null, loadErrorBanner = failure)
    } else {
        state.copy(loading = null, phase = ScreenPhase.LoadError(failure, output))
    }

/** A load was cancelled: the cached list stays as it was; without one, "loading stopped". */
internal fun applyLoadCancelled(state: KatachiScreenState): KatachiScreenState =
    if (state.snapshots.isNotEmpty()) {
        state.copy(loading = null)
    } else {
        state.copy(loading = null, phase = ScreenPhase.LoadError(LoadFailure.Cancelled, emptyList()))
    }

/** The rows of a generation, in list order, all waiting. */
internal fun startGeneration(state: KatachiScreenState, waitingForLoad: Boolean): KatachiScreenState {
    val ids = state.rows.map { it.id }.filter { state.form.isSelected(it) }
    return state.copy(generation = GenerationState.Running(ids, ids.associateWith { GenerationRowStatus.Waiting }, waitingForLoad))
}

/**
 * A result-screen button took the list back to [form]. The "▸ Details" opened under failed rows
 * belong to that result and close with it, so the next failure starts folded (provisional).
 */
internal fun leaveResult(state: KatachiScreenState, form: FormState): KatachiScreenState = state.copy(
    form = form,
    generation = null,
    view = state.view.copy(openDetails = state.view.openDetails.filterNotTo(LinkedHashSet()) { it is DetailsKey.ResultRow }),
)

internal fun updateGenerationRow(state: KatachiScreenState, id: TemplateId, status: GenerationRowStatus): KatachiScreenState =
    updateGenerationRow(state, listOf(id), status)

/** [updateGenerationRow] for every row of one run at once (design draft section 6 "IDE の複数選択"). */
internal fun updateGenerationRow(state: KatachiScreenState, ids: List<TemplateId>, status: GenerationRowStatus): KatachiScreenState {
    val running = state.generation as? GenerationState.Running ?: return state
    return state.copy(generation = running.copy(statuses = running.statuses + ids.associateWith { status }, waitingForLoad = false))
}

/**
 * Something other than Gradle ended a running generation (the IDE threw): the rows that finished
 * keep their result, the first one that did not fails with [failure], and the rest did not run. The
 * result screen shows it, so the tool window never stays "generating".
 */
internal fun abortGeneration(state: KatachiScreenState, failure: GenerationFailure, localHistoryLabel: String?): KatachiScreenState {
    val running = state.generation as? GenerationState.Running ?: return state
    var failed = false
    val items = running.rows.map { id ->
        val finished = (running.statuses[id] as? GenerationRowStatus.Finished)?.result
        val result = when {
            finished != null -> finished
            failed -> GenerationItemResult.NotRun
            else -> {
                failed = true
                GenerationItemResult.Failed(failure, emptyList())
            }
        }
        // One row per report: the runs Gradle would have batched together are not tracked here.
        GenerationItemReport(listOf(id), result)
    }
    return state.copy(generation = GenerationState.Finished(GenerationReport(items), localHistoryLabel))
}
