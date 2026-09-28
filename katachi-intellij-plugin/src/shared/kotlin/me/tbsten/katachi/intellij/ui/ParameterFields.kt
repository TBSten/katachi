package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.LinkUi
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Roughly how wide one character of a parameter name is, to tell whether it fits its label column. */
private const val LABEL_CHAR_WIDTH_DP = 8.2f

/** The label column of a wide form; narrower forms use a smaller one. */
internal val WideLabelWidth = 120.dp

/** The ▸ of String fields; other fields keep the slot empty so that right edges line up. */
private val ToggleSlot = 16.dp

/** The tallest a multi-line String field grows before it scrolls. */
private val MultilineMaxHeight = 160.dp

/** 🔗 / unlink always takes this slot, so linked and unlinked fields line up. */
private val LinkSlot = 18.dp

/** What a field keeps free at its right edge (the ▸ and 🔗 slots); a form's other rows keep it too, to line up. */
internal val FieldTrailingWidth = ToggleSlot + LinkSlot

/**
 * What a field reports. The tool window turns each call into its `KatachiIntent`, the generate
 * dialog into its `GenerateDialogIntent`; the field itself knows neither, nor the list's focus.
 *
 * ```kotlin
 * val callbacks = object : ParameterFieldCallbacks {
 *     override fun onInput(field: FieldId, value: String) = viewModel.dispatch(Input(field.parameterName, value))
 *     override fun onToggleMultiline(field: FieldId) = Unit
 *     override fun onRelink(field: FieldId) = Unit
 * }
 * ```
 */
internal interface ParameterFieldCallbacks {
    fun onInput(field: FieldId, value: String)

    fun onToggleMultiline(field: FieldId)

    fun onRelink(field: FieldId)
}

/**
 * One line of the form: label (with `*` when required), the widget of its type, 🔗, and under it
 * the error (spec 03 "入力部品は型で決める"). [fieldModifier] is the host's focus and key handling
 * of the widget: the tool window's `Modifier.listFocus(...)`, the dialog's `FocusRequester`.
 *
 * ```kotlin
 * ParameterField(field, labelWidth = 120.dp, callbacks, fieldModifier = Modifier.focusRequester(requester))
 * ```
 */
@Composable
internal fun ParameterField(field: FieldUi, labelWidth: Dp?, callbacks: ParameterFieldCallbacks, fieldModifier: Modifier = Modifier) {
    if (field is FieldUi.Collapsed) {
        CollapsedField(field)
        return
    }
    val label = if (field.isRequired) "${field.label} *" else field.label
    val focusModifier = fieldModifier.testTag(KatachiTestTags.field(field.id))
    val below: @Composable () -> Unit = {
        (field as? FieldUi.Text)?.hint?.let { Text(it, color = faintText, modifier = Modifier.testTag(KatachiTestTags.hint(field.id))) }
        errorOf(field)?.let { Text(it, color = errorText) }
    }
    // A name longer than the label column goes above its field rather than being cut.
    val sideWidth = labelWidth?.takeIf { label.length <= (it.value / LABEL_CHAR_WIDTH_DP).toInt() }
    if (sideWidth != null) {
        Row(verticalAlignment = Alignment.Top) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(sideWidth).padding(top = 6.dp, end = 6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FieldWidget(field, focusModifier, callbacks)
                below()
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FieldWidget(field, focusModifier, callbacks)
            below()
        }
    }
}

private fun errorOf(field: FieldUi): String? = when (field) {
    is FieldUi.Text -> field.error
    is FieldUi.Choice -> field.error
    is FieldUi.Bool, is FieldUi.Collapsed -> null
}

private fun linkOf(field: FieldUi): LinkUi = when (field) {
    is FieldUi.Text -> field.link
    is FieldUi.Choice -> field.link
    is FieldUi.Bool -> field.link
    is FieldUi.Collapsed -> LinkUi.None
}

