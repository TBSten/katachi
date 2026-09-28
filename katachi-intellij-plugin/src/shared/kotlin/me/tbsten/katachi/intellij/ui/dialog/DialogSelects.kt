package me.tbsten.katachi.intellij.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.ui.FieldTrailingWidth
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text

/** Roughly how wide one character of a label is: a full-width (Japanese) one is wider than a Latin one. */
private const val LATIN_CHAR_WIDTH_DP = 8.2f
private const val WIDE_CHAR_WIDTH_DP = 13.5f

private fun labelWidthOf(label: String): Dp = label.sumOf { (if (it.code >= 0x2E80) WIDE_CHAR_WIDTH_DP else LATIN_CHAR_WIDTH_DP).toDouble() }.toFloat().dp

/**
 * [label] and [content] on one line when the label fits the label column ([labelWidth], `null` when
 * the window is too narrow for that), else the label above its content.
 */
@Composable
internal fun LabeledContent(label: String, labelWidth: Dp?, content: @Composable () -> Unit) {
    val side = labelWidth?.takeIf { labelWidthOf(label) <= it - 6.dp }
    if (side != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(side).padding(top = 6.dp, end = 6.dp))
            Column(Modifier.weight(1f).padding(end = FieldTrailingWidth)) { content() }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Column(Modifier.fillMaxWidth().padding(end = FieldTrailingWidth)) { content() }
        }
    }
}

/** A select box with a label: the template select and the definition select. */
@Composable
internal fun LabeledSelect(
    label: String,
    labelWidth: Dp?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    LabeledContent(label, labelWidth) {
        if (options.isEmpty()) {
            Text("-", modifier = modifier)
        } else {
            ListComboBox(
                items = options,
                selectedIndex = selectedIndex.coerceIn(0, options.lastIndex),
                onSelectedItemChange = onSelect,
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}
