package com.example.sample.ui.core

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<T>(val value: T) : UiState<T>

    data class Error(val message: String) : UiState<Nothing>
}

val <T> UiState<T>.contentOrNull: T?
    get() = (this as? UiState.Content)?.value
