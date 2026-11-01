package com.example.kmp.ui.component.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.kmp.ui.component.PrimaryButton
import com.example.kmp.ui.preview.PreviewRoot

@Preview
@Composable
private fun PrimaryButtonPreview() {
    PreviewRoot {
        PrimaryButton(label = "Save", onClick = {})
    }
}

@Preview
@Composable
private fun PrimaryButtonDisabledPreview() {
    PreviewRoot {
        PrimaryButton(label = "Save", onClick = {}, enabled = false)
    }
}
