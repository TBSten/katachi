package com.example.kmp.ui.core

/** What every screen's state looks like. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Loaded<T>(val value: T) : UiState<T>
}
