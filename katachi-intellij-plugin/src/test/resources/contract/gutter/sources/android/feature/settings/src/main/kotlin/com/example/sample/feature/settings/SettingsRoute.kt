package com.example.sample.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Navigation entry of the settings feature.
 *
 * The only thing the feature publishes outside itself; see the home feature's route for
 * why the screen and the view model stay internal to the module.
 */
object SettingsRoute {
    const val PATH: String = "settings"
}

/** Registers the settings screen as a destination of the graph being built. */
fun NavGraphBuilder.settingsScreen(onNavigateUp: () -> Unit) {
    composable(SettingsRoute.PATH) {
        SettingsScreen(onNavigateUp = onNavigateUp)
    }
}
