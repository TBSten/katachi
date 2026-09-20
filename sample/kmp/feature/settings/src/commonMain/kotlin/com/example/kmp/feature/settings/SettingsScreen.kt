package com.example.kmp.feature.settings

import com.example.kmp.ui.component.PrimaryButton
import com.example.kmp.ui.core.UiState

/** The settings screen. A stub: no Compose in this sample. */
class SettingsScreen(private val viewModel: SettingsViewModel) {
    fun render(): String = when (val state = viewModel.state()) {
        is UiState.Loading -> "loading"
        is UiState.Loaded -> PrimaryButton("users: ${state.value}").render()
    }
}
