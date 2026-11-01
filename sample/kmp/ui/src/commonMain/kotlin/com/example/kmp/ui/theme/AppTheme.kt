package com.example.kmp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppSpacing {
    val small: Dp = 4.dp
    val medium: Dp = 8.dp
    val large: Dp = 16.dp
}

private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF3366FF),
    secondary = Color(0xFF6690FF),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content,
    )
}
