package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.ActionUi
import me.tbsten.katachi.intellij.presentation.FocusTarget
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListUi
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.RowLeadUi
import me.tbsten.katachi.intellij.presentation.RowMarker
import me.tbsten.katachi.intellij.presentation.TemplateRowUi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Where the inline form's vertical line sits: under the checkbox, one indent step (spec 03). */
internal val FormIndent = 38.dp

/** One template: the header line, and under it the form, the cause, the progress or the result. */
@Composable
internal fun TemplateRow(row: TemplateRowUi, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    val bringIntoView = remember { BringIntoViewRequester() }
    // Scroll a form that just opened fully into view, but not the ones open on the first frame.
    var wasOpen by remember { mutableStateOf(row.body is RowBodyUi.Form) }
    val isOpen = row.body is RowBodyUi.Form
    LaunchedEffect(isOpen) {
        if (isOpen && !wasOpen) bringIntoView.bringIntoView()
        wasOpen = isOpen
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)) {
        RowHeader(row, list, focus, onIntent)
        row.body?.let { body ->
            IndentedBody {
                when (body) {
                    is RowBodyUi.Form -> InlineForm(body.form, list, focus, onIntent)
                    is RowBodyUi.Cause -> CauseLines(body.lines)
                    is RowBodyUi.Running -> RunningLines(body)
                    is RowBodyUi.Result -> ResultLines(body.result, onIntent)
                }
            }
        }
    }
}

@Composable
private fun RowHeader(row: TemplateRowUi, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    val lead = row.lead
    val canToggle = lead is RowLeadUi.Check && lead.enabled
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The name keeps priority over the title and the trailing text, but never all of the line.
        val nameMax = maxWidth * 0.55f
        val trailingMax = maxWidth * 0.6f
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = RowHeight)
                .clickable(enabled = canToggle) { onIntent(KatachiIntent.ToggleCheck(row.id)) }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoldToggle(row, onIntent)
            Box(Modifier.widthIn(min = LeadSize), contentAlignment = Alignment.Center) {
                when (lead) {
                    is RowLeadUi.Check -> Checkbox(
                        checked = lead.checked,
                        onCheckedChange = { onIntent(KatachiIntent.ToggleCheck(row.id)) },
                        enabled = lead.enabled,
                        modifier = Modifier.listFocus(FocusTarget.Row(row.id), focus, list, onIntent).testTag(KatachiTestTags.check(row.id)),
                    )
                    is RowLeadUi.Status -> StatusIcon(lead.status)
                }
            }
            Spacer(Modifier.width(6.dp))
            if (row.marker != RowMarker.None) {
                val icon = if (row.marker == RowMarker.Blocked) AllIconsKeys.General.Error else AllIconsKeys.General.Warning
                WithTooltip(row.markerTooltip) { Icon(icon, contentDescription = row.markerTooltip, modifier = Modifier.padding(end = 4.dp).size(14.dp)) }
            }
            WithTooltip(row.tooltip, Modifier.widthIn(max = nameMax)) {
                Text(
                    row.name,
                    color = if (row.isFaint) faintText else JewelTheme.globalColors.text.normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RowSubtitle(row, Modifier.weight(1f).padding(start = 8.dp))
            listOfNotNull(row.filled, row.trailing.takeIf { it.isNotEmpty() }).forEach { text ->
                Text(
                    text,
                    color = faintText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = trailingMax),
                )
            }
            // The menu shows on a row whose form is open, and on a row that cannot be checked
            // ("show cause"); a plain row stays quiet.
            if (row.menu.isNotEmpty() && (row.body is RowBodyUi.Form || row.marker == RowMarker.Blocked)) {
                Spacer(Modifier.width(4.dp))
                MoreMenu(row.menu, onIntent)
            }
        }
    }
}

/** The height of a row's header, whether it shows a checkbox or a status icon. */
private val RowHeight = 26.dp

/** The checkbox's footprint; status icons sit centred in the same box so names line up. */
private val LeadSize = 24.dp

/** ▾ / ▸ of a checked row: folds the form but keeps the row checked (spec 04). */
@Composable
private fun FoldToggle(row: TemplateRowUi, onIntent: (KatachiIntent) -> Unit) {
    val expanded = row.isExpanded
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        if (expanded != null) {
            Icon(
                if (expanded) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp).clickable { onIntent(KatachiIntent.SetExpanded(row.id, !expanded)) },
            )
        }
    }
    Spacer(Modifier.width(2.dp))
}

/** "outside search, selected" and the title, faint, after the name; ellipsized first. */
@Composable
private fun RowSubtitle(row: TemplateRowUi, modifier: Modifier) {
    val faint = faintText
    val text = buildAnnotatedString {
        row.note?.let { withStyle(SpanStyle(color = faint)) { append(it) } }
        row.title?.let {
            if (length > 0) append("  ")
            withStyle(SpanStyle(color = faint)) { append(it) }
        }
    }
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

// The IDE's Jewel marks Tooltip with Compose's ExperimentalFoundationApi; the standalone one does not.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WithTooltip(tooltip: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (tooltip == null) {
        Box(modifier) { content() }
    } else {
        Tooltip(tooltip = { Text(tooltip) }, modifier = modifier, content = content)
    }
}

/** The `⋯` of a row: copy the command, show the cause. */
@Composable
internal fun MoreMenu(actions: List<ActionUi>, onIntent: (KatachiIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(20.dp)) {
            Icon(AllIconsKeys.Actions.MoreHorizontal, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        if (open) {
            PopupMenu(onDismissRequest = { open = false; true }, horizontalAlignment = Alignment.End) {
                actions.forEach { action ->
                    selectableItem(selected = false, onClick = { open = false; onIntent(action.intent) }) { Text(action.label) }
                }
            }
        }
    }
}

/** The vertical line and the one indent step of everything under a row. */
@Composable
internal fun IndentedBody(content: @Composable () -> Unit) {
    val color = lineColor
    // drawBehind rather than a Box with IntrinsicSize.Min: combo boxes do not answer intrinsic measurements.
    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = 4.dp)
            .drawBehind {
                val x = (FormIndent - 8.dp).toPx()
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(start = FormIndent, end = 8.dp),
    ) { content() }
}
