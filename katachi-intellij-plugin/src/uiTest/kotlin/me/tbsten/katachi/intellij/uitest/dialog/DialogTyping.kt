package me.tbsten.katachi.intellij.uitest.dialog

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performTextInput
import me.tbsten.katachi.intellij.uitest.ui.inputOf

/**
 * Types [text] into the field tagged [tag] and waits until the dialog has it as the value of [name].
 *
 * The field reports what was typed from an effect (a `snapshotFlow` collector) that can run on the UI thread in the middle of a frame,
 * and `waitForIdle` does not wait for it (KATACHI-312). Pressing Enter or reading what the dialog draws right after `waitForIdle` can then
 * see the state from before the keystroke. So ask the host on the UI thread (the report and the host's redraw are over by the time it
 * answers), then wait for the frame that draws it. Do not call `host.settle()` from the test thread here: it races with the UI thread's own.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.typeAndAwait(host: DialogHost, tag: String, name: String, text: String) {
    val prefix = "input:$name="
    val before = runOnUiThread { host.calls.count { it.startsWith(prefix) } }
    inputOf(tag).performTextInput(text)
    waitUntil(conditionDescription = "the dialog got $name=...$text", timeoutMillis = 5_000) {
        runOnUiThread {
            val reported = host.calls.filter { it.startsWith(prefix) }
            reported.size > before && reported.last().endsWith(text)
        }
    }
    waitForIdle()
}
