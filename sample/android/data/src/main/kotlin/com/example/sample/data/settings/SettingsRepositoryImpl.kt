package com.example.sample.data.settings

class SettingsRepositoryImpl : SettingsRepository {
    private var darkThemeEnabled = false

    override fun isDarkThemeEnabled(): Boolean = darkThemeEnabled

    override fun setDarkThemeEnabled(enabled: Boolean) {
        darkThemeEnabled = enabled
    }
}
