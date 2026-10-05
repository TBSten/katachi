package com.example.sample.ui.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.example.sample.ui.theme.AppTheme

/**
 * The wrapper every `@Preview` in this sample puts around its content.
 *
 * A `@Preview` is not rendered by the app, so nothing above it supplies the theme or a
 * background. Writing `AppTheme { }` by hand in each preview did that, but left every
 * preview free to drift; this is the one place that decides what a preview sits on.
 *
 * [darkTheme] is forwarded to [AppTheme] instead of always following the system, so two
 * `@Preview` functions over the same composable can show the light and the dark variant
 * side by side.
 */
@Composable
fun PreviewRoot(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    AppTheme(darkTheme = darkTheme) {
        Surface(content = content)
    }
}
