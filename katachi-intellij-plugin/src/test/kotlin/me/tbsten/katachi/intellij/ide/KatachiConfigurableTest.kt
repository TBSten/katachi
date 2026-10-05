package me.tbsten.katachi.intellij.ide

import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import java.nio.file.Path
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
    private val noRoleBox get() = box("settings.rules.noRole")

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

    fun `test 当たる Role が無いファイルの通知のチェックボックスが1つ見え通知の項目の中に並ぶ`() {
        val boxes = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java)
        val text = KatachiBundle.message("settings.rules.noRole")
        assertEquals(1, boxes.count { it.text == text })
        // Under the notification switches, before "load the templates": it is one of them.
        val order = boxes.map { it.text }
        assertTrue(order.toString(), order.indexOf(text) in order.indexOf(onceBox.text) + 1 until order.indexOf(autoLoadBox.text))
    }

    // covers: 論点14
    fun `test 既定ではすべてのチェックボックスが選ばれて有効`() {
        listOf(enabledBox, emptyBox, contentBox, onceBox, autoLoadBox, noRoleBox).forEach {
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
        assertFalse(noRoleBox.isEnabled)
        assertTrue("auto load does not depend on the master switch", autoLoadBox.isEnabled)

        enabledBox.doClick()

        assertTrue(emptyBox.isEnabled)
        assertTrue(contentBox.isEnabled)
        assertTrue(onceBox.isEnabled)
        assertTrue(noRoleBox.isEnabled)
    }

    fun `test 当たる Role が無いファイルの通知は中身のあるファイルの通知の入り切りに左右されない`() {
        contentBox.doClick()
        emptyBox.doClick()

        assertTrue(noRoleBox.isEnabled)
        assertTrue(noRoleBox.isSelected)
    }

    fun `test 当たる Role が無いファイルの通知を切って適用すると設定に書かれ他の項目は変わらない`() {
        val settings = KatachiSettings.getInstance(project)
        try {
            noRoleBox.doClick()
            assertTrue(configurable.isModified)

            configurable.apply()

            assertFalse(settings.notifyOnNoRole)
            assertTrue(settings.editorNotificationEnabled)
            assertTrue(settings.notifyOnEmptyFile)
            configurable.reset()
            assertFalse(noRoleBox.isSelected)
        } finally {
            settings.notifyOnNoRole = true
        }
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

    fun `test 違反を表示するチェックボックスが 1 つ見え 既定で選ばれている`() {
        val box = box("settings.showViolations")
        assertTrue(box.isSelected)
        assertTrue(box.isEnabled)
    }

    fun `test 違反の表示を替えて適用すると 開いているファイルの描き直しを頼み 関係ない設定だけなら頼まない`() {
        val settings = KatachiSettings.getInstance(project)
        val refresher = HighlightingRefresher.getInstance(project)
        val before = refresher.restarter
        val redraws = mutableListOf<Pair<String, List<Path>>>()
        refresher.restarter = { reason, roots -> redraws += reason to roots }
        try {
            autoLoadBox.doClick()
            configurable.apply()
            assertEquals("a setting the annotations do not read draws nothing", emptyList<Pair<String, List<Path>>>(), redraws)

            box("settings.showViolations").doClick()
            configurable.apply()

            assertFalse(settings.showViolationsInEditor)
            assertEquals("every open file is drawn again", 1, redraws.size)
            assertEquals("no root: any open file may hold an annotation", emptyList<Path>(), redraws.single().second)
        } finally {
            refresher.restarter = before
            settings.showViolationsInEditor = true
            settings.autoLoadTemplates = true
        }
    }
}
