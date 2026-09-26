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
}
