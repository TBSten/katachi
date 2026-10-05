package com.example.kmp.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp.data.user.UserRepository
import com.example.kmp.feature.home.HomeRoute
import com.example.kmp.feature.settings.SettingsRoute
import com.example.kmp.navigation.Destination
import com.example.kmp.navigation.Navigator
import com.example.kmp.ui.theme.AppTheme

/**
 * The whole app below the platform entry point: theme, navigation bar, and the route that
 * matches the current destination.
 */
@Composable
fun AppRoot(
    repository: UserRepository,
    modifier: Modifier = Modifier,
) {
    val navigator = remember { Navigator() }
    val current by navigator.current.collectAsState()

    AppTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    Destination.topLevel.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == current,
                            onClick = { navigator.navigateTo(destination) },
                            icon = { Text(text = destination.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val content = Modifier.padding(padding)
            when (current) {
                is Destination.Home -> HomeRoute.Content(repository, content)
                is Destination.Settings -> SettingsRoute.Content(repository, content)
            }
        }
    }
}
