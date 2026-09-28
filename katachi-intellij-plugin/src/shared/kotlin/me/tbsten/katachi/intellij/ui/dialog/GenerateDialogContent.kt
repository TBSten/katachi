package me.tbsten.katachi.intellij.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The generate dialog's content: template select, definition select (two or more definitions),
 * the form, the target path and the existing-file notice, stacked so that it adapts to the window
 * size (decision 19: Compose instead of `JBUI.Panel`). `DialogWrapper` (D4) supplies the buttons.
 *
 * ```kotlin
 * JewelComposePanel { GenerateDialogContent(uiState, strings, actions) }
 * ```
 */
@Composable
internal fun GenerateDialogContent(
    state: GenerateDialogUiState,
    strings: GenerateDialogStrings,
    actions: GenerateDialogActions,
    modifier: Modifier = Modifier,
) {
    // TODO(D2): the parts.
    Box(modifier)
}
