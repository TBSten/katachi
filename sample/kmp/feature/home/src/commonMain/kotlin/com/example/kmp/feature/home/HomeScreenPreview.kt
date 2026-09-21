package com.example.kmp.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.kmp.ui.core.UiState
import com.example.kmp.ui.preview.PreviewRoot

/**
 * Previews of [HomeContent], one per [UiState] branch.
 *
 * They take the stateless composable, not [HomeScreen], so no `ViewModel` has to be built to
 * render them.
 */
@Preview
@Composable
private fun HomeLoadedPreview() {
    PreviewRoot {
        HomeContent(state = UiState.Loaded(listOf("alice", "bob")), onReload = {})
    }
}

@Preview
@Composable
private fun HomeLoadingPreview() {
    PreviewRoot {
        HomeContent(state = UiState.Loading, onReload = {})
    }
}

@Preview
@Composable
private fun HomeFailedPreview() {
    PreviewRoot {
        HomeContent(state = UiState.Failed("読み込みに失敗しました"), onReload = {})
    }
}
