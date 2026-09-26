package me.tbsten.katachi.intellij.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import me.tbsten.katachi.intellij.presentation.FocusMove
import me.tbsten.katachi.intellij.presentation.FocusTarget
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.ListUi
import me.tbsten.katachi.intellij.presentation.NavKey
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.firstEmptyRequiredOf
import me.tbsten.katachi.intellij.presentation.navigate

/**
 * Carries out the list's keyboard rules (presentation/KeyboardNavigation.kt): one FocusRequester
 * per target, and a focus move waiting for the next state when the target is not composed yet.
 */
@Stable
internal class ListFocusController {
    private val requesters = HashMap<FocusTarget, FocusRequester>()

    var pending: FocusMove? by mutableStateOf(null)

    fun requesterOf(target: FocusTarget): FocusRequester = requesters.getOrPut(target) { FocusRequester() }

    /** Tries [move] now against [list]; keeps it pending when its target is not there yet. */
    fun move(move: FocusMove, list: ListUi) {
        val target = when (move) {
            is FocusMove.To -> move.target
            is FocusMove.FirstEmptyRequired -> {
                val form = list.items.firstNotNullOfOrNull { item ->
                    ((item as? ListItemUi.Row)?.row?.takeIf { it.id == move.id }?.body as? RowBodyUi.Form)?.form
                }
                form?.let(::firstEmptyRequiredOf)?.let { FocusTarget.Field(it) }
            }
        }
        pending = if (target != null && tryFocus(target)) null else move
    }

    private fun tryFocus(target: FocusTarget): Boolean {
        val requester = requesters[target] ?: return false
        // Throws when the target is not attached yet (a row scrolled out, a form not composed).
        return runCatching { requester.requestFocus() }.isSuccess
    }
}

@Composable
internal fun rememberListFocusController(list: ListUi): ListFocusController {
    val controller = remember { ListFocusController() }
    val pending = controller.pending
    LaunchedEffect(list, pending) {
        if (pending != null) controller.move(pending, list)
    }
    return controller
}

/** Attaches [target]'s FocusRequester and its key handling. */
internal fun Modifier.listFocus(
    target: FocusTarget,
    controller: ListFocusController,
    list: ListUi,
    onIntent: (KatachiIntent) -> Unit,
): Modifier = focusRequester(controller.requesterOf(target)).onPreviewKeyEvent { event ->
    val key = navKeyOf(event, target) ?: return@onPreviewKeyEvent false
    val result = navigate(list, target, key)
    result.intent?.let(onIntent)
    result.focus?.let { controller.move(it, list) }
    result.isHandled
}

internal fun isGenerateShortcut(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
        (event.isMetaPressed || event.isCtrlPressed)

private fun navKeyOf(event: KeyEvent, target: FocusTarget): NavKey? {
    if (event.type != KeyEventType.KeyDown) return null
    val primary = event.isMetaPressed || event.isCtrlPressed
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> if (primary) NavKey.Generate else NavKey.Enter
        Key.DirectionUp -> NavKey.Up
        Key.DirectionDown -> NavKey.Down
        Key.DirectionRight -> NavKey.Right
        Key.DirectionLeft -> NavKey.Left
        Key.Spacebar -> NavKey.Space
        Key.Escape -> NavKey.Escape
        else -> {
            // Speed search: only a row hands printable characters to the search field.
            val code = event.utf16CodePoint
            if (target is FocusTarget.Row && !primary && code > ' '.code) NavKey.Type(code.toChar().toString()) else null
        }
    }
}
