package com.example.sample.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object HomeRoute {
    const val PATH: String = "home"
}

fun NavGraphBuilder.homeScreen(onNavigateToSettings: () -> Unit) {
    composable(HomeRoute.PATH) {
        HomeScreen(onNavigateToSettings = onNavigateToSettings)
    }
}
