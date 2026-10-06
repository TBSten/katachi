package me.tbsten.katachi.intellij.ide.notification

import me.tbsten.katachi.intellij.ide.definition.RefreshReason
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness

/**
 * The no-role notification over the real definition service, the real project service and a fake Gradle that writes a real
 * `definition.json`: nothing of the definition side is faked, so the answer of `forFile` (the real matcher, the real nearby
 * roles) is the one a user gets. The operations are the user's: create files, open them, close the notification, change the
 * definition and read it again.
 */
internal class NoRoleRealServiceTest : NoRoleRealServiceTestBase() {
    fun `test ファイルを作って開くと定義を読み込み終わった所で通知が出て当たるファイルには出ない`() {
        installReal()
        val stray = write("src/other/Stray.kt", "")
        val held = write("src/service/HealthService.kt", "class HealthService\n")

        // Nothing was read when the file opened: the notification itself starts the load.
        val editor = open(stray)
        awaitPanel("stray", editor, present = true)
        val text = peekNoRolePanel(editor)?.text.orEmpty()

        // The nearest place: `src/thrower` and `src/service` are both one level away with a fitting name; `thrower` is the fewer characters from `other`.
        assertTrue(text, text.contains("Thrower") && text.contains("src/thrower"))
        pump(600)
        assertNull("a file a role holds", noRolePanelOf(open(held)))
    }

    fun `test × のあと開き直しても出ず別のファイルには出て定義が読み直されて当たるようになると消える`() {
        installReal()
        val first = write("src/other/Stray.kt", "")
        val second = write("src/other/Other.kt", "")
        val firstEditor = open(first)
        awaitPanel("first", firstEditor, present = true)
        pump(600)

        peekNoRolePanel(firstEditor)!!.button("notification.rules.dismiss.tooltip").doClick()
        awaitPanel("closed", firstEditor, present = false)
        close(first)
        pump(300)
        assertNull("reopened after x", noRolePanelOf(open(first)))
        val secondEditor = open(second)
        awaitPanel("second", secondEditor, present = true)

        // The user declares src/other in the layout (here: the Thrower role moves there) and the definition is read again.
        gradle.definitionOf["arch"] = definitionJson().replace("src/thrower", "src/other")
        real.refresh(RefreshReason.UserAction)
        awaitState("reloaded") { it is DefinitionState.Ready && it.freshness == Freshness.Fresh && gradle.loadRequests.size >= 2 }
        awaitPanel("held after the reload", secondEditor, present = false)
    }

    fun `test gitTracked の定義で VCS が無視するファイルには出ない`() {
        installReal(definitionJson().replace("\"files\": \"wholeTree\"", "\"files\": \"gitTracked\""), ignoredByVcs = { it.name == "Ignored.kt" })
        val ignored = write("src/other/Ignored.kt", "")
        val stray = write("src/other/Stray.kt", "")
        val strayEditor = open(stray)
        awaitPanel("stray", strayEditor, present = true)
        pump(600)

        assertNull("ignored by the VCS", noRolePanelOf(open(ignored)))
    }
}
