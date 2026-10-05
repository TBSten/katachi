package com.example.sample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

/**
 * Moves between the screens of the app.
 *
 * `:feature:*` modules depend on this interface instead of on `NavHostController`, so a
 * feature never reaches into the navigation graph of another feature. The graph itself is
 * assembled in `:app`, which is the only module that knows every destination.
 */
interface AppNavigator {
    /** Goes to [destination], keeping the current screen on the back stack. */
    fun navigateTo(destination: String)

    /** Goes back to the previous screen, if there is one. */
    fun navigateUp()
}

/**
 * An [AppNavigator] backed by [navController], remembered for as long as it stays the
 * same instance.
 */
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
