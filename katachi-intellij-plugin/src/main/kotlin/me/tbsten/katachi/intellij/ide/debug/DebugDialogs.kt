package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogIntent

/**
 * What a user does in the generate dialog on screen (typing into a field, pressing Generate or Cancel), done inside the IDE for
 * the Driver smoke. The dialog is modal and one `ComposePanel`, so Driver's own input cannot reach its fields; these send the
 * same intents the panel would. Every call runs with `ModalityState.any()`, the dialog being modal.
 *
 * ```kotlin
 * DebugDialogs().fillDialog("name", "Probe")   // "filled=name"
 * DebugDialogs().pressDialog("generate")       // "pressed=generate", or "pressed=none" while Generate is disabled
 * ```
 */
internal class DebugDialogs {
    /** Types [value] into the field [name] of the dialog on screen: `filled=<name>`, or `filled=none` when no dialog is up or it has no such field. */
    fun fillDialog(name: String, value: String): String = onEdt("fill the field $name of the generate dialog", "filled=none") {
        val dialog = GenerateDialogs.showing
        if (dialog == null || dialog.viewModel.state.value.fields.none { it.name == name }) {
            "filled=none"
        } else {
            // TODO(dialog-input): this skips KatachiGenerateDialog.changed (it clears refusalState), so a refusal stays in describeDialog
            // after a refill. Until an internal input door that goes through actions.onInput is added to KatachiGenerateDialog, refusal is
            // not cleared here. L-S (stage S) adds the door and swaps this for it, with a DebugBridgeDialogInputTest case
            // "refilled after a refusal -> describeDialog has no refusal=".
            dialog.viewModel.dispatch(GenerateDialogIntent.Input(name, value))
            "filled=$name"
        }
    }

    /**
     * Presses `generate` (only while the dialog's OK is enabled) or `cancel` of the dialog on screen: `pressed=generate` or
     * `pressed=cancel`; `pressed=none` for any other button, no dialog, or a disabled Generate.
     */
    fun pressDialog(button: String): String = onEdt("press $button of the generate dialog", "pressed=none") {
        val dialog = GenerateDialogs.showing
        when {
            dialog == null -> "pressed=none"
            button == GENERATE && dialog.isOKActionEnabled -> "pressed=$GENERATE".also { dialog.pressGenerate() }
            button == CANCEL -> "pressed=$CANCEL".also { dialog.pressCancel() }
            else -> "pressed=none"
        }
    }

    private fun onEdt(action: String, failed: String, block: () -> String): String {
        var answer = failed
        ApplicationManager.getApplication().invokeAndWait({ answer = sdkCall(action, block).getOrDefault(failed) }, ModalityState.any())
        return answer
    }

    private companion object {
        const val GENERATE = "generate"
        const val CANCEL = "cancel"
    }
}
