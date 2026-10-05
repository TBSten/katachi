package com.example.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.sample.feature.home.HomeRoute
import com.example.sample.feature.home.homeScreen
import com.example.sample.feature.settings.SettingsRoute
import com.example.sample.feature.settings.settingsScreen
import com.example.sample.navigation.rememberAppNavigator
import com.example.sample.ui.theme.AppTheme

/** Launcher activity: the one place that turns the module graph into a running screen. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppNavHost()
            }
        }
    }
}

/**
 * The navigation graph of the app.
 *
 * It lives in `:app` because it is the only module allowed to know every feature: each
 * `:feature:*` contributes one destination through its own route file, and none of them
 * can see another feature's screen.
 */
@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val navigator = rememberAppNavigator(navController)
    NavHost(navController = navController, startDestination = HomeRoute.PATH) {
        homeScreen(onNavigateToSettings = { navigator.navigateTo(SettingsRoute.PATH) })
        settingsScreen(onNavigateUp = navigator::navigateUp)
    }
}
