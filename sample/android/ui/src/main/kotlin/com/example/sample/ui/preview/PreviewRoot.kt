package com.example.sample.ui.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.example.sample.ui.theme.AppTheme

@Composable
fun PreviewRoot(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    AppTheme(darkTheme = darkTheme) {
        Surface(content = content)
    }
}
