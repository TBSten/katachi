package com.example.sample.testing

import com.example.sample.data.settings.SettingsRepository

class FakeSettingsRepository(
    private var darkThemeEnabled: Boolean = false,
) : SettingsRepository {
    override fun isDarkThemeEnabled(): Boolean = darkThemeEnabled

    override fun setDarkThemeEnabled(enabled: Boolean) {
        darkThemeEnabled = enabled
    }
}
