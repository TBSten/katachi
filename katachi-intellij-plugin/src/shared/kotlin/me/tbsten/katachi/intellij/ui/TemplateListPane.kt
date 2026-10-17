package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import org.jetbrains.jewel.foundation.theme.JewelTheme
import me.tbsten.katachi.intellij.ui.listFocus
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.FocusTarget
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.ListUi
import me.tbsten.katachi.intellij.presentation.NavKey
import me.tbsten.katachi.intellij.presentation.navigate
import me.tbsten.katachi.intellij.presentation.SearchUi
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * The one screen of the tool window (spec 02, 03): search, the list with the inline forms under
 * checked rows, and the fixed footer. The same order whether docked tall or wide.
 */
@Composable
internal fun TemplateListPane(
    list: ListUi,
    onIntent: (KatachiIntent) -> Unit,
    // Which focus request the search field has already taken. Held by the caller, which outlives this pane: the pane is
    // dropped while an error, an empty state or the first load shows, and must not take an old request again when it returns.
    focusServed: MutableIntState = remember { mutableIntStateOf(0) },
) {
    val focus = rememberListFocusController(list)
    CompositionLocalProvider(LocalCandidatePopupOpen provides focus.popup) {
        Column(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                // ⌘⏎ / Ctrl+Enter from anywhere in the tool window; a disabled Generate still eats the key.
                if (!isGenerateShortcut(event)) return@onPreviewKeyEvent false
                // An open candidate popup takes it (it closes, taking the highlighted candidate): nothing is generated.
                if (focus.popup.isOpen) return@onPreviewKeyEvent false
                navigate(list, FocusTarget.Search, NavKey.Generate).intent?.let(onIntent)
                true
            },
        ) {
            list.refresh?.let { RefreshBar(it, onIntent) }
            list.banners.forEach { Banner(it, onIntent) }
            SearchField(list.search, list, focus, focusServed, onIntent)
            Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val empty = list.emptySearch
                if (empty != null) {
                    MessageView(empty, onIntent)
                } else {
                    ListBody(list, focus, onIntent)
                }
            }
            Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
            Footer(list.footer, list, focus, onIntent)
        }
    }
}

@Composable
private fun SearchField(
    search: SearchUi,
    list: ListUi,
    focus: ListFocusController,
    focusServed: MutableIntState,
    onIntent: (KatachiIntent) -> Unit,
) {
    val state = rememberSyncedTextFieldState(search.query) { onIntent(KatachiIntent.Search(it)) }
    // T-3: the IDE asked for the cursor here (Find Action). Once per request number ([focusServed] outlives this pane), so a
    // list that appears after the request (loading, an empty state came first) takes it when it shows, and never again.
    LaunchedEffect(search.focusRequest) {
        val request = search.focusRequest
        if (request == 0 || request == focusServed.intValue) return@LaunchedEffect
        focusServed.intValue = request
        // Wait for the field to be placed; a disabled field (while generating) refuses the focus, which is fine.
        withFrameNanos { }
        runCatching { focus.requesterOf(FocusTarget.Search).requestFocus() }
    }
    TextField(
        state = state,
        enabled = search.enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).listFocus(FocusTarget.Search, focus, list, onIntent)
            .testTag(KatachiTestTags.SEARCH),
        placeholder = { Text(search.placeholder, color = faintText) },
        leadingIcon = { Icon(AllIconsKeys.Actions.Search, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp)) },
    )
}

/** What lets a [ModuleHeader] take part in the list's keyboard navigation (T-5). */
internal class ModuleHeaderKeys(val focus: ListFocusController, val list: ListUi)

/** The band of a definition module (two or more modules only): fold, name, "checked/all" (E-05). */
@Composable
internal fun ModuleHeader(
    header: ListItemUi.ModuleHeader,
    onIntent: (KatachiIntent) -> Unit,
    modifier: Modifier = Modifier,
    keys: ModuleHeaderKeys? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = JewelTheme.globalColors.outlines.focused
    Row(
        modifier.fillMaxWidth()
            .background(bandColor)
            // T-5: the band is a stop of the ↑ ↓ sequence. The pinned copy has no keys: it is only a picture of the band.
            .then(
                if (keys == null) {
                    Modifier
                } else {
                    Modifier.listFocus(FocusTarget.ModuleHeader(header.moduleId), keys.focus, keys.list, onIntent)
                        .onFocusChanged { focused = it.isFocused }
                        .testTag(KatachiTestTags.moduleBand(header.moduleId))
                },
            )
            .then(if (focused) Modifier.border(1.dp, accent) else Modifier)
            .clickable { onIntent(KatachiIntent.ToggleModule(header.moduleId)) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (header.isCollapsed) AllIconsKeys.General.ChevronRight else AllIconsKeys.General.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Text(header.title, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        Text(header.counter, color = faintText, maxLines = 1)
    }
}

/** A group ("data", "domain"): a small header, not indented (spec 02). */
@Composable
internal fun GroupHeader(header: ListItemUi.GroupHeader) {
    Text(
        header.title,
        color = faintText,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}
