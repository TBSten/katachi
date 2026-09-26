package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.CountPopupUi
import me.tbsten.katachi.intellij.presentation.FocusTarget
import me.tbsten.katachi.intellij.presentation.FooterUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.ListUi
import me.tbsten.katachi.intellij.presentation.NavKey
import me.tbsten.katachi.intellij.presentation.navigate
import me.tbsten.katachi.intellij.presentation.SearchUi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * The one screen of the tool window (spec 02, 03): search, the list with the inline forms under
 * checked rows, and the fixed footer. The same order whether docked tall or wide.
 */
@OptIn(ExperimentalFoundationApi::class)
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
                val lazyState = rememberLazyListState()
                VerticallyScrollableContainer(scrollState = lazyState, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = lazyState, modifier = Modifier.fillMaxSize()) {
                        list.items.forEach { item ->
                            when (item) {
                                is ListItemUi.ModuleHeader -> stickyHeader(key = item.key) { ModuleHeader(item, onIntent) }
                                is ListItemUi.GroupHeader -> item(key = item.key) { GroupHeader(item) }
                                is ListItemUi.Row -> item(key = item.key) { TemplateRow(item.row, list, focus, onIntent) }
                            }
                        }
                    }
                }
            }
            list.footer.countPopupOrNull()?.let { popup ->
                CountPopup(popup, onDismiss = { onIntent(KatachiIntent.ToggleFileCountPopup) }, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
        Footer(list.footer, list, focus, onIntent)
    }
}

private fun FooterUi.countPopupOrNull(): CountPopupUi? = (this as? FooterUi.Form)?.countPopup

@Composable
private fun SearchField(search: SearchUi, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    val state = rememberSyncedTextFieldState(search.query) { onIntent(KatachiIntent.Search(it)) }
    TextField(
        state = state,
        enabled = search.enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).listFocus(FocusTarget.Search, focus, list, onIntent),
        placeholder = { Text(search.placeholder, color = faintText) },
        leadingIcon = { Icon(AllIconsKeys.Actions.Search, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp)) },
    )
}

/** The band of a definition module (two or more modules only): fold, name, "checked/all" (E-05). */
@Composable
private fun ModuleHeader(header: ListItemUi.ModuleHeader, onIntent: (KatachiIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
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
private fun GroupHeader(header: ListItemUi.GroupHeader) {
    Text(
        header.title,
        color = faintText,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** Every expected file of the checked templates, above the footer (spec 03 "ファイル数を押すと一覧を出す"). */
@Composable
private fun CountPopup(popup: CountPopupUi, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(8.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .background(JewelTheme.globalColors.panelBackground)
            .border(1.dp, lineColor)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(popup.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            CloseButton(onDismiss)
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            popup.groups.forEach { (name, files) ->
                Text(name, color = faintText)
                files.forEach { ExpectedFileLine(it) }
            }
        }
    }
}
