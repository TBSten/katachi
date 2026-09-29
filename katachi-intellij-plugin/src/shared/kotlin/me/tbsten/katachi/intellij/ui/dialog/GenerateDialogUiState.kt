package me.tbsten.katachi.intellij.ui.dialog

import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FieldUi

/**
 * What the generate dialog's Composable draws: plain values, no IntelliJ API (issue 9), so the preview
 * and uiTest render it on standalone Compose. `dialogUiStateOf` maps the ViewModel's state to it.
 */
internal data class GenerateDialogUiState(
    /** Every template (issue 0), shown even when there is one. */
    val templateOptions: List<String>,
    val selectedTemplate: Int,
    /** Empty: no definition select box (fewer than two definitions matched, issue 7). */
    val definitionOptions: List<String> = emptyList(),
    val selectedDefinition: Int = 0,
    /** The selected template's one-line summary, when it has one. */
    val summary: String? = null,
    /** The selected template's fields in form order; empty for a template without parameters. */
    val fields: List<FieldUi> = emptyList(),
    /** The path the current inputs produce, unfilled captures as placeholders. */
    val targetPath: String = "",
    /** The existing-file notice (issue 6); `null` while unknown or when katachi decides on its own. */
    val targetNotice: TargetNoticeUi? = null,
    /** What changed in the template list while the dialog was open. */
    val listNotice: ListNoticeUi? = null,
    /** Why the pre-check of Generate refused (E3); the dialog stays open showing it. */
    val refusal: String? = null,
    val canGenerate: Boolean = false,
) {
    /** The first required field with nothing in it: where the cursor starts. */
    val firstEmptyRequired: FieldId?
        get() = fields.firstOrNull {
            when (it) {
                is FieldUi.Text -> it.isRequired && it.value.isEmpty()
                is FieldUi.Choice -> it.isRequired && it.selectedIndex < 0
                is FieldUi.Bool, is FieldUi.Collapsed -> false
            }
        }?.id
}

/** The three existing-file notices of issue 6. */
internal enum class TargetNoticeUi {
    WillCreate,
    WillOverwriteEmpty,
    CannotOverwrite,
}

/** The two things the template list can do to the open dialog. */
internal sealed interface ListNoticeUi {
    /** The selected template is gone; [switchedTo] (a title) is selected instead. */
    data class TemplateReplaced(val switchedTo: String) : ListNoticeUi

    /** The definition has no template any more. */
    data object NoCandidates : ListNoticeUi
}

/**
 * What the dialog's parts report. Callbacks rather than an intent type, so that the Composable in
 * `src/shared` does not depend on the ViewModel's intents in `presentation/dialog`; D4 wires them.
 */
internal interface GenerateDialogActions {
    fun onSelectTemplate(index: Int)

    fun onSelectDefinition(index: Int)

    /** A field's text (or a Boolean / enum choice) changed. */
    fun onInput(name: String, value: String)

    /** Enter in the form, like the OK button. */
    fun onGenerate()
}
