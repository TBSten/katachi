package com.example.kmp.feature.home.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One user on the home screen. Only the home screen draws it, so it stays in this module. */
@Composable
internal fun HomeUserCard(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(text = name, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}
