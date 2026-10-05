package com.example.kmp.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kmp.data.user.UserRepository
import com.example.kmp.navigation.Destination

/** Connects the settings screen to the navigation graph. */
object SettingsRoute {
    val destination: Destination = Destination.Settings

    @Composable
    fun Content(
        repository: UserRepository,
        modifier: Modifier = Modifier,
    ) {
        SettingsScreen(
            viewModel = viewModel { SettingsViewModel(repository) },
            modifier = modifier,
        )
    }
}
