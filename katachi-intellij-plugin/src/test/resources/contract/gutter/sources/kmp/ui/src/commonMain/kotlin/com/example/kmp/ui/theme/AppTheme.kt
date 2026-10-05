package com.example.kmp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens. Material 3 has no scheme for these, so they live next to the theme and are
 * read directly by the components.
 */
object AppSpacing {
    val small: Dp = 4.dp
    val medium: Dp = 8.dp
    val large: Dp = 16.dp
}

private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF3366FF),
    secondary = Color(0xFF6690FF),
)

/**
 * The single theme of the app. Every entry point (the Android activity, and the iOS
 * `ComposeUIViewController` once app/ios grows one) wraps its content in this.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content,
    )
}
