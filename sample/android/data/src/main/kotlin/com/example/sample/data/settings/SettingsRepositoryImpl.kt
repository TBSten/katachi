package com.example.sample.data.settings

/**
 * Production implementation of [SettingsRepository].
 *
 * Kept in memory: this sample is built and inspected, never run against real storage.
 */
class SettingsRepositoryImpl : SettingsRepository {
    private var darkThemeEnabled = false

    override fun isDarkThemeEnabled(): Boolean = darkThemeEnabled

    override fun setDarkThemeEnabled(enabled: Boolean) {
        darkThemeEnabled = enabled
    }
}
