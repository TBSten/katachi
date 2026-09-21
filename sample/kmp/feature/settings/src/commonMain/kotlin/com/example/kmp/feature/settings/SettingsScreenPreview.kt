package com.example.kmp.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.kmp.ui.core.UiState
import com.example.kmp.ui.preview.PreviewRoot

/** Previews of [SettingsContent]. The platform string is hard-coded: a preview has none. */
@Preview
@Composable
private fun SettingsLoadedPreview() {
    PreviewRoot {
        SettingsContent(
            state = UiState.Loaded(SettingsUi(userCount = 2, platform = "preview")),
            onReload = {},
        )
    }
}

@Preview
@Composable
private fun SettingsLoadingPreview() {
    PreviewRoot {
        SettingsContent(state = UiState.Loading, onReload = {})
    }
}
