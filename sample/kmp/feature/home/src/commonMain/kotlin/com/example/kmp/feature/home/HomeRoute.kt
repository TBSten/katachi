package com.example.kmp.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kmp.data.user.UserRepository
import com.example.kmp.navigation.Destination

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
