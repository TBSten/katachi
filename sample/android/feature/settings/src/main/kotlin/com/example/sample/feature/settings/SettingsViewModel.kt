package com.example.sample.feature.settings

import androidx.lifecycle.ViewModel
import com.example.sample.data.settings.SettingsRepository
import com.example.sample.data.settings.SettingsRepositoryImpl
import com.example.sample.ui.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What [SettingsScreen] draws once it has something to draw. */
data class SettingsContent(
    val darkThemeEnabled: Boolean,
)

/** State holder of [SettingsScreen]. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<UiState<SettingsContent>>(UiState.Loading)

    /** State of the settings screen, collected by the composable. */
    val uiState: StateFlow<UiState<SettingsContent>> = mutableUiState.asStateFlow()

    init {
        mutableUiState.value = UiState.Content(
            SettingsContent(darkThemeEnabled = settingsRepository.isDarkThemeEnabled()),
        )
    }

    /** Records the user's choice of theme and republishes the screen state. */
    fun setDarkThemeEnabled(enabled: Boolean) {
        settingsRepository.setDarkThemeEnabled(enabled)
        mutableUiState.value = UiState.Content(SettingsContent(darkThemeEnabled = enabled))
    }
}
