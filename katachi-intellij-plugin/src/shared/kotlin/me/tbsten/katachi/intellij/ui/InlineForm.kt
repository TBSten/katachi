package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.ExpectedFileUi
import me.tbsten.katachi.intellij.presentation.FileSummaryUi
import me.tbsten.katachi.intellij.presentation.FocusTarget
import me.tbsten.katachi.intellij.presentation.FormUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListUi
import me.tbsten.katachi.intellij.presentation.TemplateRowUi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Wide windows keep the fields left-aligned at this width instead of stretching (spec 03). */
internal val FormMaxWidth = 520.dp

/** Below this the label goes above its field; side by side would leave the field too narrow. */
private val SideBySideMinWidth = 240.dp

/** The inline form: the template's summary, its fields in order, then the file summary (spec 03). */
@Composable
internal fun InlineForm(form: FormUi, row: TemplateRowUi, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = when {
            maxWidth < SideBySideMinWidth -> null
            maxWidth >= 400.dp -> WideLabelWidth
            else -> 84.dp
        }
        Column(Modifier.widthIn(max = FormMaxWidth).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            form.summary?.let { Text(it, color = faintText) }
            form.fields.forEach { field -> ParameterField(field, labelWidth, list, focus, onIntent) }
            FileSummaryLine(form.files, row, list, focus, onIntent)
        }
    }
}

/** "UserRepository.kt and 1 more file": pressing it (or Enter) opens the expected-file list. */
@Composable
private fun FileSummaryLine(files: FileSummaryUi, row: TemplateRowUi, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f)
                    .listFocus(FocusTarget.FileSummary(row.id), focus, list, onIntent)
                    .focusable()
                    .clickable { onIntent(files.toggle) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    if (files.isOpen) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(files.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (files.hasWarning) Icon(AllIconsKeys.General.Warning, contentDescription = null, modifier = Modifier.padding(start = 2.dp).size(14.dp))
            }
            if (row.menu.isNotEmpty()) MoreMenu(row.menu, onIntent)
        }
        if (files.isOpen) {
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(files.caption, color = faintText)
                files.files.forEach { ExpectedFileLine(it) }
            }
        }
    }
}

/** A file name large, its directory small under it, shortened in the middle (spec 03). */
@Composable
internal fun ExpectedFileLine(file: ExpectedFileUi) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            if (file.isWarning) AllIconsKeys.General.Warning else AllIconsKeys.FileTypes.Any_type,
            contentDescription = null,
            modifier = Modifier.padding(top = 1.dp).size(14.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            WithTooltip(file.locationTooltip) {
                Text(file.location, color = if (file.isWarning) warningText else faintText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        file.badge?.let { Text(it, color = faintText, maxLines = 1) }
    }
}
