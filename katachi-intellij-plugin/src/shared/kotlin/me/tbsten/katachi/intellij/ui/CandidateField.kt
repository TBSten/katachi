package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.SuggestionsUi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.Text

/** The tallest the candidate popup grows before it scrolls. */
private val PopupMaxHeight = 240.dp

/**
 * Whether a candidate popup is open anywhere under the screen that provides it. A popup eats Esc and Enter for itself
 * (Esc closes only it, Enter takes the highlighted candidate), so whatever sits above it (the dialog's Esc, the list's Esc and
 * Cmd+Enter) asks here and steps aside while it is open.
 *
 * ```kotlin
 * val popup = CandidatePopupOpen()
 * CompositionLocalProvider(LocalCandidatePopupOpen provides popup) { Screen() }
 * val escapeBelongsToPopup = popup.isOpen
 * ```
 */
@Stable
internal class CandidatePopupOpen {
    private var openCount by mutableIntStateOf(0)

    val isOpen: Boolean get() = openCount > 0

    /** A [CandidateField] opened (`true`) or closed (`false`) its popup. Counted, so that two fields cannot leave it stuck open. */
    fun changed(open: Boolean) {
        openCount = (openCount + if (open) 1 else -1).coerceAtLeast(0)
    }
}

/** The [CandidatePopupOpen] of the screen; `null` outside one (then nobody above the field asks). */
internal val LocalCandidatePopupOpen = compositionLocalOf<CandidatePopupOpen?> { null }

/**
 * The input of a capture that has candidates (design 3.4): a Jewel `EditableComboBox` whose text is [state] as it is in the plain
 * field, and whose popup lists the [suggestions] the typed text is a prefix of (case ignored).
 *
 * - Down opens it (as does the chevron) with the first candidate highlighted; Up / Down move the highlight; Enter takes the highlighted candidate and closes it;
 *   Esc closes only the popup. While it is open Enter generates nothing.
 * - Enter with the popup closed is [onSubmit]: the screen's Generate, if it can.
 * - The "{n} more" line ends the list when candidates were cut off.
 *
 * ```kotlin
 * CandidateField(field.id, state, suggestions, Modifier.testTag("feature"), Outline.None, onSubmit = { generateIfPossible() })
 * ```
 */
@Composable
internal fun CandidateField(
    id: FieldId,
    state: TextFieldState,
    suggestions: SuggestionsUi,
    modifier: Modifier,
    outline: Outline,
    onSubmit: () -> Unit,
) {
    val popup = LocalCandidatePopupOpen.current
    val latestPopup = rememberUpdatedState(popup)
    val manager = remember { PopupManager(onPopupVisibleChange = { latestPopup.value?.changed(it) }) }
    DisposableEffect(manager) {
        onDispose { if (manager.isPopupVisible.value) latestPopup.value?.changed(false) }
    }
    val typed = state.text.toString()
    val shown = remember(suggestions.values, typed) { suggestions.values.filter { it.startsWith(typed, ignoreCase = true) } }
    var highlighted by remember { mutableIntStateOf(NONE) }
    val open by manager.isPopupVisible
    // An open popup starts on the candidate the text is, else on the first one; what it lists changing starts it again (design 3.4:
    // Down opens, the next Down moves to the second).
    LaunchedEffect(open, shown, typed) { highlighted = if (open && shown.isNotEmpty()) shown.indexOf(typed).coerceAtLeast(0) else NONE }
    // Nothing to offer for what is typed: no empty popup.
    LaunchedEffect(open, shown.isEmpty()) { if (open && shown.isEmpty()) manager.setPopupVisible(false) }

    fun pick(value: String) {
        state.setTextAndPlaceCursorAtEnd(value)
        manager.setPopupVisible(false)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(highlighted) { if (highlighted >= 0) listState.scrollToItem(highlighted) }

    // The combo takes Enter whatever it holds; the open popup's Enter is settled here, before it, and a closed one is left to
    // it, which calls [onSubmit].
    val enterOfPopup = Modifier.onPreviewKeyEvent { event ->
        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!isEnter || event.type != KeyEventType.KeyDown || !manager.isPopupVisible.value) return@onPreviewKeyEvent false
        shown.getOrNull(highlighted)?.let(::pick) ?: manager.setPopupVisible(false)
        true
    }
    // The popup is as wide as the field (design 3.4): Jewel does not narrow a custom popup content to the combo box.
    val density = LocalDensity.current
    var fieldWidth by remember { mutableStateOf(0.dp) }
    EditableComboBox(
        textFieldState = state,
        modifier = modifier.then(enterOfPopup).onSizeChanged { fieldWidth = with(density) { it.width.toDp() } },
        popupModifier = Modifier.heightIn(max = PopupMaxHeight),
        outline = outline,
        onArrowDownPress = { highlighted = (highlighted + 1).coerceAtMost(shown.lastIndex) },
        onArrowUpPress = { highlighted = (highlighted - 1).coerceAtLeast(0) },
        onEnterPress = onSubmit,
        popupManager = manager,
    ) {
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = PopupMaxHeight).then(if (fieldWidth > 0.dp) Modifier.width(fieldWidth) else Modifier)) {
            itemsIndexed(shown, key = { _, value -> value }) { index, value ->
                CandidateRow(value, selected = index == highlighted, id) { pick(value) }
            }
            suggestions.moreLabel?.let { label ->
                item(key = MoreKey) {
                    Text(label, color = faintText, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).testTag(KatachiTestTags.candidateMore(id)))
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(value: String, selected: Boolean, id: FieldId, onPick: () -> Unit) {
    val highlight = if (selected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.3f) else Color.Transparent
    Text(
        value,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().background(highlight).clickable(onClick = onPick)
            .padding(horizontal = 8.dp, vertical = 3.dp).testTag(KatachiTestTags.candidate(id, value)),
    )
}

private const val NONE = -1

/** The lazy list key of the "{n} more" line; an object, so that no candidate value can collide with it. */
private data object MoreKey
