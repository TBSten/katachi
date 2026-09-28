package me.tbsten.katachi.intellij.ui.dialog

/**
 * What the generate dialog's Composable draws: plain values, no IntelliJ API (issue 9), so the preview
 * and uiTest render it on standalone Compose. D1 maps its ViewModel state to it; D2 adds what the
 * form, the notices and the validation errors need.
 */
internal data class GenerateDialogUiState(
    /** Every template (issue 0), shown even when there is one. */
    val templateOptions: List<String>,
    val selectedTemplate: Int,
    /** Empty: no definition select box (fewer than two definitions matched, issue 7). */
    val definitionOptions: List<String> = emptyList(),
    val selectedDefinition: Int = 0,
    /** The path the current inputs produce, unfilled captures as placeholders. */
    val targetPath: String = "",
    val canGenerate: Boolean = false,
)

/**
 * What the dialog's parts report. Callbacks rather than an intent type, so that the Composable in
 * `src/shared` does not depend on the ViewModel's intents in `presentation/dialog`; D1 fixes the
 * callback shape of the fields (it also frees `ParameterField` from `KatachiIntent`) and D4 wires them.
 */
internal interface GenerateDialogActions {
    fun onSelectTemplate(index: Int)

    fun onSelectDefinition(index: Int)

    /** Enter in the form, like the OK button. */
    fun onGenerate()
}