/** The widget and the link slot on one line. */
@Composable
private fun FieldWidget(field: FieldUi, focusModifier: Modifier, callbacks: ParameterFieldCallbacks) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            when (field) {
                is FieldUi.Text -> TextInput(field, focusModifier, callbacks)
                is FieldUi.Bool -> Checkbox(
                    checked = field.checked,
                    onCheckedChange = { callbacks.onInput(field.id, it.toString()) },
                    modifier = focusModifier,
                )
                is FieldUi.Choice -> ChoiceInput(field, focusModifier, callbacks)
                is FieldUi.Collapsed -> Unit
            }
        }
        if (field !is FieldUi.Bool) MultilineToggle(field, callbacks)
        LinkIcon(field.id, linkOf(field), callbacks)
    }
}

@Composable
private fun TextInput(field: FieldUi.Text, focusModifier: Modifier, callbacks: ParameterFieldCallbacks) {
    val state = rememberSyncedTextFieldState(field.value) { callbacks.onInput(field.id, it) }
    val outline = if (field.error != null) Outline.Error else Outline.None
    val placeholder: (@Composable () -> Unit)? = field.placeholder?.let { text -> { Text(text, color = faintText, maxLines = 1) } }
    Row(verticalAlignment = Alignment.Top) {
        if (field.isMultiline == true) {
            // Jewel's TextArea scrolls inside itself and fails when measured with an unbounded
            // height, so it always gets an upper bound; longer text scrolls within the area.
            TextArea(state = state, modifier = focusModifier.weight(1f).heightIn(min = 56.dp, max = MultilineMaxHeight), outline = outline, placeholder = placeholder)
        } else {
            TextField(
                state = state,
                modifier = focusModifier.weight(1f),
                outline = outline,
                placeholder = placeholder,
                keyboardOptions = if (field.isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            )
        }
    }
}

/**
 * `>` widens a String field to several lines (spec 03), with a tooltip saying which way it goes;
 * an empty slot for the other types.
 */
@Composable
private fun MultilineToggle(field: FieldUi, callbacks: ParameterFieldCallbacks) {
    Box(Modifier.width(ToggleSlot), contentAlignment = Alignment.Center) {
        val text = field as? FieldUi.Text ?: return@Box
        val multiline = text.isMultiline ?: return@Box
        WithTooltip(text.multilineTooltip) {
            Icon(
                if (multiline) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
                contentDescription = text.multilineTooltip,
                modifier = Modifier.size(14.dp).clickable { callbacks.onToggleMultiline(field.id) }.testTag(KatachiTestTags.multiline(field.id)),
            )
        }
    }
}

@Composable
private fun ChoiceInput(field: FieldUi.Choice, focusModifier: Modifier, callbacks: ParameterFieldCallbacks) {
    // Without a value the combo starts on "choose one", which disappears once a value is picked.
    val hasValue = field.selectedIndex >= 0
    val items = if (hasValue) field.options else listOf(field.placeholder) + field.options
    ListComboBox(
        items = items,
        selectedIndex = if (hasValue) field.selectedIndex else 0,
        onSelectedItemChange = { index ->
            val value = if (hasValue) field.options.getOrNull(index) else field.options.getOrNull(index - 1)
            value?.let { callbacks.onInput(field.id, it) }
        },
        modifier = focusModifier.fillMaxWidth(),
        outline = if (field.error != null) Outline.Error else Outline.None,
    )
}

@Composable
private fun LinkIcon(id: FieldId, link: LinkUi, callbacks: ParameterFieldCallbacks) {
    Box(Modifier.width(LinkSlot), contentAlignment = Alignment.CenterEnd) {
        when (link) {
            LinkUi.None -> Spacer(Modifier.size(14.dp))
            LinkUi.Linked -> LinkGlyph(linked = true)
            is LinkUi.Unlinked -> LinkGlyph(linked = false, modifier = Modifier.clickable { callbacks.onRelink(id) })
        }
    }
}

/** A parameter of an untaken branch: one grey line after a dotted mark, input kept (E-08). */
@Composable
private fun CollapsedField(field: FieldUi.Collapsed) {
    val color = faintText
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.padding(start = 4.dp).size(width = 2.dp, height = 14.dp)) {
            drawLine(
                color,
                Offset(size.width / 2, 0f),
                Offset(size.width / 2, size.height),
                strokeWidth = size.width,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
            )
        }
        Text(field.label, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
