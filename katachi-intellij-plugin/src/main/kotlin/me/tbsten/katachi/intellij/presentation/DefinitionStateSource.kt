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

/**
 * What of the tool window's state the definition model depends on (design 3: the ViewModel's state is
 * the one source; the definitions are derived from it as the placement index is). Two states with the
 * same source give the same [DefinitionState], so typing in the form changes nothing.
 *
 * ```kotlin
 * val source = DefinitionStateSource.of(viewModel.state.value, started, loadsWithoutUser)
 * val next = nextDefinitionState(current, lastSource, source, Instant.now())
 * ```
 */
internal data class DefinitionStateSource(
    val phase: ScreenPhase,
    val modules: List<KatachiModule>,
    /** The list on screen: the cached JSON, then the latest load. */
    val snapshots: List<DescriptionSnapshot>,
    val loading: Boolean,
    /** A reload failed while a list stays on screen. */
    val failure: LoadFailure?,
    val loadStarted: Boolean,
    /** The setting "load without a user action" when the source was taken. */
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

/**
 * The [DefinitionState] after [current] once the tool window's state goes from [previous] to [source], at
 * [now] (design 4.3). An answer is never dropped: it moves to `Loading(previous)`, `Unavailable(previous)`
 * or a stale `Ready`.
 *
 * - Nothing to read: the synced data has no definition module ([DefinitionUnavailableReason.NoDefinition] /
 *   [DefinitionUnavailableReason.NotSynced]), or every module is katachi 0.2.x ([DefinitionUnavailableReason.KatachiTooOld]).
 * - A load running: [DefinitionState.Loading].
 * - A load failed: the answer goes stale ([StaleReason.ReloadFailed]), or with none [DefinitionUnavailableReason.LoadFailed].
 * - A list read: [DefinitionState.Ready]. The same list as before (a cancelled load, a form change) keeps the
 *   answer as it was, a stale mark included. A list read from the disk's cache that no load of this session
 *   confirmed (loading without the user is off) is stale: the definition may have changed since.
 * - Modules found, nothing read: [DefinitionState.NotLoaded], or with loading without the user off
 *   [DefinitionUnavailableReason.LoadingWithoutUserDisabled].
 */
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
    val modules = source.modules.map { module -> source.snapshots.firstOrNull { it.module.id == module.id }?.module ?: module }
    if (modules.isNotEmpty() && modules.none { it.readsDefinition } && source.snapshots.none { it.definition != null }) {
        return DefinitionUnavailableReason.KatachiTooOld
    }
    return null
}

/** The definition part of each snapshot that has one (a templates-only module has none), in list order. */
internal fun definitionsOf(snapshots: List<DescriptionSnapshot>): List<DefinitionSnapshot> = snapshots.mapNotNull { snapshot ->
    snapshot.definition?.let { DefinitionSnapshot(snapshot.module, it, snapshot.templates, snapshot.katachiVersion, snapshot.loadedAt) }
}
