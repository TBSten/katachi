package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.FocusMove
import me.tbsten.katachi.intellij.presentation.FocusTarget
import me.tbsten.katachi.intellij.presentation.FooterUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListUi
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** The fixed footer: existing files, the file count and Generate; or the progress; or the summary. */
@Composable
internal fun Footer(footer: FooterUi, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (footer) {
            is FooterUi.Form -> FormFooter(footer, list, focus, onIntent)
            is FooterUi.Generating -> GeneratingFooter(footer, onIntent)
            is FooterUi.Result -> ResultFooter(footer, onIntent)
        }
    }
}

@Composable
private fun FormFooter(footer: FooterUi.Form, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    footer.reason?.let { reason ->
        val target = footer.reasonTarget
        Row(
            Modifier.then(
                if (target != null) {
                    // Pressing the reason opens that row, even outside the search, and focuses the field.
                    Modifier.clickable {
                        onIntent(KatachiIntent.RevealField(target))
                        focus.move(FocusMove.To(FocusTarget.Field(target)), list)
                    }
                } else {
                    Modifier
                },
            ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(AllIconsKeys.General.Warning, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(14.dp))
            Text(reason, color = warningText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Docked wide and short, the choice, the count and Generate share one line to leave the list room.
        if (maxWidth >= WideFooterMinWidth) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OnExistingChoice(footer, onIntent)
                footer.overwriteWarning?.let { OverwriteWarning(it, Modifier.widthIn(max = 320.dp)) }
                CountAndGenerate(footer, onIntent, countAtStart = false, modifier = Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OnExistingChoice(footer, onIntent)
                footer.overwriteWarning?.let { OverwriteWarning(it) }
                CountAndGenerate(footer, onIntent, countAtStart = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val WideFooterMinWidth = 560.dp

/** "Replaces existing files without asking", under or beside the combo when it says overwrite (spec 04). */
@Composable
private fun OverwriteWarning(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(AllIconsKeys.General.Warning, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(14.dp))
        Text(text, color = warningText, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OnExistingChoice(footer: FooterUi.Form, onIntent: (KatachiIntent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(footer.onExistingLabel, maxLines = 1)
        ListComboBox(
            items = footer.onExistingOptions.map { it.label },
            selectedIndex = footer.onExistingIndex,
            onSelectedItemChange = { index -> footer.onExistingOptions.getOrNull(index)?.let { onIntent(it.intent) } },
            modifier = Modifier.widthIn(min = 110.dp, max = 160.dp),
        )
    }
}

/** "3 files" (press for the list) and Generate: the count at the start when narrow, beside the button when wide. */
@Composable
private fun CountAndGenerate(footer: FooterUi.Form, onIntent: (KatachiIntent) -> Unit, countAtStart: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!countAtStart) Spacer(Modifier.weight(1f))
        Text(
            footer.countLabel,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            modifier = Modifier.clickable { onIntent(KatachiIntent.ToggleFileCountPopup) },
        )
        if (countAtStart) Spacer(Modifier.weight(1f))
        WithTooltip(footer.generateTooltip) {
            DefaultButton(onClick = { onIntent(footer.generate.intent) }, enabled = footer.generateEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(AllIconsKeys.Actions.Execute, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(footer.generate.label)
                }
            }
        }
    }
}

@Composable
private fun GeneratingFooter(footer: FooterUi.Generating, onIntent: (KatachiIntent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(footer.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        val progress = footer.progress
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))) {
            if (progress != null) HorizontalProgressBar(progress, Modifier.fillMaxWidth()) else ProgressBar(Modifier.fillMaxWidth())
        }
    }
    Row {
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { onIntent(footer.cancel.intent) }) { Text(footer.cancel.label) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultFooter(footer: FooterUi.Result, onIntent: (KatachiIntent) -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            if (footer.isComplete) AllIconsKeys.RunConfigurations.TestPassed else AllIconsKeys.General.Warning,
            contentDescription = null,
            modifier = Modifier.padding(top = 1.dp).size(14.dp),
        )
        Text(footer.summary)
    }
    footer.undo?.let { Text(it, color = faintText) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        footer.actions.forEachIndexed { index, action ->
            if (index == 0) {
                DefaultButton(onClick = { onIntent(action.intent) }) { Text(action.label) }
            } else {
                OutlinedButton(onClick = { onIntent(action.intent) }) { Text(action.label) }
            }
        }
    }
}
