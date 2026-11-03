package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.ide.sdkCall

internal class DebugDialogs {
    fun fillDialog(name: String, value: String): String = onEdt("fill the field $name of the generate dialog", "filled=none") {
        val dialog = GenerateDialogs.showing
        if (dialog == null || dialog.viewModel.state.value.fields.none { it.name == name }) {
            "filled=none"
        } else {
            // The panel's own input path: it clears a refusal left by a Generate that stopped.
            dialog.input(name, value)
            "filled=$name"
        }
    }

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
