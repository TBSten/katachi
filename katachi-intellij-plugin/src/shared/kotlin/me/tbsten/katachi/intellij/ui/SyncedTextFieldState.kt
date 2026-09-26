package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/**
 * A TextFieldState mirroring [value] from the UI state: typing reports through [onChange], and a
 * value changed from outside (a linked field, "generate more" clearing it) is written back in.
 *
 * A [value] that only echoes what this field reported is never written back: by the time it
 * arrives the field may hold a newer keystroke, or an IME composition, that writing would undo.
 */
@Composable
internal fun rememberSyncedTextFieldState(value: String, onChange: (String) -> Unit): TextFieldState {
    val state = remember { TextFieldState(value) }
    val echoes = remember { EchoTracker() }
    val latestValue = rememberUpdatedState(value)
    val latestOnChange = rememberUpdatedState(onChange)
    LaunchedEffect(value) {
        if (echoes.isEcho(value)) return@LaunchedEffect
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != latestValue.value) {
                echoes.sent(text)
                latestOnChange.value(text)
            }
        }
    }
    return state
}

/**
 * The texts a field reported that have not come back as its value yet. Values come back in the
 * order they were sent, so one coming back retires it and every older one.
 */
internal class EchoTracker {
    private val pending = ArrayDeque<String>()

    fun sent(text: String) {
        pending.addLast(text)
        // A value the state refused never comes back; do not let those pile up.
        while (pending.size > MAX_PENDING) pending.removeFirst()
    }

    /** Whether [value] is one this field sent; retires it and every older one. */
    fun isEcho(value: String): Boolean {
        val index = pending.indexOf(value)
        if (index < 0) {
            pending.clear()
            return false
        }
        repeat(index + 1) { pending.removeFirst() }
        return true
    }

    private companion object {
        const val MAX_PENDING = 32
    }
}
