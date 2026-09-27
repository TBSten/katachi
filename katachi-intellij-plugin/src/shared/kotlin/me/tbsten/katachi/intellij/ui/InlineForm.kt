package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.FormUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.ListUi
import org.jetbrains.jewel.ui.component.Text

/** Wide windows keep the fields left-aligned at this width instead of stretching (spec 03). */
internal val FormMaxWidth = 520.dp

/** Below this the label goes above its field; side by side would leave the field too narrow. */
private val SideBySideMinWidth = 240.dp

/** The inline form: the template's summary, then its fields in order (spec 03). */
@Composable
internal fun InlineForm(form: FormUi, list: ListUi, focus: ListFocusController, onIntent: (KatachiIntent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = when {
            maxWidth < SideBySideMinWidth -> null
            maxWidth >= 400.dp -> WideLabelWidth
            else -> 84.dp
        }
        Column(Modifier.widthIn(max = FormMaxWidth).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            form.summary?.let { Text(it, color = faintText) }
            form.fields.forEach { field -> ParameterField(field, labelWidth, list, focus, onIntent) }
        }
    }
}
