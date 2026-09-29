package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.ListUi
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/**
 * The scrolling part of the list: every entry composed at once in a Column, not a LazyColumn.
 * Templates number in the tens, and a lazy list measures its items with an unbounded height,
 * which Jewel's TextArea cannot take. [key] keeps each row's remembered state (its text fields)
 * with the row when the search or a reload moves it.
 *
 * The module band of the section scrolled past stays pinned to the top, pushed up by the next
 * band, as the LazyColumn's sticky header did (spec 02 "見出し").
 *
 * TODO: every row recomposes on each keystroke (rows take the whole [ListUi]). Measured headless
 *  with software rendering, a keystroke costs about 42 ms at 200 templates and 54 ms at 500,
 *  against 16-20 ms with the LazyColumn. Pass the list to rows through a stable holder and mark
 *  the UI models immutable if E-29's "200 templates" turns out to lag.
 */
@Composable
internal fun ListBody(list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    val scrollState = rememberScrollState()
    // Created with the list: a tool window created after [View template] still scrolls to the row once.
    val reveal = remember { RevealTracker() }
    // Where each module band sits in the content, for the pinned copy.
    val bandTops = remember { mutableStateMapOf<ModuleId, Int>() }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        VerticallyScrollableContainer(scrollState = scrollState, modifier = Modifier.fillMaxSize().testTag(KatachiTestTags.LIST)) {
            Column(Modifier.fillMaxWidth()) {
                list.items.forEach { item ->
                    key(item.key) {
                        when (item) {
                            is ListItemUi.ModuleHeader -> ModuleHeader(
                                item,
                                onIntent,
                                Modifier.onPlaced { bandTops[item.moduleId] = it.positionInParent().y.toInt() },
                            )
                            is ListItemUi.GroupHeader -> GroupHeader(item)
                            is ListItemUi.Row -> TemplateRow(item.row, list, focus, reveal, onIntent)
                        }
                    }
                }
            }
        }
        PinnedModuleBand(list, scrollState, bandTops, onIntent)
    }
}

/** The band whose section the top edge is in, drawn over the list once its own band scrolled away. */
@Composable
private fun PinnedModuleBand(list: ListUi, scrollState: ScrollState, bandTops: Map<ModuleId, Int>, onIntent: (KatachiIntent) -> Unit) {
    var height by remember { mutableIntStateOf(0) }
    val pinned by remember(list) {
        derivedStateOf { pinnedBandOf(list.items.filterIsInstance<ListItemUi.ModuleHeader>(), bandTops, scrollState.value) }
    }
    val band = pinned ?: return
    // The next band pushes this one up instead of sliding under it.
    val push = band.nextTop?.let { (it - scrollState.value - height).coerceAtMost(0) } ?: 0
    ModuleHeader(
        band.header,
        onIntent,
        Modifier.onSizeChanged { height = it.height }.offset { IntOffset(0, push) },
    )
}

private data class PinnedBand(val header: ListItemUi.ModuleHeader, val nextTop: Int?)

/** The last band above the scroll position, when it is strictly above: at 0 the band itself shows. */
private fun pinnedBandOf(headers: List<ListItemUi.ModuleHeader>, tops: Map<ModuleId, Int>, scroll: Int): PinnedBand? {
    val placed = headers.mapNotNull { header -> tops[header.moduleId]?.let { header to it } }
    val index = placed.indexOfLast { (_, top) -> top < scroll }
    if (index < 0) return null
    return PinnedBand(placed[index].first, placed.getOrNull(index + 1)?.second)
}

/**
 * The [View template] requests (their numbers) the list already scrolled to, so that a highlighted
 * row coming back into the composition (a module band folded and unfolded) does not pull the list
 * to it again. Not state: nothing is drawn from it.
 */
internal class RevealTracker {
    private var served: Int? = null

    /** Whether [request] is new; it is served from then on. */
    fun take(request: Int): Boolean {
        if (served == request) return false
        served = request
        return true
    }
}
