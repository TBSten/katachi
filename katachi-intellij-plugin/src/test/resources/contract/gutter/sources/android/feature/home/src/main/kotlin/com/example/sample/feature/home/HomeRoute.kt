package com.example.sample.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Navigation entry of the home feature.
 *
 * The only thing the feature publishes outside itself: `:app` builds the graph from
 * [PATH] and [homeScreen], and never sees `HomeScreen` or `HomeViewModel`.
 */
object HomeRoute {
    const val PATH: String = "home"
}

/** Registers the home screen as a destination of the graph being built. */
fun NavGraphBuilder.homeScreen(onNavigateToSettings: () -> Unit) {
    composable(HomeRoute.PATH) {
        HomeScreen(onNavigateToSettings = onNavigateToSettings)
    }
}
