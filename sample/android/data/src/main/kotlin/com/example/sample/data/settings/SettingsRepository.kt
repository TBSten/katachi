package com.example.sample.data.settings

interface SettingsRepository {
    fun isDarkThemeEnabled(): Boolean

    fun setDarkThemeEnabled(enabled: Boolean)
}
