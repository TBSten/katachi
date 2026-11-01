package com.example.sample.feature.settings

import androidx.lifecycle.ViewModel
import com.example.sample.data.settings.SettingsRepository
import com.example.sample.data.settings.SettingsRepositoryImpl
import com.example.sample.ui.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SettingsContent(
    val darkThemeEnabled: Boolean,
)

internal class SettingsViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<UiState<SettingsContent>>(UiState.Loading)

    val uiState: StateFlow<UiState<SettingsContent>> = mutableUiState.asStateFlow()

    init {
        mutableUiState.value = UiState.Content(
            SettingsContent(darkThemeEnabled = settingsRepository.isDarkThemeEnabled()),
        )
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        settingsRepository.setDarkThemeEnabled(enabled)
        mutableUiState.value = UiState.Content(SettingsContent(darkThemeEnabled = enabled))
    }
}
