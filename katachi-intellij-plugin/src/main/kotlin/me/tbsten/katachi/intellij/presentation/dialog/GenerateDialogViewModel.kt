package me.tbsten.katachi.intellij.presentation.dialog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import java.nio.file.Path

/**
 * The generate dialog's ViewModel, IntelliJ-free (issue 9), so the dialog's behaviour is tested
 * without an IDE: template and definition selection, the form with the decided captures as editable
 * initial values (issue 1), the target path, the existing-file notice (issue 6) and whether
 * [Generate] can be pressed. Driven from the EDT; [checkTarget] runs off it, debounced.
 *
 * ```kotlin
 * val vm = GenerateDialogViewModel(scope, request, candidates, seeds, generation::checkTarget)
 * vm.dispatch(GenerateDialogIntent.Input("name", "Profile"))
 * vm.generationRequest() // one template (issue 2), or null while something required is missing
 * ```
 */
internal class GenerateDialogViewModel(
    private val scope: CoroutineScope,
    request: GenerateDialogRequest,
    /** Every template of every definition, in list order. */
    private val candidates: List<ModuleTemplate>,
    private val seeds: CaptureSeedPort,
    /** `SingleFileGeneration.checkTarget`: the same check [Generate] runs (issue 6). */
    private val checkTarget: suspend (Path?) -> TargetState,
) {
    private val mutableState = MutableStateFlow(
        GenerateDialogState(request, candidates, request.initialTemplate),
    )

    val state: StateFlow<GenerateDialogState> = mutableState.asStateFlow()

    fun dispatch(intent: GenerateDialogIntent) {
        // TODO(D1)
    }

    /** What [Generate] runs: the selected template with the inputs; `null` while it cannot be pressed. */
    fun generationRequest(): SingleFileGenerationRequest? {
        // TODO(D1)
        return null
    }
}
