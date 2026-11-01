package com.example.kmp.ui.preview

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.example.kmp.ui.theme.AppTheme

@Composable
fun PreviewRoot(content: @Composable () -> Unit) {
    AppTheme {
        Surface(content = content)
    }
}
