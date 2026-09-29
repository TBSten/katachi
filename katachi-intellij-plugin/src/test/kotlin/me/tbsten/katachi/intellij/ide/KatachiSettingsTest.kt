package me.tbsten.katachi.intellij.ide

import com.intellij.util.xmlb.XmlSerializer
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.generate.OpenAfterGeneration
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import java.nio.file.Path

internal class KatachiSettingsTest : AnalysisTestBase() {

    fun `test 既定は自動で再読み込みせず、生成後は最初の1ファイルを開き、既存ファイルは確認する`() {
        val settings = KatachiSettings()
        assertFalse(settings.autoReloadOnSave)
        assertEquals(OpenAfterGeneration.First, settings.openAfterGeneration)
        assertEquals(OnExistingChoice.Fail, settings.onExisting)
        assertEmpty(settings.collapsedModules)
    }

    fun `test 保存した値を読み戻せる`() {
        val collapsed = setOf(ModuleId(Path.of("/work/project"), ":arch-a"), ModuleId(Path.of("/work/other"), ":"))
        val written = KatachiSettings().apply {
            autoReloadOnSave = true
            openAfterGeneration = OpenAfterGeneration.All
            onExisting = OnExistingChoice.Skip
            collapsedModules = collapsed
        }

        val read = KatachiSettings()
        read.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(written.state), KatachiSettingsState::class.java))

        assertTrue(read.autoReloadOnSave)
        assertEquals(OpenAfterGeneration.All, read.openAfterGeneration)
        assertEquals(OnExistingChoice.Skip, read.onExisting)
        assertEquals(collapsed, read.collapsedModules)
    }

    fun `test 上書きは覚えずその前の選択を残す`() {
        val settings = KatachiSettings()
        settings.onExisting = OnExistingChoice.Skip
        settings.onExisting = OnExistingChoice.Overwrite
        assertEquals(OnExistingChoice.Skip, settings.onExisting)
    }

    // covers: 論点15
    fun `test 通知と自動読み込みの5項目は既定でどれも有効`() {
        val settings = KatachiSettings()
        assertTrue(settings.editorNotificationEnabled)
        assertTrue(settings.notifyOnEmptyFile)
        assertTrue(settings.notifyOnFileWithContent)
        assertTrue(settings.notifyOnContentOnce)
        assertTrue(settings.autoLoadTemplates)
    }

    // covers: 論点15
    fun `test 通知と自動読み込みの5項目を保存して読み戻せる`() {
        val written = KatachiSettings().apply {
            editorNotificationEnabled = false
            notifyOnEmptyFile = false
            notifyOnFileWithContent = false
            notifyOnContentOnce = false
            autoLoadTemplates = false
        }

        val read = KatachiSettings()
        read.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(written.state), KatachiSettingsState::class.java))

        assertFalse(read.editorNotificationEnabled)
        assertFalse(read.notifyOnEmptyFile)
        assertFalse(read.notifyOnFileWithContent)
        assertFalse(read.notifyOnContentOnce)
        assertFalse(read.autoLoadTemplates)
    }

    // covers: 論点15
    fun `test 閉じた記憶と最初の1回の記憶は状態に含まれず保存されない`() {
        val written = KatachiSettings().apply {
            editorNotificationEnabled = false
            notifyOnEmptyFile = false
            notifyOnFileWithContent = false
            notifyOnContentOnce = false
            autoLoadTemplates = false
            autoReloadOnSave = true
        }

        val element = XmlSerializer.serialize(written.state)
        val saved = element.children.mapNotNull { it.getAttributeValue("name") }.toSet()

        assertEquals(
            setOf(
                "editorNotificationEnabled",
                "notifyOnEmptyFile",
                "notifyOnFileWithContent",
                "notifyOnContentOnce",
                "autoLoadTemplates",
                "autoReloadOnSave",
            ),
            saved,
        )
        val declared = KatachiSettingsState::class.java.declaredFields.map { it.name }
        assertFalse(declared.any { it.contains("dismiss", ignoreCase = true) || it.contains("seen", ignoreCase = true) || it.contains("closed", ignoreCase = true) })
    }
}
