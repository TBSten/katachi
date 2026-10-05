package me.tbsten.katachi.intellij.ide.notification

import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiConfigurable
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.testing.MISNAMED
import me.tbsten.katachi.intellij.testing.STRAY
import javax.swing.JComponent

/**
 * The settings page in front of the real notifications: a click on its checkboxes and Apply must reach the editors that
 * are already open and the ones opened later, the way a user sees it. [KatachiConfigurableTest] only reads the checkboxes.
 */
internal class NoRoleSettingsTest : NoRoleTestBase() {
    private val settings get() = KatachiSettings.getInstance(project)
    private lateinit var configurable: KatachiConfigurable
    private lateinit var page: JComponent

    override fun setUp() {
        super.setUp()
        configurable = KatachiConfigurable(project)
        page = configurable.createComponent()
        configurable.reset()
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
            settings.notifyOnNoRole = true
            settings.editorNotificationEnabled = true
        } finally {
            super.tearDown()
        }
    }

    private fun box(key: String): JBCheckBox {
        val text = KatachiBundle.message(key)
        return UIUtil.findComponentsOfType(page, JBCheckBox::class.java).single { it.text == text }
    }

    private val noRoleBox get() = box("settings.rules.noRole")
    private val masterBox get() = box("settings.editorNotification.enabled")

    fun `test 設定画面で切って適用すると開いているファイルの通知が消え後から開くファイルにも出ず戻すと両方に出る`() {
        fake.emit(ready())
        answer(STRAY)
        answer(MISNAMED)
        val first = open(STRAY, "")
        val second = write(MISNAMED, "")
        assertNotNull("shown before", noRolePanelOf(first))

        noRoleBox.doClick()
        configurable.apply()

        awaitNoRoleText("switched off in the page", first, null)
        val secondEditor = open(second)
        assertNull("a file opened afterwards", noRolePanelOf(secondEditor))

        noRoleBox.doClick()
        configurable.apply()

        awaitNoRoleText("switched on again", first, expectedText(STRAY))
        awaitNoRoleText("and the second file too", secondEditor, expectedText(MISNAMED))
    }

    fun `test 適用しない変更は通知に届かず元に戻すと画面も設定も元のまま`() {
        fake.emit(ready())
        answer(STRAY)
        val editor = open(STRAY, "")
        assertNotNull(noRolePanelOf(editor))

        noRoleBox.doClick()
        pump(400)
        assertNotNull("not applied yet", peekNoRolePanel(editor))
        assertTrue(settings.notifyOnNoRole)

        configurable.reset()

        assertTrue(noRoleBox.isSelected)
        assertFalse(configurable.isModified)
        assertNotNull(peekNoRolePanel(editor))
    }

    fun `test 通知の全体を設定画面で切ると当たる Role が無いファイルの通知も消え個別の設定は残ったまま戻すと出る`() {
        fake.emit(ready())
        answer(STRAY)
        val editor = open(STRAY, "")
        assertNotNull(noRolePanelOf(editor))

        masterBox.doClick()
        configurable.apply()

        awaitNoRoleText("master off", editor, null)
        assertFalse(noRoleBox.isEnabled)
        assertTrue("its own choice is kept", noRoleBox.isSelected)
        assertTrue(settings.notifyOnNoRole)

        masterBox.doClick()
        configurable.apply()

        awaitNoRoleText("master on", editor, expectedText(STRAY))
        assertTrue(noRoleBox.isEnabled)
    }

    fun `test 個別を切ってから全体を切って戻しても個別は切ったままで通知は出ない`() {
        fake.emit(ready())
        answer(STRAY)
        val editor = open(STRAY, "")
        assertNotNull(noRolePanelOf(editor))

        noRoleBox.doClick()
        masterBox.doClick()
        configurable.apply()
        masterBox.doClick()
        configurable.apply()

        awaitNoRoleText("master back on, own choice still off", editor, null)
        assertFalse(settings.notifyOnNoRole)
        assertTrue(settings.editorNotificationEnabled)
    }
}
