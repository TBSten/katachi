package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
internal fun TemplateListPane(list: ListUi, onIntent: (KatachiIntent) -> Unit) {
    val focus = rememberListFocusController(list)
    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            // ⌘⏎ / Ctrl+Enter from anywhere in the tool window; a disabled Generate still eats the key.
            if (!isGenerateShortcut(event)) return@onPreviewKeyEvent false
            navigate(list, FocusTarget.Search, NavKey.Generate).intent?.let(onIntent)
            true
        },
    ) {
        list.refresh?.let { RefreshBar(it, onIntent) }
        list.banners.forEach { Banner(it, onIntent) }
        SearchField(list.search, list, focus, onIntent)
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

@Composable
private fun SearchField(search: SearchUi, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    val state = rememberSyncedTextFieldState(search.query) { onIntent(KatachiIntent.Search(it)) }
    TextField(
        state = state,
        enabled = search.enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).listFocus(FocusTarget.Search, focus, list, onIntent)
            .testTag(KatachiTestTags.SEARCH),
        placeholder = { Text(search.placeholder, color = faintText) },
        leadingIcon = { Icon(AllIconsKeys.Actions.Search, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp)) },
    )
}

/** The band of a definition module (two or more modules only): fold, name, "checked/all" (E-05). */
@Composable
internal fun ModuleHeader(header: ListItemUi.ModuleHeader, onIntent: (KatachiIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .background(bandColor)
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
