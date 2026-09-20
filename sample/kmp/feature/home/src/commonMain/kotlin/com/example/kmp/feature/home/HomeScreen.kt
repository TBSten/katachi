package com.example.kmp.feature.home

import com.example.kmp.ui.component.PrimaryButton
import com.example.kmp.ui.core.UiState

/** The home screen. A stub: no Compose in this sample. */
class HomeScreen(private val viewModel: HomeViewModel) {
    fun render(): String = when (val state = viewModel.state()) {
        is UiState.Loading -> "loading"
        is UiState.Loaded -> state.value.joinToString { PrimaryButton(it).render() }
    }
}
