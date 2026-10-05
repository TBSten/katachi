package com.example.kmp.ui.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.example.kmp.ui.theme.AppTheme

/**
 * Wrapper every `@Preview` in this sample puts around its content.
 *
 * It exists so that a preview is never a bare `@Composable`: [AppTheme] supplies the colour
 * scheme the real app runs with, and [Surface] paints the theme background behind it, which
 * a preview otherwise renders on. Without it, every preview would repeat the same two lines
 * and would drift apart the moment the theme grows a parameter.
 *
 * This is the `preview` package of `:ui`, next to `component` / `theme` / `core`. It ships in
 * `commonMain` rather than in a test source set because the previews that use it live in
 * `commonMain` of the feature modules.
 */
@Composable
fun PreviewRoot(content: @Composable () -> Unit) {
    AppTheme {
        Surface(content = content)
    }
}
