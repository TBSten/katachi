package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.tbsten.katachi.intellij.presentation.ActionUi
import me.tbsten.katachi.intellij.presentation.BannerKind
import me.tbsten.katachi.intellij.presentation.BannerUi
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.DetailsUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.MessageUi
import me.tbsten.katachi.intellij.presentation.RefreshUi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Titles are a step larger as well as bold: the headless preview (and some IDE font fallbacks) draw
 * Japanese in one weight only, so bold alone does not set a Japanese title apart.
 */
private val TitleSize = 15.sp

/** Wide tool windows keep status texts at a readable measure instead of spanning the width. */
private val MessageMaxWidth = 480.dp

@Composable
internal fun InitializingView(body: BodyUi.Initializing) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spinner()
            Text(body.message, color = faintText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InitialLoadingView(body: BodyUi.InitialLoading, onIntent: (KatachiIntent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Column(Modifier.widthIn(max = MessageMaxWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(body.title, fontWeight = FontWeight.Bold, fontSize = TitleSize)
            ProgressBar(Modifier.fillMaxWidth())
            body.currentTask?.let { Text(it, color = faintText, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(body.note, color = faintText)
            ActionButtons(body.actions, onIntent)
        }
    }
}

/** An indeterminate bar; a still one in the preview. */
@Composable
internal fun ProgressBar(modifier: Modifier = Modifier) {
    if (LocalStaticRendering.current) {
        Box(modifier.height(4.dp).background(lineColor, RoundedCornerShape(2.dp)))
    } else {
        IndeterminateHorizontalProgressBar(modifier)
    }
}

/** An empty state or an error: title, what to do, buttons, and "▸ Details" (spec 02). */
@Composable
internal fun MessageView(message: MessageUi, onIntent: (KatachiIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.widthIn(max = MessageMaxWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.isError) Icon(AllIconsKeys.General.Error, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                Text(message.title, fontWeight = FontWeight.Bold, fontSize = TitleSize)
            }
            message.body.forEach { Text(it) }
            if (message.actions.isNotEmpty()) ActionButtons(message.actions, onIntent, Modifier.padding(top = 4.dp))
            message.details?.let { DetailsView(it, onIntent) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActionButtons(actions: List<ActionUi>, onIntent: (KatachiIntent) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEach { action -> OutlinedButton(onClick = { onIntent(action.intent) }) { Text(action.label) } }
    }
}

/** "▸ Details": the lines in a monospace block once opened. */
@Composable
internal fun DetailsView(details: DetailsUi, onIntent: (KatachiIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.clickable { onIntent(details.toggle) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                if (details.isOpen) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(details.label)
        }
        if (details.isOpen) {
            Column(
                Modifier.fillMaxWidth().border(1.dp, lineColor).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                details.lines.forEach { Text(it, fontFamily = FontFamily.Monospace, color = faintText) }
            }
        }
    }
}

/** A band above the list: icon, text, actions and ✕ (spec 02, 04). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Banner(banner: BannerUi, onIntent: (KatachiIntent) -> Unit) {
    val icon = when (banner.kind) {
        BannerKind.Info -> AllIconsKeys.General.Information
        BannerKind.Warning -> AllIconsKeys.General.Warning
        BannerKind.Error -> AllIconsKeys.General.Error
    }
    Row(
        Modifier.fillMaxWidth().background(bandColor).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(banner.text)
                banner.actions.forEach { action -> Link(action.label, onClick = { onIntent(action.intent) }) }
            }
        }
        banner.dismiss?.let { dismiss -> CloseButton { onIntent(dismiss) } }
    }
}

@Composable
internal fun CloseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(20.dp)) {
        Icon(AllIconsKeys.General.CloseSmall, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

/** The thin progress over a cached list; the list stays usable (spec 02). */
@Composable
internal fun RefreshBar(refresh: RefreshUi, onIntent: (KatachiIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressBar(Modifier.weight(1f))
        Text(refresh.label, color = faintText, maxLines = 1)
        CloseButton { onIntent(refresh.cancel.intent) }
    }
}
