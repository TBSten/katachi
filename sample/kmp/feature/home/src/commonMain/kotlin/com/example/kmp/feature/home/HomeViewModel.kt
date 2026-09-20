package com.example.kmp.feature.home

import com.example.kmp.data.UserRepository
import com.example.kmp.ui.core.UiState

/** Holds the state of the home screen. */
class HomeViewModel(private val repository: UserRepository) {
    fun state(): UiState<List<String>> = UiState.Loaded(repository.names())
}
