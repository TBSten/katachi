package com.example.kmp.feature.settings

import com.example.kmp.data.UserRepository
import com.example.kmp.ui.core.UiState

/** Holds the state of the settings screen. */
class SettingsViewModel(private val repository: UserRepository) {
    fun state(): UiState<Int> = UiState.Loaded(repository.names().size)
}
