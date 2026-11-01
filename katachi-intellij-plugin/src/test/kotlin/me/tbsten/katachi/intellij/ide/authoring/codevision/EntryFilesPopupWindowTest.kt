package me.tbsten.katachi.intellij.ide.authoring.codevision

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.popup.list.ListPopupImpl
import me.tbsten.katachi.intellij.ide.authoring.BundleAuthoringStrings
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesInput
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesPopupModel
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesPopups
import me.tbsten.katachi.intellij.presentation.authoring.EntryPopupRow
import java.nio.file.Paths

/** What a person can reach in the popup of a Code Vision: only the rows that are files, the first of which is already selected. */
internal class EntryFilesPopupWindowTest : BasePlatformTestCase() {
    private val root = Paths.get("/work")

    private fun noMatchModel(): EntryFilesPopupModel = EntryFilesPopups.of(
        EntryFilesInput(patterns = listOf("core/service/*Servise.kt"), files = emptyList(), count = 0, projectRoot = root),
        BundleAuthoringStrings,
    ) { _, _ -> listOf("Another.kt", "Other.kt") }

    fun `test 選べる行は ファイルのある行だけで 見出しと もっと件数の行は選べない`() {
        val model = noMatchModel()

        assertEquals(
            listOf(false, false, true, true),
            model.rows.map { it.selectable },
        )
        assertFalse(EntryPopupRow("… and 3 more (open the tool window)").selectable)
        assertTrue(EntryPopupRow("A.kt", target = root.resolve("A.kt")).selectable)
    }

    fun `test 実際の popup は 最初に選ばれている行が ファイルを持つ 見出しの行ではない`() {
        val model = noMatchModel()
        val opened = mutableListOf<java.nio.file.Path>()
        val popup = createEntryFilesPopup(model) { opened.add(it) }
        try {
            val list = (popup as ListPopupImpl).list
            val first = list.selectedValue as? EntryPopupRow
            assertNotNull("a row is selected", first)
            first!!
            assertEquals("Another.kt", first.text)
            assertEquals(root.resolve("core/service/Another.kt"), first.target)
        } finally {
            Disposer.dispose(popup)
        }
    }
}
