package com.example.kmp.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kmp.data.user.UserRepository
import com.example.kmp.navigation.Destination

/**
 * Connects the home screen to the navigation graph.
 *
 * The route is what a caller outside this feature touches: it owns the destination and it
 * owns how the screen gets its [HomeViewModel], so nothing else has to know either.
 */
object HomeRoute {
    val destination: Destination = Destination.Home

    @Composable
    fun Content(
        repository: UserRepository,
        modifier: Modifier = Modifier,
    ) {
        HomeScreen(
            viewModel = viewModel { HomeViewModel(repository) },
            modifier = modifier,
        )
    }
}
