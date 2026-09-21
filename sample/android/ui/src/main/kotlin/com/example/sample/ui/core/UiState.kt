package com.example.sample.ui.core

/**
 * State of one screen, as a view model exposes it.
 *
 * Lives in the `core` package of `:ui` rather than in `component` or `theme` because it
 * is the vocabulary both of those are written against, and because it deliberately has
 * no Compose dependency: view models in `:feature:*` produce it without importing
 * anything from the Compose runtime.
 */
sealed interface UiState<out T> {
    /** The screen has nothing to show yet. */
    data object Loading : UiState<Nothing>

    /** The screen has its [value] and can be drawn. */
    data class Content<T>(val value: T) : UiState<T>

    /** Loading failed; [message] is what to tell the user. */
    data class Error(val message: String) : UiState<Nothing>
}

/** The value of a [UiState.Content], or `null` while loading or after a failure. */
val <T> UiState<T>.contentOrNull: T?
    get() = (this as? UiState.Content)?.value
