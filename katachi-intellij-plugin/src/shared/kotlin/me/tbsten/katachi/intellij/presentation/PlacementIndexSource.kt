package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.PlacementUnavailableReason
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot

internal data class PlacementIndexSource(
    val phase: ScreenPhase,
    val modulesFound: Boolean,
    val snapshots: List<DescriptionSnapshot>,
    val loading: Boolean,
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

// `build` is called only for Ready, off the EDT (it walks every template).
internal fun placementIndexStateOf(source: PlacementIndexSource, build: (List<DescriptionSnapshot>) -> TemplatePlacementIndex): PlacementIndexState {
    if (source.snapshots.isNotEmpty()) return PlacementIndexState.Ready(build(source.snapshots))
    return when (val phase = source.phase) {
        is ScreenPhase.Empty -> when (phase.reason) {
            EmptyReason.NotSynced -> PlacementIndexState.Unavailable(PlacementUnavailableReason.NotSynced)
            // Found, loaded, and no template in it: nothing fits any file.
            EmptyReason.NoTemplates -> PlacementIndexState.Ready(build(emptyList()))
            EmptyReason.NotGradle, is EmptyReason.NotInstalled, is EmptyReason.Outdated -> PlacementIndexState.Unavailable(PlacementUnavailableReason.NoDefinition)
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
