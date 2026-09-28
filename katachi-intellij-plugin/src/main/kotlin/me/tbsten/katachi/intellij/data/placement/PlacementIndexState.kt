package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability

/** Why the entries cannot use the template list. */
internal enum class PlacementUnavailableReason {
    /** No Gradle sync data yet. */
    NotSynced,

    /** The sync data has no definition module (katachi not installed): no service, no load (decision 17). */
    NoDefinition,

    /** `katachiInternalTemplatesJson` failed and there is no earlier list. */
    LoadFailed,

    /** Loading without the user is turned off and there is no cached JSON (decision 16). */
    LoadingWithoutUserDisabled,
}

/**
 * The placement index as `KatachiProjectService.placementIndex` publishes it (issues 4, 11, 18).
 * C1 rebuilds it off the EDT from the ViewModel's state, the one source of the loaded list.
 *
 * ```kotlin
 * when (val state = service.placementIndex.value) { is PlacementIndexState.Ready -> state.index.matchesForFile(file); else -> emptyList() }
 * ```
 */
internal sealed interface PlacementIndexState {
    val availability: EntryAvailability

    /** A definition module exists but nothing is loaded: the entries call `ensureLoaded()`. */
    data object NotLoaded : PlacementIndexState {
        override val availability: EntryAvailability get() = EntryAvailability.NotLoaded
    }

    data object Loading : PlacementIndexState {
        override val availability: EntryAvailability get() = EntryAvailability.Loading
    }

    /** Stays while a reload runs; replaced when it ends. */
    data class Ready(val index: TemplatePlacementIndex) : PlacementIndexState {
        override val availability: EntryAvailability get() = EntryAvailability.Ready
    }

    data class Unavailable(val reason: PlacementUnavailableReason) : PlacementIndexState {
        override val availability: EntryAvailability get() = EntryAvailability.Unavailable
    }
}
