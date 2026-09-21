package com.example.sample.testing

import com.example.sample.data.settings.SettingsRepository

/** In-memory [SettingsRepository] for tests of other modules. */
class FakeSettingsRepository(
    private var darkThemeEnabled: Boolean = false,
) : SettingsRepository {
    override fun isDarkThemeEnabled(): Boolean = darkThemeEnabled

    override fun setDarkThemeEnabled(enabled: Boolean) {
        darkThemeEnabled = enabled
    }
}
