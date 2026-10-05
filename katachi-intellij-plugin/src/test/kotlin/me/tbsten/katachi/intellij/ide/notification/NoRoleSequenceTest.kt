package me.tbsten.katachi.intellij.ide.notification

import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.HELD
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.USE_CASE_DIRECTORY
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.rolesFor

/**
 * The notification through sequences, end to end from a change of the definition's state to the panel on screen: the
 * states a session goes through (`Loading`, `Ready`, stale, a failed reload), the user's edits that make the file held,
 * and the other katachi notification wanting the same file. UI faults showed up in sequences, not in single states.
 */
internal class NoRoleSequenceTest : NoRoleTestBase() {
    fun `test 定義の状態が NotLoaded から Ready 古い 再読み込み 失敗 回復と進む間の通知は答えのある間だけ出て点滅しない`() {
        val file = write(STRAY, "")
        answer(STRAY)
        val editor = open(file)
        val states = DefinitionStateScripts.standardCycle(snapshot)
        var shownOnce = false

        fake.play(states) { state ->
            val answer = state.answer
            val expected = if (answer == null) null else expectedText(STRAY, stale = answer.freshness is Freshness.Stale)
            awaitNoRoleText(state::class.simpleName.orEmpty(), editor, expected)
            // Once the answer exists no state takes the notification away again: that is the flicker.
            if (shownOnce) assertNotNull("flicker at $state", peekNoRolePanel(editor))
            shownOnce = shownOnce || expected != null
        }

        assertTrue(shownOnce)
    }

    fun `test 乱数の列でも通知は答えのある状態にだけ付き古さが状態の freshness と一致する`() {
        val file = write(STRAY, "")
        answer(STRAY)
        val editor = open(file)
        val walk = DefinitionStateScripts.random(kotlin.random.Random(20261005), 40, listOf(snapshot, noRoleSnapshotWithMore()))

        fake.play(walk) { state ->
            val answer = state.answer
            val expected = if (answer == null) null else expectedText(STRAY, stale = answer.freshness is Freshness.Stale || state is DefinitionState.Unavailable)
            awaitNoRoleText("$state", editor, expected)
        }
    }

    fun `test Loading への遷移では描き直さず Ready になって初めて描き直す`() {
        fake.emit(ready())
        val file = write(STRAY, "")
        answer(STRAY)
        val editor = open(file)
        assertNotNull(noRolePanelOf(editor))
        // The platform asks again on its own a moment after a file opens; let that pass before counting.
        pump(700)
        val before = fake.asked.size

        fake.emit(DefinitionState.Loading(ready()))
        pump(700)

        assertEquals("Loading redraws nothing", before, fake.asked.size)
        assertNotNull("and the answer stays", peekNoRolePanel(editor))
        fake.emit(ready(noRoleSnapshotWithMore()))
        awaitAsked(before)
    }

    fun `test 定義が使えなくなって古い印が付いた後 Loading に移ると前の答えの鮮度に戻って印が消える`() {
        val ready = ready()
        fake.emit(ready)
        val file = write(STRAY, "")
        answer(STRAY)
        val editor = open(file)
        awaitNoRoleText("fresh", editor, expectedText(STRAY))

        fake.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.LoadFailed, ready))
        awaitNoRoleText("unavailable keeps the answer and says it may be old", editor, expectedText(STRAY, stale = true))

        // Design 2.1: Loading(previous) shows the previous answer with its own freshness (fresh here), not the mark of Unavailable.
        fake.emit(DefinitionState.Loading(ready))
        awaitNoRoleText("loading again", editor, expectedText(STRAY))
        pump(500)
        assertEquals(expectedText(STRAY), peekNoRolePanel(editor)?.text)
    }

    fun `test 定義が替わって当たるようになると消え別の場所へ移すと出る`() {
        fake.emit(ready())
        val file = write(STRAY, "")
        answer(STRAY)
        val editor = open(file)
        assertNotNull(noRolePanelOf(editor))

        // The user adds the directory to the role's layout and the definition is read again.
        val fixed = definitionSnapshot(
            definitionModel(
                roleModel("domain.UseCase", layoutEntry("$USE_CASE_DIRECTORY/*UseCase.kt"), layoutEntry("$USE_CASE_DIRECTORY/helpers/*.kt")),
            ),
        )
        fake.answer(path(STRAY), rolesFor(STRAY, fixed))
        fake.emit(ready(fixed))
        awaitNoRoleText("held after the edit", editor, null)

        // The file is moved to a place no role takes: the notification comes back with the new answer.
        fake.answer(path(STRAY), rolesFor(STRAY, snapshot))
        fake.emit(ready(snapshot))
        awaitNoRoleText("unexpected again", editor, expectedText(STRAY))
    }

    fun `test 定義が読めた後に定義の外のファイルや当たるファイルを開いても通知は出ない`() {
        fake.emit(ready())
        answer(HELD)

        assertNull(noRolePanelOf(open(HELD, "class A\n")))
    }

    fun `test テンプレートの通知が出るはずのファイルでは当たる Role 無しの通知だけが出て × で閉じるとテンプレートの通知に戻る`() {
        synced = syncedWith(":arch-a")
        loadIndex()
        fake.emit(ready())
        val relative = "feature/a/ui/Home.kt"
        val file = write(relative, "")
        fake.answer(path(relative), rolesFor(relative))
        val editor = open(file)

        assertEquals(NotificationDecision.CreateFromTemplate::class, provider.templateDecisionOf(project, file, path(relative))::class)
        val rule = noRolePanelOf(editor) ?: throw AssertionError("the no-role notification lost to the convenience")
        assertNull("one katachi notification at a time", panelOf(editor))

        rule.button("notification.rules.dismiss.tooltip").doClick()
        // The artificial case (the fake says unexpected while a template matches): once closed, the template shows.
        assertNull(noRolePanelOf(editor))
        assertNotNull(panelOf(editor))
    }

    fun `test 生成中のファイルでは当たる Role 無しの通知より生成中の案内が勝つ`() {
        synced = syncedWith(":arch-a")
        loadIndex()
        fake.emit(ready())
        val relative = "feature/a/ui/Home.kt"
        val file = write(relative, "")
        fake.answer(path(relative), rolesFor(relative))
        val editor = open(file)
        assertNotNull(noRolePanelOf(editor))

        service.ledger.markGenerating(path(relative), matchesOf(relative).first().id, "./gradlew katachiTemplate")
        updateAll()

        val template = panelOf(editor) ?: throw AssertionError("the generating notification must show")
        assertTrue(template.text, template.text.isNotBlank())
        assertNull("the no-role notification gives way to the generation", noRolePanelOf(editor))
        // A Kotlin file holds the notice itself, so the command is not spelled out.
        assertEquals(KatachiBundle.message("notification.editor.generating"), template.text)
    }

    private fun awaitAsked(before: Int) {
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching({ "asked ${fake.asked.size}" }, { fake.asked.size > before }, 10)
    }

    /** A definition that differs from [snapshot] only by one more role, so that two reads give different states. */
    private fun noRoleSnapshotWithMore() = definitionSnapshot(
        definitionModel(
            roleModel("domain.UseCase", layoutEntry("$USE_CASE_DIRECTORY/*UseCase.kt")),
            roleModel("Extra", layoutEntry("extra/*.kt")),
        ),
    )
}
