package me.tbsten.katachi.intellij.ide.authoring.codevision

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.TextRange
import com.intellij.ui.awt.RelativePoint
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesPopupModel
import me.tbsten.katachi.intellij.presentation.authoring.EntryPopupRow
import java.awt.Point
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Shows [model] under the Code Vision at [textRange] of [editor] (not at the caret, which a press does not move); choosing a row that
 * has a file calls [open] with it. A row without a file (a heading, "... and 12 more") is text: it cannot be selected, so the first
 * row the keyboard lands on is a file.
 */
internal fun showEntryFilesPopup(editor: Editor, textRange: TextRange, model: EntryFilesPopupModel, open: (Path) -> Unit) {
    sdkCall("show the files of a layout line") {
        val below = editor.offsetToXY(textRange.startOffset).let { Point(it.x, it.y + editor.lineHeight) }
        createEntryFilesPopup(model, open).show(RelativePoint(editor.contentComponent, below))
    }
}

/** The popup of [model], not yet shown: a list popup whose headings cannot be selected, as the rows of a ◆'s menu that cannot be chosen. */
internal fun createEntryFilesPopup(model: EntryFilesPopupModel, open: (Path) -> Unit): ListPopup =
    JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<EntryPopupRow>(model.title, model.rows) {
        override fun getTextFor(value: EntryPopupRow): String = value.text + (value.trailing?.let { "  $it" } ?: "")

        override fun isSelectable(value: EntryPopupRow): Boolean = value.selectable

        override fun isSpeedSearchEnabled(): Boolean = true

        override fun onChosen(selectedValue: EntryPopupRow, finalChoice: Boolean): PopupStep<*>? {
            selectedValue.target?.let(open)
            return FINAL_CHOICE
        }
    })

/** Reads the files that really are in a directory of the project, for the popup of a glob that matched none. */
internal object DirectoryListing {
    /** At most [limit] names of the files directly in [directory] (relative to [root]), sorted; none when it cannot be read. */
    fun namesIn(root: Path, directory: String, limit: Int): List<String> {
        val path = if (directory.isEmpty()) root else root.resolve(directory)
        return try {
            Files.newDirectoryStream(path).use { entries -> entries.filter { Files.isRegularFile(it) }.map { it.fileName.toString() }.sorted().take(limit) }
        } catch (_: IOException) {
            emptyList()
        } catch (_: InvalidPathException) {
            emptyList()
        }
    }
}
