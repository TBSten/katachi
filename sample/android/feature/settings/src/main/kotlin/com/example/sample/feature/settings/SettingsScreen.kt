package com.example.sample.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sample.ui.component.AppButton
import com.example.sample.ui.component.AppButtonEmphasis
import com.example.sample.ui.core.UiState
import com.example.sample.ui.preview.PreviewRoot

/** The settings screen, wired to its [SettingsViewModel]. */
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onDarkThemeChange = viewModel::setDarkThemeEnabled,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

/** The settings screen as a function of its state. */
@Composable
internal fun SettingsScreen(
    uiState: UiState<SettingsContent>,
    onDarkThemeChange: (Boolean) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when (uiState) {
            UiState.Loading -> CircularProgressIndicator()

            is UiState.Content -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "ダークテーマ", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = uiState.value.darkThemeEnabled,
                    onCheckedChange = onDarkThemeChange,
                )
            }

            is UiState.Error -> Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        AppButton(
            text = "戻る",
            onClick = onNavigateUp,
            emphasis = AppButtonEmphasis.Outlined,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PreviewRoot {
        SettingsScreen(
            uiState = UiState.Content(SettingsContent(darkThemeEnabled = true)),
            onDarkThemeChange = {},
            onNavigateUp = {},
        )
    }
}
