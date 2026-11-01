package com.example.sample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

interface AppNavigator {
    fun navigateTo(destination: String)

    fun navigateUp()
}

@Composable
fun rememberAppNavigator(navController: NavHostController): AppNavigator =
    remember(navController) { NavHostAppNavigator(navController) }

private class NavHostAppNavigator(
    private val navController: NavHostController,
) : AppNavigator {
    override fun navigateTo(destination: String) {
        navController.navigate(destination) {
            launchSingleTop = true
        }
    }

    override fun navigateUp() {
        navController.navigateUp()
    }
}
