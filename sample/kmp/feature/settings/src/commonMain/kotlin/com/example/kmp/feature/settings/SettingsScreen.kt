package com.example.kmp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.kmp.ui.component.PrimaryButton
import com.example.kmp.ui.core.UiState
import com.example.kmp.ui.theme.AppSpacing

/** The settings screen, bound to its state holder. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    SettingsContent(
        state = state,
        onReload = viewModel::reload,
        modifier = modifier,
    )
}

/** The same screen without the state holder. */
@Composable
internal fun SettingsContent(
    state: UiState<SettingsUi>,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(AppSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
    ) {
        when (state) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Failed -> Text(text = state.message)
            is UiState.Loaded -> {
                Text(text = "ユーザー数: ${state.value.userCount}")
                Text(text = "プラットフォーム: ${state.value.platform}")
            }
        }
        PrimaryButton(label = "再読み込み", onClick = onReload)
    }
}
