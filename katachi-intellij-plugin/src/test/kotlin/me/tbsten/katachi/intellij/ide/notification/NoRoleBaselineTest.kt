package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.EditorNotificationPanel
import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.rules.RulesPorts
import me.tbsten.katachi.intellij.testing.MISNAMED
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.USE_CASE_DIRECTORY
import me.tbsten.katachi.intellij.testing.noRoleSnapshot
import java.nio.file.Files

/**
 * The baseline through the real providers, the real `KatachiBaselineService` and the real file watcher, over a baseline file
 * the test edits on disk as a user does: the notification follows the file without the editor being touched. The wording
 * and the weak mark are the pure decision's (`NoRoleBaselineDecisionTest`); this is about reading and redrawing.
 */
internal class NoRoleBaselineTest : NoRoleTestBase() {
    private val baselineName = "katachi-baseline.json"
    private val baselined = noRoleSnapshot(baseline = baselineName)

    private fun baselineText(version: Int = 1, vararg entries: Pair<String, String>): String =
        """{"version": $version, "checks": {"me.tbsten.katachi.check.LayoutCheck": [${entries.joinToString(",") { (rule, path) -> """{"rule": "$rule", "path": "$path"}""" }}]}}"""

    /** The VFS learns of the change as it does of an edit made in another window: by a refresh of the directory. */
    private fun refreshRoot() {
        val directory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root) ?: throw AssertionError("not in the VFS: $root")
        VfsUtil.markDirtyAndRefresh(false, true, true, directory)
    }

    private fun writeBaseline(text: String) {
        // The directory's listing is loaded first: a child found by its path only is loaded without an event.
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)?.children
        Files.writeString(path(baselineName), text)
        refreshRoot()
    }

    private fun deleteBaseline() {
        Files.delete(path(baselineName))
        refreshRoot()
    }

    private fun held(rule: String) = KatachiBundle.message("notification.rules.baselineHeld", rule)

    private fun isWeak(editor: FileEditor): Boolean =
        peekNoRolePanel(editor)?.background == EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).background

    private fun open(relative: String): FileEditor {
        fake.emit(ready(baselined))
        declareSites()
        return openAnswered(relative)
    }

    fun `test baseline が無いうちは条件付きの警告で 書くと弱い印になり 項目を消すと断定の警告に戻る`() {
        val editor = open(MISNAMED)
        awaitNoRoleText("no baseline file", editor, expectedText(MISNAMED, conditional = true))

        writeBaseline(baselineText(1))
        awaitNoRoleText("an empty baseline", editor, expectedText(MISNAMED))
        assertFalse(isWeak(editor))

        writeBaseline(baselineText(1, "UnexpectedFile" to MISNAMED))
        awaitNoRoleText("an entry added", editor, held("UnexpectedFile"))
        assertTrue("a weak mark, not a warning", isWeak(editor))

        writeBaseline(baselineText(1, "UnexpectedFile" to "some/other/File.kt"))
        awaitNoRoleText("the entry removed", editor, expectedText(MISNAMED))
        assertFalse(isWeak(editor))
    }

    fun `test 版を 2 に書き換えると baseline を使わず条件付きの警告になり 1 に戻すと弱い印に戻る`() {
        writeBaseline(baselineText(1, "UnexpectedFile" to MISNAMED))
        val editor = open(MISNAMED)
        awaitNoRoleText("version 1", editor, held("UnexpectedFile"))

        writeBaseline(baselineText(2, "UnexpectedFile" to MISNAMED))
        awaitNoRoleText("version 2", editor, expectedText(MISNAMED, conditional = true))
        assertFalse(isWeak(editor))

        writeBaseline(baselineText(1, "UnexpectedFile" to MISNAMED))
        awaitNoRoleText("version 1 again", editor, held("UnexpectedFile"))
    }

    fun `test 壊れた baseline と消された baseline は条件付きの警告で 直すと弱い印に戻る`() {
        writeBaseline(baselineText(1, "UnexpectedFile" to MISNAMED))
        val editor = open(MISNAMED)
        awaitNoRoleText("read", editor, held("UnexpectedFile"))

        writeBaseline("{ not json")
        awaitNoRoleText("broken", editor, expectedText(MISNAMED, conditional = true))

        writeBaseline(baselineText(1, "UnexpectedFile" to MISNAMED))
        awaitNoRoleText("fixed", editor, held("UnexpectedFile"))

        deleteBaseline()
        awaitNoRoleText("deleted", editor, expectedText(MISNAMED, conditional = true))
    }

    fun `test 知らないディレクトリの下のファイルは 一番上の知らないディレクトリの項目なら許容され それより深い項目や既知の祖先の項目では許容されない`() {
        writeBaseline(baselineText(1, "UnexpectedDirectory" to "$USE_CASE_DIRECTORY/helpers"))
        val editor = open(STRAY)
        awaitNoRoleText("directory entry", editor, held("UnexpectedDirectory"))
        assertTrue(isWeak(editor))

        writeBaseline(baselineText(1, "UnexpectedDirectory" to "$USE_CASE_DIRECTORY/helpers/deeper"))
        awaitNoRoleText("deeper entry", editor, expectedText(STRAY))

        writeBaseline(baselineText(1, "UnexpectedDirectory" to USE_CASE_DIRECTORY))
        awaitNoRoleText("known ancestor entry", editor, expectedText(STRAY))
    }

    fun `test 本番の RulesPorts は定義の projectRoot からの相対で baseline を読む`() {
        val withBaseline = root.resolve("with")
        val without = root.resolve("without")
        Files.createDirectories(withBaseline)
        Files.createDirectories(without)
        Files.writeString(withBaseline.resolve(baselineName), baselineText(1, "UnexpectedFile" to MISNAMED))
        val production = RulesPorts.of(project)

        val read = production.baseline(withBaseline, baselineName)
        assertTrue("$read", read is BaselineReading.Read && read.ledger.holdsFile(MISNAMED))
        assertEquals(BaselineReading.Missing, production.baseline(without, baselineName))
    }

    fun `test 許容中の通知も × で閉じられ 設定のスイッチに従う`() {
        writeBaseline(baselineText(1, "UnexpectedFile" to MISNAMED))
        val editor = open(MISNAMED)
        awaitNoRoleText("held", editor, held("UnexpectedFile"))

        val panel = noRolePanelOf(editor) ?: throw AssertionError("no panel")
        panel.button("notification.rules.dismiss.tooltip").doClick()

        awaitNoRoleText("dismissed", editor, null)
    }
}
