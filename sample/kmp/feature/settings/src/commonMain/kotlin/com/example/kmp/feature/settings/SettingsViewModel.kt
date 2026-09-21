package com.example.kmp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmp.data.platform.platformName
import com.example.kmp.data.user.UserRepository
import com.example.kmp.ui.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the settings screen shows. */
data class SettingsUi(
    val userCount: Int,
    val platform: String,
)

/** Holds the state of the settings screen. */
class SettingsViewModel(private val repository: UserRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<UiState<SettingsUi>>(UiState.Loading)

    val state: StateFlow<UiState<SettingsUi>> = mutableState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        mutableState.value = UiState.Loading
        viewModelScope.launch {
            mutableState.value = UiState.Loaded(
                SettingsUi(
                    userCount = repository.names().size,
                    platform = platformName(),
                ),
            )
        }
    }
}
