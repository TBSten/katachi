package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.UIUtil
import java.nio.file.Path
import javax.swing.JLabel
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.CaptureRef
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.HELD
import me.tbsten.katachi.intellij.testing.MISNAMED
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.USE_CASE_DIRECTORY
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.noRoleSnapshot
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.rolesFor
import org.junit.Assert.assertNotEquals

/** The real providers over real editors: when the "no role holds this file" notification shows, and what closes it (issue 13). */
internal class NoRoleNotificationTest : NoRoleTestBase() {
    private val settings get() = KatachiSettings.getInstance(project)

    fun `test 当たる Role が無いファイルを開くと警告の通知が出て近い Role と ⚙ と × が並ぶ`() {
        fake.emit(ready())
        declareSites()
        val editor = openAnswered(STRAY)

        val panel = noRolePanelOf(editor) ?: throw AssertionError("no panel")

        assertNotEquals("a warning, not the info of the template notification", EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).background, panel.background)
        assertEquals(expectedText(STRAY), panel.text)
        assertEquals(
            listOf(KatachiBundle.message("notification.rules.nearbyRoles"), KatachiBundle.message("notification.rules.openDefinition")),
            panel.links.map { it.text },
        )
        panel.button("notification.editor.settings.tooltip")
        panel.button("notification.rules.dismiss.tooltip")
        assertNull("the template notification stays away", panelOf(editor))
    }

    fun `test capture の値が揃わないファイルの通知は CaptureMismatch の文で 近い Role の代わりに各階層の値と移す先が出る`() {
        val screen = roleModel(
            "feature.Screen",
            layoutEntry("feature/*/src/*/*Screen.kt", captures = listOf(CaptureRef("feature", 1), CaptureRef("feature", 3), CaptureRef("feature", 4, 0, "pascalCase"))),
        )
        val bound = definitionSnapshot(definitionModel(screen))
        fake.emit(ready(bound))
        val relative = "feature/home/src/settings/HomeScreen.kt"
        fake.answer(path(relative), rolesFor(relative, bound))
        val editor = open(relative, "")

        val panel = noRolePanelOf(editor) ?: throw AssertionError("no panel")

        val first = KatachiBundle.message("notification.rules.captureMismatch.sure", "feature.Screen", "capture(\"feature\")")
        val second = "feature: \"home\" at level 2, \"settings\" at level 4 (expected \"home\"); feature.pascalCase: \"Home\" at level 5 " +
            KatachiBundle.message("notification.rules.captureMismatch.moveTo", "feature/home/src/home/HomeScreen.kt")
        assertEquals("<html>${StringUtil.escapeXmlEntities(first)}<br>${StringUtil.escapeXmlEntities(second)}</html>", panel.text)
        assertTrue(panel.links.map { it.text }.contains(KatachiBundle.message("notification.rules.nearbyRoles")))
    }

    fun `test 近い Role が無くても通知は出て近い Role の行は無い`() {
        val lonely = noRoleSnapshot()
        fake.emit(ready())
        fake.answer(path("README.md"), rolesFor("README.md", lonely))
        val editor = open("README.md", "")

        val panel = noRolePanelOf(editor) ?: throw AssertionError("no panel")

        assertEquals(KatachiBundle.message("notification.rules.noRole.sure"), panel.text)
        assertTrue(panel.links.map { it.text }.contains(KatachiBundle.message("notification.rules.nearbyRoles")))
    }

    fun `test 当たるファイルと ignore の下と検査しない所と VCS が無視するファイルには出ない`() {
        fake.emit(ready())
        declareSites()
        val shown = mapOf(
            "held" to HELD,
            "ignore" to "docs/guide.kt",
            "foreign" to "core/.git/config",
            "vcs" to MISNAMED,
        )
        for ((name, relative) in shown) {
            val roles = if (name == "vcs") rolesFor(relative).copy(outcome = MatchOutcome.IgnoredByVcs, nearby = emptyList()) else rolesFor(relative)
            fake.answer(path(relative), roles)
            assertNull(name, noRolePanelOf(open(relative, "")))
        }
    }

    fun `test 定義に答えが無い状態のファイルと定義の外のファイルには出ない`() {
        // Nothing read yet: the fake answers nothing, as the real service does.
        answer(STRAY)
        assertNull("not loaded", noRolePanelOf(open(STRAY, "")))
        fake.emit(ready())
        // No definition holds the library file: forFile is null.
        assertNull("outside the definition", noRolePanelOf(open("elsewhere/Lib.kt", "")))
    }

    fun `test × はそのファイルだけを閉じ開き直しても別のファイルでも続き作り直したメモリでは戻る`() {
        fake.emit(ready())
        val a = openAnswered(STRAY)
        val bFile = write(MISNAMED, "")
        answer(MISNAMED)
        val shown = noRolePanelOf(a) ?: throw AssertionError("A opened without a panel")

        shown.button("notification.rules.dismiss.tooltip").doClick()
        assertNull("A after x", noRolePanelOf(a))
        assertNull("A reopened", noRolePanelOf(reopen(fileAt(path(STRAY)))))
        assertNotNull("B opened", noRolePanelOf(open(bFile)))

        recreateMemoryService()

        assertNotNull("A in a new session", noRolePanelOf(reopen(fileAt(path(STRAY)))))
    }

    fun `test テンプレートの通知の × では閉じず定義が替わっても閉じたままで出し直さない`() {
        fake.emit(ready())
        val file = write(STRAY, "")
        answer(STRAY)
        open(file)
        memoryService.dismiss(path(STRAY))
        val reopened = noRolePanelOf(reopen(file)) ?: throw AssertionError("the template notification's x closed the no-role notification")

        reopened.button("notification.rules.dismiss.tooltip").doClick()
        // The definition changes (stale, then a new read): it stays closed.
        fake.emit(ready(freshness = Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged)))
        fake.emit(ready())
        updateAll()

        assertNull(noRolePanelOf(editorOf(file)))
    }

    fun `test 未追跡の文言は git に追加するまでで追加されると通知が自分で書き変わる`() {
        fake.emit(ready())
        val file = write(STRAY, "")
        answer(STRAY)
        vcsOf = { VcsTracking.Untracked }
        val editor = open(file)
        assertEquals(expectedText(STRAY, untracked = true), noRolePanelOf(editor)?.text)

        // `git add`: the VCS's answer changes and the platform says statuses changed. Nothing else happens.
        vcsOf = { VcsTracking.Tracked }
        FileStatusManager.getInstance(project).fileStatusesChanged()

        awaitNoRoleText("tracked", editor, expectedText(STRAY))
    }

    fun `test wholeTree の定義では未追跡でも git の文言にしない`() {
        val wholeTree = noRoleSnapshot(ArchitectureModel.FILES_WHOLE_TREE)
        fake.emit(ready(wholeTree))
        val file = write(STRAY, "")
        fake.answer(path(STRAY), rolesFor(STRAY, wholeTree))
        vcsOf = { VcsTracking.Untracked }

        assertEquals(expectedText(STRAY), noRolePanelOf(open(file))?.text)
    }

    fun `test 設定の当たる Role が無いファイルの通知か通知の全体が off なら出ない`() {
        fake.emit(ready())
        val file = write(STRAY, "")
        answer(STRAY)
        val editor = open(file)
        assertNotNull(noRolePanelOf(editor))

        settings.notifyOnNoRole = false
        updateAll()
        assertNull("notifyOnNoRole off", noRolePanelOf(editor))

        settings.notifyOnNoRole = true
        updateAll()
        assertNotNull("on again", noRolePanelOf(editor))

        settings.editorNotificationEnabled = false
        updateAll()
        assertNull("all notifications off", noRolePanelOf(editor))
        settings.editorNotificationEnabled = true
    }

    fun `test 知られていないディレクトリの下のファイルは文言に付く説明にそのディレクトリが入り普通の Unexpected には付かない`() {
        fake.emit(ready())
        declareSites()
        val strayEditor = openAnswered(STRAY)
        val plainEditor = openAnswered(MISNAMED)

        val tooltip = labelTooltips(noRolePanelOf(strayEditor) ?: throw AssertionError("no panel"))
        val plain = labelTooltips(noRolePanelOf(plainEditor) ?: throw AssertionError("no panel"))

        val expected = KatachiBundle.message("notification.rules.noRole.unexpectedUnder", "$USE_CASE_DIRECTORY/helpers")
        assertEquals(listOf("<html>${StringUtil.escapeXmlEntities(expected)}</html>"), tooltip.distinct())
        assertEquals("an ordinary Unexpected file has no such tooltip", emptyList<String>(), plain.filterNotNull())
    }

    private fun labelTooltips(panel: EditorNotificationPanel): List<String?> =
        UIUtil.findComponentsOfType(panel, JLabel::class.java).map { it.toolTipText }

    fun `test dumb mode の間も通知が出る`() {
        fake.emit(ready())
        val file = write(STRAY, "")
        answer(STRAY)

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertNotNull(noRolePanelOf(open(file)))
        }
    }

    fun `test katachi の無いプロジェクトでは出さずサービスも作らない`() {
        definitions = null
        val editor = open(STRAY, "")

        assertNull(noRolePanelOf(editor))
        assertEquals(0, servicesCreated.get())
    }

    fun `test 定義サービスが例外を投げても通知が出ないだけで IDE のエラーにしない`() {
        definitions = ThrowingDefinitions(fake)
        val editor = open(STRAY, "")

        assertNull(noRolePanelOf(editor))
    }

    fun `test 判定は EDT の外で行われ定義サービスへの問い合わせは UI スレッドを使わない`() {
        val recording = RecordingDefinitions(fake)
        fake.emit(ready())
        declareSites()
        answer(STRAY)
        definitions = recording

        val editor = open(STRAY, "")
        assertNotNull(noRolePanelOf(editor))

        assertTrue("asked", recording.threads.isNotEmpty())
        assertFalse("on the EDT: ${recording.threads}", recording.threads.any { it })
    }

    private fun fileAt(path: Path) =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("not in the VFS: $path")
}
