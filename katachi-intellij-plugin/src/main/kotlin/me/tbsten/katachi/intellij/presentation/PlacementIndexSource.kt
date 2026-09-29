package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.PlacementUnavailableReason
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot

/**
 * What of the tool window's state the placement index depends on (issues 4, 11, 18). Two states with
 * the same source give the same index, so the index is rebuilt only when this changes, not on every
 * keystroke in the form.
 *
 * ```kotlin
 * val state = placementIndexStateOf(PlacementIndexSource.of(viewModel.state.value, viewModel.loadStarted.value)) { build(it) }
 * ```
 */
internal data class PlacementIndexSource(
    val phase: ScreenPhase,
    val modulesFound: Boolean,
    /** The list on screen: the cached JSON, then the latest load. */
    val snapshots: List<DescriptionSnapshot>,
    val loading: Boolean,
    /** A load started in this session ([KatachiToolWindowViewModel.loadStarted]). */
    val loadStarted: Boolean,
) {
    companion object {
        fun of(state: KatachiScreenState, loadStarted: Boolean): PlacementIndexSource = PlacementIndexSource(
            phase = state.phase,
            modulesFound = state.modules.isNotEmpty(),
            snapshots = state.snapshots,
            loading = state.loading != null,
            loadStarted = loadStarted,
        )
    }
}

/**
 * The index state [source] stands for; [build] makes the index of a list and is called only for
 * [PlacementIndexState.Ready], off the EDT (it walks every template).
 *
 * A list on screen is Ready even while a reload runs or after a reload failed (it stays, as in the
 * tool window). Without one: no definition module → Unavailable; nothing asked yet or still
 * detecting → NotLoaded (so a project without katachi never shows "loading", decision 17); a load
 * running or about to → Loading; definition modules found but no load started (loading without the
 * user is off and there is no cached JSON, decision 16) → Unavailable.
 */
internal fun placementIndexStateOf(source: PlacementIndexSource, build: (List<DescriptionSnapshot>) -> TemplatePlacementIndex): PlacementIndexState {
    if (source.snapshots.isNotEmpty()) return PlacementIndexState.Ready(build(source.snapshots))
    return when (val phase = source.phase) {
        is ScreenPhase.Empty -> when (phase.reason) {
            EmptyReason.NotSynced -> PlacementIndexState.Unavailable(PlacementUnavailableReason.NotSynced)
            // Found, loaded, and no template in it: nothing fits any file.
            EmptyReason.NoTemplates -> PlacementIndexState.Ready(build(emptyList()))
            EmptyReason.NotGradle, EmptyReason.NotInstalled, is EmptyReason.Outdated -> PlacementIndexState.Unavailable(PlacementUnavailableReason.NoDefinition)
        }
        is ScreenPhase.LoadError -> PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadFailed)
        ScreenPhase.Ready, ScreenPhase.Initializing -> when {
            source.loading -> PlacementIndexState.Loading
            // Still detecting: not "loading" yet, a project without katachi never shows it (decision 17).
            !source.modulesFound -> PlacementIndexState.NotLoaded
            source.loadStarted -> PlacementIndexState.Loading
            else -> PlacementIndexState.Unavailable(PlacementUnavailableReason.LoadingWithoutUserDisabled)
        }
    }
}
