package me.tbsten.katachi.intellij.uitest.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput

/**
 * The text input of the field tagged [tag]: the tag sits on the text field itself in a plain field, and on the box around the
 * text field and chevron in a candidate field (a combo box), so the input is whichever of the two holds the text.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.inputOf(tag: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

/** Presses the ▾ at the right end of the candidate field tagged [tag] (the tag sits on the box around the text field and the chevron). */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.clickChevron(tag: String) {
    onNodeWithTag(tag).performMouseInput { click(Offset(width - 8f, height / 2f)) }
}
