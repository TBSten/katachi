package com.example.sample.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

/**
 * The home screen, wired to its [HomeViewModel].
 *
 * This overload is what the navigation graph calls. It only collects state and forwards
 * events, so the stateless overload below stays previewable and testable.
 */
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

/** The home screen as a function of its state. */
@Composable
internal fun HomeScreen(
    uiState: UiState<HomeContent>,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (uiState) {
            UiState.Loading -> CircularProgressIndicator()

            is UiState.Content -> {
                Text(
                    text = "こんにちは、${uiState.value.userName} さん",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "${uiState.value.visitCount} 回目の表示です",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppButton(text = "読み直す", onClick = onRefresh)
            }

            is UiState.Error -> Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        AppButton(
            text = "設定へ",
            onClick = onNavigateToSettings,
            emphasis = AppButtonEmphasis.Outlined,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenContentPreview() {
    PreviewRoot {
        HomeScreen(
            uiState = UiState.Content(HomeContent(userName = "katachi", visitCount = 1)),
            onRefresh = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingPreview() {
    PreviewRoot {
        HomeScreen(
            uiState = UiState.Loading,
            onRefresh = {},
            onNavigateToSettings = {},
        )
    }
}
