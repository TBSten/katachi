package me.tbsten.katachi.intellij.ide

import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.AnalysisTestBase
import javax.swing.JComponent

internal class KatachiConfigurableTest : AnalysisTestBase() {
    private lateinit var configurable: KatachiConfigurable
    private lateinit var panel: JComponent

    override fun setUp() {
        super.setUp()
        configurable = KatachiConfigurable(project)
        panel = configurable.createComponent()
        configurable.reset()
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
        } finally {
            super.tearDown()
        }
    }

    private fun box(key: String): JBCheckBox {
        val text = KatachiBundle.message(key)
        return UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).single { it.text == text }
    }

    private val enabledBox get() = box("settings.editorNotification.enabled")
    private val emptyBox get() = box("settings.editorNotification.empty")
    private val contentBox get() = box("settings.editorNotification.content")
    private val onceBox get() = box("settings.editorNotification.contentOnce")
    private val autoLoadBox get() = box("settings.editorNotification.autoLoad")

    // covers: 論点14
    fun `test 新しい5項目の文言のチェックボックスがそれぞれ1つ見える`() {
        listOf(
            "settings.editorNotification.enabled",
            "settings.editorNotification.empty",
            "settings.editorNotification.content",
            "settings.editorNotification.contentOnce",
            "settings.editorNotification.autoLoad",
        ).forEach { key ->
            val text = KatachiBundle.message(key)
            val found = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).filter { it.text == text }
            assertEquals("checkbox for $key", 1, found.size)
        }
    }

    // covers: 論点14
    fun `test 既定ではすべてのチェックボックスが選ばれて有効`() {
        listOf(enabledBox, emptyBox, contentBox, onceBox, autoLoadBox).forEach {
            assertTrue("${it.text} selected", it.isSelected)
            assertTrue("${it.text} enabled", it.isEnabled)
        }
    }

    // covers: 論点14
    fun `test 全体をOFFにすると詳細3つが無効になり戻すと有効に戻る`() {
        enabledBox.doClick()

        assertFalse(emptyBox.isEnabled)
        assertFalse(contentBox.isEnabled)
        assertFalse(onceBox.isEnabled)
        assertTrue("auto load does not depend on the master switch", autoLoadBox.isEnabled)

        enabledBox.doClick()

        assertTrue(emptyBox.isEnabled)
        assertTrue(contentBox.isEnabled)
        assertTrue(onceBox.isEnabled)
    }

    // covers: 論点14
    fun `test 中身のあるファイルをOFFにすると最初の1回だけが無効になり戻すと有効に戻る`() {
        contentBox.doClick()

        assertFalse(onceBox.isEnabled)
        assertTrue(emptyBox.isEnabled)

        contentBox.doClick()

        assertTrue(onceBox.isEnabled)
    }

    // covers: 論点14
    fun `test 全体を戻しても中身がOFFのままなら最初の1回は無効のまま`() {
        contentBox.doClick()
        enabledBox.doClick()
        enabledBox.doClick()

        assertTrue(contentBox.isEnabled)
        assertFalse(onceBox.isEnabled)
    }

    // covers: 論点14
    fun `test 変更を適用すると設定に書かれ、元に戻すと画面も戻る`() {
        val settings = KatachiSettings.getInstance(project)
        try {
            enabledBox.doClick()
            autoLoadBox.doClick()
            assertTrue(configurable.isModified)

            configurable.apply()

            assertFalse(settings.editorNotificationEnabled)
            assertFalse(settings.autoLoadTemplates)
            assertTrue(settings.notifyOnEmptyFile)
            assertFalse(configurable.isModified)

            configurable.reset()
            assertFalse(enabledBox.isSelected)
        } finally {
            settings.editorNotificationEnabled = true
            settings.autoLoadTemplates = true
        }
    }
}
