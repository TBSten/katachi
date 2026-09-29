package me.tbsten.katachi.intellij.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.ui.KatachiTestTags
import me.tbsten.katachi.intellij.ui.ParameterField
import me.tbsten.katachi.intellij.ui.ParameterFieldCallbacks
import me.tbsten.katachi.intellij.ui.errorText
import me.tbsten.katachi.intellij.ui.faintText
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/** Narrower than this the labels sit above their fields; side by side would leave the field too narrow. */
private val SideBySideMinWidth = 300.dp

/** The label column takes this share of the width (clamped), so it stretches with the window. */
private const val LABEL_SHARE = 0.26f
private val LabelMinWidth = 96.dp
private val LabelMaxWidth = 150.dp

/**
 * The generate dialog's content: template select, definition select (two or more definitions),
 * the form, the target path and the existing-file notice, stacked so that it adapts to the window
 * size (decision 19: Compose instead of `JBUI.Panel`). `DialogWrapper` (D4) supplies the buttons.
 *
 * The label column is a share of the width, not a fixed one; the form scrolls when it is taller than
 * the window while the selects and the target path stay in view. The cursor starts in the first
 * required field with nothing in it, and Enter that no field consumed generates (like the OK button).
 *
 * ```kotlin
 * JewelComposePanel { GenerateDialogContent(uiState, strings, actions) }
 * ```
 */
@Composable
internal fun GenerateDialogContent(
    state: GenerateDialogUiState,
    strings: GenerateDialogStrings,
    actions: GenerateDialogActions,
    modifier: Modifier = Modifier,
) {
    val callbacks = remember(actions) { dialogFieldCallbacks(actions) }
    val initialFocus = remember { FocusRequester() }
    val initialFocusField = state.firstEmptyRequired
    // Again when another template is chosen: its first empty field takes the cursor.
    LaunchedEffect(state.selectedTemplate, state.selectedDefinition) {
        if (initialFocusField != null) initialFocus.requestFocusWhenReady()
    }
    BoxWithConstraints(
        modifier.fillMaxSize().onKeyEvent { event ->
            val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
            if (isEnter && event.type == KeyEventType.KeyDown && state.canGenerate) {
                actions.onGenerate()
                true
            } else {
                false
            }
        },
    ) {
        val labelWidth = if (maxWidth < SideBySideMinWidth) null else (maxWidth * LABEL_SHARE).coerceIn(LabelMinWidth, LabelMaxWidth)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.listNotice?.let { ListNotice(it, state, strings) }
            LabeledSelect(
                strings.templateLabel,
                labelWidth,
                state.templateOptions,
                state.selectedTemplate,
                actions::onSelectTemplate,
                Modifier.testTag(KatachiTestTags.DIALOG_TEMPLATE),
            )
            if (state.definitionOptions.isNotEmpty()) {
                LabeledSelect(
                    strings.definitionLabel,
                    labelWidth,
                    state.definitionOptions,
                    state.selectedDefinition,
                    actions::onSelectDefinition,
                    Modifier.testTag(KatachiTestTags.DIALOG_DEFINITION),
                )
            }
            state.summary?.let { Text(it, color = faintText) }
            Form(state, labelWidth, callbacks, initialFocusField, initialFocus, Modifier.weight(1f, fill = false))
            TargetPath(state, strings, labelWidth)
        }
    }
}

@Composable
private fun ListNotice(notice: ListNoticeUi, state: GenerateDialogUiState, strings: GenerateDialogStrings) {
    val text = when (notice) {
        is ListNoticeUi.TemplateReplaced -> strings.templateRemoved(notice.switchedTo)
        ListNoticeUi.NoCandidates -> strings.noTemplates
    }
    val color = if (notice == ListNoticeUi.NoCandidates && state.templateOptions.isEmpty()) errorText else faintText
    Text(text, color = color, modifier = Modifier.fillMaxWidth().testTag(KatachiTestTags.DIALOG_LIST_NOTICE))
}

/** The fields, scrolling on their own so that the rest of the dialog keeps its place. */
@Composable
private fun Form(
    state: GenerateDialogUiState,
    labelWidth: Dp?,
    callbacks: ParameterFieldCallbacks,
    initialFocusField: FieldId?,
    initialFocus: FocusRequester,
    modifier: Modifier,
) {
    if (state.fields.isEmpty()) return
    val scroll = rememberScrollState()
    VerticallyScrollableContainer(scrollState = scroll, modifier = modifier.fillMaxWidth().testTag(KatachiTestTags.DIALOG_FORM)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.fields.forEach { field ->
                val fieldModifier = if (field.id == initialFocusField) Modifier.focusRequester(initialFocus) else Modifier
                ParameterField(field, labelWidth, callbacks, fieldModifier)
            }
        }
    }
}

/** The path the inputs produce, under it the notice about what is there already, and a refused generation. */
@Composable
private fun TargetPath(state: GenerateDialogUiState, strings: GenerateDialogStrings, labelWidth: Dp?) {
    LabeledContent(strings.targetPathLabel, labelWidth) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Drawn with break hints, read (tests, accessibility) as the path itself.
            Box(Modifier.fillMaxWidth().testTag(KatachiTestTags.DIALOG_TARGET_PATH).semantics { text = AnnotatedString(state.targetPath) }) {
                Text(breakableAtPlaceholders(state.targetPath), modifier = Modifier.fillMaxWidth().clearAndSetSemantics { })
            }
            state.targetNotice?.let { notice ->
                val (text, color) = when (notice) {
                    TargetNoticeUi.WillCreate -> strings.targetNew to faintText
                    TargetNoticeUi.WillOverwriteEmpty -> strings.targetEmpty to faintText
                    TargetNoticeUi.CannotOverwrite -> strings.targetHasContent to errorText
                }
                Text(text, color = color, modifier = Modifier.fillMaxWidth().testTag(KatachiTestTags.DIALOG_TARGET_NOTICE))
            }
            state.refusal?.let {
                Text(strings.generateRefused(it), color = errorText, modifier = Modifier.fillMaxWidth().testTag(KatachiTestTags.DIALOG_REFUSAL))
            }
        }
    }
}

/** The dialog's fields report through the dialog's actions; it has no multi-line mode or links yet. */
private fun dialogFieldCallbacks(actions: GenerateDialogActions) = object : ParameterFieldCallbacks {
    override fun onInput(field: FieldId, value: String) = actions.onInput(field.parameterName, value)

    // TODO(dialog-multiline): the dialog keeps no multi-line state; every String field is one line.
    override fun onToggleMultiline(field: FieldId) = Unit

    // The dialog shows no links (linked fields are a tool window notion), so there is nothing to relink.
    override fun onRelink(field: FieldId) = Unit
}

/**
 * Asks for the focus once per frame until it is given (at most [attempts] frames): the field is not
 * in the focus tree before its first layout, and the window may not have the focus yet when the
 * dialog opens. Gives up quietly; the cursor then just starts where the platform puts it.
 */
private suspend fun FocusRequester.requestFocusWhenReady(attempts: Int = 30) {
    repeat(attempts) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.getOrDefault(false)) return
    }
}

/**
 * [path] with a word joiner inside each `${` so that a long path wraps at its `/`s and never between the
 * `$` and the `{` of a placeholder (the line breaker allows a break there). Only what is drawn changes.
 */
private fun breakableAtPlaceholders(path: String): String = path.replace("\${", "$\u2060{")
