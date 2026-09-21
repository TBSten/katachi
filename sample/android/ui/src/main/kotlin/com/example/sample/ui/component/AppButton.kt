package com.example.sample.ui.component

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.sample.ui.preview.PreviewRoot

/**
 * Shared button.
 *
 * Feature modules call this instead of Material's [Button] so that a change of shape or
 * emphasis lands everywhere at once.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: AppButtonEmphasis = AppButtonEmphasis.Filled,
) {
    when (emphasis) {
        AppButtonEmphasis.Filled -> Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text = text)
        }

        AppButtonEmphasis.Outlined -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(text = text)
        }
    }
}

/** How much weight an [AppButton] carries on the screen it sits on. */
enum class AppButtonEmphasis {
    /** The single main action of a screen. */
    Filled,

    /** A secondary action, shown next to a [Filled] one. */
    Outlined,
}

@Preview(showBackground = true)
@Composable
private fun AppButtonFilledPreview() {
    PreviewRoot {
        AppButton(text = "保存", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun AppButtonOutlinedPreview() {
    PreviewRoot {
        AppButton(text = "キャンセル", onClick = {}, emphasis = AppButtonEmphasis.Outlined)
    }
}

// The one preview that pins `darkTheme` instead of following the system, so the dark
// variant is visible next to the light ones above without switching the whole IDE.
@Preview(showBackground = true)
@Composable
private fun AppButtonFilledDarkPreview() {
    PreviewRoot(darkTheme = true) {
        AppButton(text = "保存", onClick = {})
    }
}
