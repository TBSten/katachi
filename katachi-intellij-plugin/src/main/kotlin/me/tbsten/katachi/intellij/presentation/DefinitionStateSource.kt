package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.model.loadFailed
import me.tbsten.katachi.intellij.model.loaded
import me.tbsten.katachi.intellij.model.markStale
import me.tbsten.katachi.intellij.model.startLoading
import me.tbsten.katachi.intellij.model.unavailable
import java.time.Instant

internal data class DefinitionStateSource(
    val phase: ScreenPhase,
    val modules: List<KatachiModule>,
    val snapshots: List<DescriptionSnapshot>,
    val loading: Boolean,
    val failure: LoadFailure?,
    val loadStarted: Boolean,
    val loadsWithoutUser: Boolean,
) {
    companion object {
        fun of(state: KatachiScreenState, loadStarted: Boolean, loadsWithoutUser: Boolean): DefinitionStateSource = DefinitionStateSource(
            phase = state.phase,
            modules = state.modules,
            snapshots = state.snapshots,
            loading = state.loading != null,
            failure = state.loadErrorBanner ?: (state.phase as? ScreenPhase.LoadError)?.failure,
            loadStarted = loadStarted,
            loadsWithoutUser = loadsWithoutUser,
        )
    }
}

internal fun nextDefinitionState(
    current: DefinitionState,
    previous: DefinitionStateSource?,
    source: DefinitionStateSource,
    now: Instant,
): DefinitionState {
    unavailableReasonOf(source)?.let { return current.unavailable(it) }
    if (source.loading) return current.startLoading()
    source.failure?.takeIf { it != LoadFailure.Cancelled }?.let { return current.loadFailed(now, it) }
    val answer = current.answer
    if (source.snapshots.isNotEmpty() || source.phase is ScreenPhase.Empty) {
        val ready = if (answer != null && previous?.snapshots === source.snapshots) answer else current.loaded(definitionsOf(source.snapshots))
        return if (source.loadStarted) ready else ready.markStale(now, StaleReason.DefinitionChanged)
    }
    return when {
        answer != null -> answer
        source.modules.isNotEmpty() && !source.loadStarted && !source.loadsWithoutUser ->
            current.unavailable(DefinitionUnavailableReason.LoadingWithoutUserDisabled)
        else -> DefinitionState.NotLoaded
    }
}

private fun unavailableReasonOf(source: DefinitionStateSource): DefinitionUnavailableReason? {
    when (val phase = source.phase) {
        is ScreenPhase.Empty -> when (phase.reason) {
            EmptyReason.NotSynced -> return DefinitionUnavailableReason.NotSynced
            EmptyReason.NotGradle, EmptyReason.NotInstalled, is EmptyReason.Outdated -> return DefinitionUnavailableReason.NoDefinition
            EmptyReason.NoTemplates -> Unit
        }
        else -> Unit
    }
    // katachi 0.2.x: the templates task only. Candidates without a task list may still have the definition task,
    // until a load answered: what the snapshots' modules say (the tasks that answered) counts over the candidate's.
    if (definitionTooOldOf(source.modules, source.snapshots) != null) return DefinitionUnavailableReason.KatachiTooOld
    return null
}

internal fun definitionsOf(snapshots: List<DescriptionSnapshot>): List<DefinitionSnapshot> = snapshots.mapNotNull { snapshot ->
    snapshot.definition?.let { DefinitionSnapshot(snapshot.module, it, snapshot.templates, snapshot.katachiVersion, snapshot.loadedAt) }
}
