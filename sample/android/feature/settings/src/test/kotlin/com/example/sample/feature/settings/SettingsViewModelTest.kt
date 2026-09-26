package com.example.sample.feature.settings

import com.example.sample.testing.FakeSettingsRepository
import com.example.sample.ui.core.UiState
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun `リポジトリから読んだ値を Content として公開する`() {
        val viewModel = SettingsViewModel(settingsRepository = FakeSettingsRepository(darkThemeEnabled = true))

        assertEquals(UiState.Content(SettingsContent(darkThemeEnabled = true)), viewModel.uiState.value)
    }
}
