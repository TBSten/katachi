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
    val sync = remember { TextFieldSync(value) }
    val latestOnChange = rememberUpdatedState(onChange)
    LaunchedEffect(value) {
        sync.onValue(value, state.text.toString())?.let { state.setTextAndPlaceCursorAtEnd(it) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (sync.onTyped(text)) latestOnChange.value(text)
        }
    }
    return state
}

/**
 * Keeps a text field and the value the UI state holds for it in step, without Compose.
 *
 * What the field reports is compared with the last text this field and the state agreed on, not
 * with the latest value the composition saw: that one lags a frame behind, so typing a letter and
 * deleting it before the next frame would look like "no change" and leave the state on the letter
 * (the search stuck on a query the empty field no longer shows).
 *
 * ```kotlin
 * val sync = TextFieldSync(initial = "")
 * if (sync.onTyped("r")) send("r")      // the field changed
 * sync.onValue("r", fieldText = "r")    // null: only the echo of "r"
 * ```
 */
internal class TextFieldSync(initial: String) {
    private var lastKnown = initial
    private val echoes = EchoTracker()

    /** The field now holds [text]; `true` when it is new and must be reported. */
    fun onTyped(text: String): Boolean {
        if (text == lastKnown) return false
        lastKnown = text
        echoes.sent(text)
        return true
    }

    /** The state now holds [value]; returns the text to write into the field, or `null` to leave it. */
    fun onValue(value: String, fieldText: String): String? {
        if (echoes.isEcho(value)) return null
        lastKnown = value
        return value.takeIf { it != fieldText }
    }
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
