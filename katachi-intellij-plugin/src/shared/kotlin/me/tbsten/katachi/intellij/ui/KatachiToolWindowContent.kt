package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

/**
 * What the `katachi` tool window shows. PSI-free, so that the preview can build it by hand.
 *
 * TODO: grow into the states of the screen spec (initialising, loading, list + form, generating,
 *  result, empty, error).
 */
internal sealed interface KatachiToolWindowState {
    /** Shown until the template list is implemented. */
    data object Placeholder : KatachiToolWindowState
}

/**
 * The root Composable of the tool window.
 *
 * Compiled both into the plugin (bundled Jewel) and into the preview (standalone Jewel), so it may
 * only use API present in both. Labels are English: Japanese text is not yet verified to render
 * in the headless preview.
 */
@Composable
internal fun KatachiToolWindowContent(state: KatachiToolWindowState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            KatachiToolWindowState.Placeholder -> {
                Text("katachi")
                Text("Coming soon: pick a template, fill in its arguments and generate the files.")
            }
        }
    }
}
