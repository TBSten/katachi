package com.example.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.kmp.ui.preview.PreviewRoot

/**
 * Previews of [PrimaryButton], both of its states.
 *
 * `@Preview` here comes from `org.jetbrains.compose.ui:ui-tooling-preview`, the Compose
 * Multiplatform artifact `:ui` exposes — not from the Android-only
 * `androidx.compose.ui:ui-tooling-preview`, which the fully qualified name of the import
 * makes it easy to reach for by mistake. That is why these compile from `commonMain`.
 */
@Preview
@Composable
private fun PrimaryButtonPreview() {
    PreviewRoot {
        PrimaryButton(label = "保存", onClick = {})
    }
}

@Preview
@Composable
private fun PrimaryButtonDisabledPreview() {
    PreviewRoot {
        PrimaryButton(label = "保存", onClick = {}, enabled = false)
    }
}
