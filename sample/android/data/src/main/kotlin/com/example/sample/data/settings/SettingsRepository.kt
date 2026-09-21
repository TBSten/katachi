package com.example.sample.data.settings

/** Reads and writes user settings. */
interface SettingsRepository {
    /** Whether the user asked for the dark theme. */
    fun isDarkThemeEnabled(): Boolean

    /** Records the user's choice of theme. */
    fun setDarkThemeEnabled(enabled: Boolean)
}
