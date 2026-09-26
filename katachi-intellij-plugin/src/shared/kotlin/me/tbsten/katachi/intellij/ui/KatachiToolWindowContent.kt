package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiUiState

/**
 * The root Composable of the `katachi` tool window: draws [state] and sends what the user does to
 * [onIntent]. It decides nothing; every text and every enabled state comes worded in [state].
 *
 * Compiled both into the plugin (bundled Jewel) and into the preview (standalone Jewel), so it may
 * only use API present in both.
 */
@Composable
internal fun KatachiToolWindowContent(state: KatachiUiState, onIntent: (KatachiIntent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (val body = state.body) {
            is BodyUi.Initializing -> InitializingView(body)
            is BodyUi.InitialLoading -> InitialLoadingView(body, onIntent)
            is BodyUi.Empty -> MessageView(body.message, onIntent)
            is BodyUi.Error -> MessageView(body.message, onIntent)
            is BodyUi.Listing -> TemplateListPane(body.list, onIntent)
        }
    }
}
