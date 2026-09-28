package me.tbsten.katachi.intellij.uitest.dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.dialog.CaptureSeedPort
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogIntent
import me.tbsten.katachi.intellij.presentation.dialog.GenerateDialogViewModel
import me.tbsten.katachi.intellij.presentation.dialog.dialogUiStateOf
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.newMenuDirectory
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogActions
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogStrings
import me.tbsten.katachi.intellij.ui.dialog.GenerateDialogUiState
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings

/**
 * The real [GenerateDialogViewModel] behind the dialog's Composable, wired the way the dialog frame
 * (D4) wires it: an action dispatches the intent, and the state the Composable draws is the mapper's
 * output of the ViewModel's state. The ViewModel's scope and existing-file check run on
 * [ManualDispatcher]s that [settle] drains, so no test waits in real time.
 *
 * ```kotlin
 * val host = DialogHost(templates, initial = screen, seeds = mapOf("feature" to "home"))
 * setContent { GenerateDialogContent(host.ui, host.strings, host.actions) }
 * ```
 */
internal class DialogHost(
    templates: List<ModuleTemplate>,
    initial: ModuleTemplate = templates.first(),
    seeds: Map<String, String> = emptyMap(),
    origin: EntryOrigin = newMenuDirectory("feature/home"),
    seedsOf: (ModuleTemplate) -> Map<String, String> = { emptyMap() },
    val strings: GenerateDialogStrings = PropertiesGenerateDialogStrings.english(),
) {
    private val edt = ManualDispatcher()
    private val background = ManualDispatcher()
    private val templates = templates.associateBy { it.id }
    private val scope = CoroutineScope(edt + Job())

    val viewModel = GenerateDialogViewModel(
        scope = scope,
        request = GenerateDialogRequest(origin, initial.id, seeds),
        candidates = templates,
        seeds = CaptureSeedPort { _, template -> this.templates[template]?.let(seedsOf).orEmpty() },
        checkTarget = { TargetState.Absent },
        rootOf = { it.linkedRootPath },
        checkContext = background,
        settle = {},
    )

    /** What [GenerateDialogUiState] the Composable is drawing now. */
    var ui: GenerateDialogUiState by mutableStateOf(mapped())
        private set

    /** The requests of every Enter that reached the dialog's generate action (`null`: it could not be pressed). */
    val generated = mutableListOf<SingleFileGenerationRequest?>()

    /** The order the parts reported in: `template:<index>`, `definition:<index>`, `input:<name>=<value>`, `generate`. */
    val calls = mutableListOf<String>()

    val actions = object : GenerateDialogActions {
        override fun onSelectTemplate(index: Int) {
            calls += "template:$index"
            viewModel.state.value.candidates.getOrNull(index)?.let { send(GenerateDialogIntent.SelectTemplate(it.id)) }
        }

        override fun onSelectDefinition(index: Int) {
            calls += "definition:$index"
            viewModel.state.value.definitions.getOrNull(index)?.let { send(GenerateDialogIntent.SelectDefinition(it.id)) }
        }

        override fun onInput(name: String, value: String) {
            calls += "input:$name=$value"
            send(GenerateDialogIntent.Input(name, value))
        }

        override fun onGenerate() {
            calls += "generate"
            generated += viewModel.generationRequest()
        }
    }

    private fun send(intent: GenerateDialogIntent) {
        viewModel.dispatch(intent)
        settle()
    }

    /** Lets the existing-file check answer and redraws from the ViewModel's state. */
    fun settle() {
        while (edt.pending + background.pending > 0) {
            edt.runAll()
            background.runAll()
        }
        ui = mapped()
    }

    private fun mapped(): GenerateDialogUiState = dialogUiStateOf(viewModel.state.value, strings)
}
