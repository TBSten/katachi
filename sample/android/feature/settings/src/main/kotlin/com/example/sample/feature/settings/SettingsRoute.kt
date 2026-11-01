package com.example.sample.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object SettingsRoute {
    const val PATH: String = "settings"
}

fun NavGraphBuilder.settingsScreen(onNavigateUp: () -> Unit) {
    composable(SettingsRoute.PATH) {
        SettingsScreen(onNavigateUp = onNavigateUp)
    }
}
