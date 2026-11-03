package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability

internal enum class PlacementUnavailableReason {
    NotSynced,

    NoDefinition,

    LoadFailed,

    LoadingWithoutUserDisabled,
}

internal sealed interface PlacementIndexState {
    val availability: EntryAvailability

    data object NotLoaded : PlacementIndexState {
        override val availability: EntryAvailability get() = EntryAvailability.NotLoaded
    }

    data object Loading : PlacementIndexState {
        override val availability: EntryAvailability get() = EntryAvailability.Loading
    }

    data class Ready(val index: TemplatePlacementIndex) : PlacementIndexState {
        override val availability: EntryAvailability get() = EntryAvailability.Ready
    }

    data class Unavailable(val reason: PlacementUnavailableReason) : PlacementIndexState {
        override val availability: EntryAvailability get() = EntryAvailability.Unavailable
    }
}
