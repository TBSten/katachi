package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ResultFileUi
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.RowResultUi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** The `katachiTemplates` text of a row whose preview failed (E-07). */
@Composable
internal fun CauseLines(lines: List<String>) {
    Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { Text(it, color = faintText, fontFamily = FontFamily.Monospace) }
    }
}

/** Under the running row: the task path and the arguments, cut with … when too long (spec 03). */
@Composable
internal fun RunningLines(body: RowBodyUi.Running) {
    Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(body.taskLine, color = faintText, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(body.argsLine, color = faintText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

/** What replaced a row's form after generating: the files and how, or why it failed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResultLines(result: RowResultUi, onIntent: (KatachiIntent) -> Unit) {
    Column(Modifier.widthIn(max = FormMaxWidth).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        result.files.forEach { ResultFileLine(it, onIntent) }
        result.message.forEach { Text(it, color = if (result.isError) errorText else faintText) }
        if (result.actions.isNotEmpty() || result.details != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                result.actions.forEach { action -> Link(action.label, onClick = { onIntent(action.intent) }) }
                result.details?.takeIf { !it.isOpen }?.let { DetailsView(it, onIntent) }
            }
            // Opened, the details take the full width under the links.
            result.details?.takeIf { it.isOpen }?.let { DetailsView(it, onIntent) }
        }
    }
}

@Composable
private fun ResultFileLine(file: ResultFileUi, onIntent: (KatachiIntent) -> Unit) {
    val open = file.open
    Row(
        Modifier.then(if (open != null) Modifier.clickable { onIntent(open) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(AllIconsKeys.FileTypes.Any_type, contentDescription = null, modifier = Modifier.size(14.dp))
        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(file.badge, color = faintText, maxLines = 1)
        file.note?.let { Text(it, color = JewelTheme.globalColors.text.normal, maxLines = 1) }
    }
}
