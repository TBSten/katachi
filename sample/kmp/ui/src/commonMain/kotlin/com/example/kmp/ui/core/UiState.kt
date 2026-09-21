package com.example.kmp.ui.core

/**
 * What every screen's state looks like.
 *
 * Deliberately free of Compose: a state holder produces it and a `@Composable` consumes it,
 * so keeping it a plain sealed interface lets both sides be tested without a Compose runtime.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Loaded<T>(val value: T) : UiState<T>

    data class Failed(val message: String) : UiState<Nothing>
}

/** The loaded value, or `null` while the screen is still loading or has failed. */
fun <T> UiState<T>.valueOrNull(): T? = (this as? UiState.Loaded)?.value
