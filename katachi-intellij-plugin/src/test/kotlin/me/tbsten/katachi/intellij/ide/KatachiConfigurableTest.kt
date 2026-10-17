package me.tbsten.katachi.intellij.ide

import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.AnalysisTestBase
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JRadioButton

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
        // Under the notification switches, in the same group as the master switch: it is one of them.
        val order = boxes.map { it.text }
        assertTrue(order.toString(), order.indexOf(text) > order.indexOf(onceBox.text))
        assertTrue(order.toString(), order.indexOf(enabledBox.text) < order.indexOf(emptyBox.text))
    }

    fun `test 見出しが定義の読み込み エディタ 生成の順に3つ並ぶ`() {
        val titles = UIUtil.findComponentsOfType(panel, TitledSeparator::class.java).map { it.text }
        assertEquals(
            listOf("settings.group.definition", "settings.group.editor", "settings.group.generation").map(KatachiBundle::message),
            titles,
        )
    }

    /** The page's parts in tree order: a heading by its title, a checkbox or radio by its text. */
    private fun flatten(c: java.awt.Component): List<String> = buildList {
        when (c) {
            is TitledSeparator -> add("# ${c.text}")
            is JBCheckBox -> add("[x] ${c.text}")
            is JRadioButton -> add("(o) ${c.text}")
        }
        if (c is java.awt.Container) c.components.forEach { addAll(flatten(it)) }
    }

    fun `test 項目は見出しごとに設計の図のとおりの並びで並ぶ`() {
        fun m(key: String) = KatachiBundle.message(key)
        assertEquals(
            listOf(
                "# ${m("settings.group.definition")}",
                "[x] ${m("settings.editorNotification.autoLoad")}",
                "[x] ${m("settings.autoReload")}",
                "# ${m("settings.group.editor")}",
                "[x] ${m("settings.showViolations")}",
                "[x] ${m("settings.editorNotification.enabled")}",
                "[x] ${m("settings.editorNotification.empty")}",
                "[x] ${m("settings.editorNotification.content")}",
                "[x] ${m("settings.editorNotification.contentOnce")}",
                "[x] ${m("settings.rules.noRole")}",
                "# ${m("settings.group.generation")}",
                "(o) ${m("settings.open.first")}",
                "(o) ${m("settings.open.all")}",
                "(o) ${m("settings.open.none")}",
            ),
            flatten(panel),
        )
    }

    fun `test 定義の読み直しの下に2秒後の説明があり ガターの説明にはリンクが付く`() {
        val labels = UIUtil.findComponentsOfType(panel, JLabel::class.java).map { it.text.orEmpty() } +
            UIUtil.findComponentsOfType(panel, JEditorPane::class.java).map { it.text.orEmpty() }
        // The HTML of a comment is wrapped at its width: compare the words, not the line breaks.
        val words = labels.map { it.replace(Regex("\\s+"), " ") }
        assertTrue(labels.toString(), words.any { KatachiBundle.message("settings.autoReload.comment").replace(Regex("\\s+"), " ") in it })
        assertTrue(labels.toString(), words.any { "</a>" in it })
    }

    fun `test ガターのリンクを押すと 表示名ではなく設定の ID でページを開く`() {
        val opened = mutableListOf<String>()
        configurable.pageOpener = SettingsPageOpener { _, _, pageId -> opened += pageId }
        val pane = UIUtil.findComponentsOfType(panel, JEditorPane::class.java).single { "</a>" in it.text }

        val link = javax.swing.event.HyperlinkEvent(pane, javax.swing.event.HyperlinkEvent.EventType.ACTIVATED, null, "")
        pane.hyperlinkListeners.forEach { it.hyperlinkUpdate(link) }

        assertEquals(listOf("editor.preferences.gutterIcons"), opened)
        assertEquals("editor.preferences.gutterIcons", KatachiConfigurable.GUTTER_ICONS_ID)
    }

    fun `test 生成後に開くファイルの選択肢が3つ並ぶ`() {
        val radios = UIUtil.findComponentsOfType(panel, JRadioButton::class.java).map { it.text }
        assertEquals(
            listOf("settings.open.first", "settings.open.all", "settings.open.none").map(KatachiBundle::message),
            radios,
        )
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
            // A negative stays a window: a draw that never comes cannot be waited for.
            val window = System.currentTimeMillis() + HighlightingRefresher.SETTLE_MILLIS + 200
            PlatformTestUtil.waitWithEventsDispatching("a window for a draw that must not come", { System.currentTimeMillis() > window && !refresher.isBusy }, 10)
            assertEquals("a setting the annotations do not read draws nothing", emptyList<Pair<String, List<Path>>>(), redraws)

            box("settings.showViolations").doClick()
            configurable.apply()
            // redrawNow draws on its own coroutine: wait for the refresher's record, not for a time.
            PlatformTestUtil.waitWithEventsDispatching("the draw of the setting reached the restarter", { redraws.isNotEmpty() && !refresher.isBusy }, 10)

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
