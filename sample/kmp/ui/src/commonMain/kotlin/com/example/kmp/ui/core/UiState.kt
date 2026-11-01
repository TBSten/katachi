package com.example.kmp.ui.core

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Loaded<T>(val value: T) : UiState<T>

    data class Failed(val message: String) : UiState<Nothing>
}

fun <T> UiState<T>.valueOrNull(): T? = (this as? UiState.Loaded)?.value
