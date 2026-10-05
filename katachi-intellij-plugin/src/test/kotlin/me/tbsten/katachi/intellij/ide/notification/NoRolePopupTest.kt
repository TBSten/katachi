package me.tbsten.katachi.intellij.ide.notification

import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.rules.roleSuggestionDraftFor
import me.tbsten.katachi.intellij.testing.MISNAMED
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.USE_CASE_DIRECTORY
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.rolesFor

/** The links of the no-role panel and the popup under [Nearby roles...]: what each row does (design 2.2, 2.3). */
internal class NoRolePopupTest : NoRoleTestBase() {
    private val nearbyKey = "notification.rules.nearbyRoles"

    fun `test 近い Role のリンクで popup がリンクの下に開き近い順の行と下書きと更新が並ぶ`() {
        fake.emit(ready())
        declareSites()
        val panel = noRolePanelOf(openAnswered(STRAY)) ?: throw AssertionError("no panel")
        val nearby = rolesFor(STRAY).nearby

        val link = panel.link(nearbyKey)
        link.doClick()

        val popup = popups.single()
        assertSame("opens under the link that was clicked", link, popup.under)
        val rows = popup.rows
        assertEquals(nearby.size + 3, rows.size)
        nearby.forEachIndexed { index, place ->
            assertTrue(rows[index], rows[index].startsWith(KatachiBundle.message("popup.rules.nearby.row", place.role.qualifiedName, place.directory)))
        }
        assertEquals("---", rows[nearby.size])
        assertEquals(KatachiBundle.message("popup.rules.copySuggestion", "Paging"), rows[nearby.size + 1])
        assertEquals(KatachiBundle.message("popup.rules.action.refresh"), rows.last())
    }

    fun `test 近い Role の行を押すとその宣言元へ移りファイル名と行が行に書いてある`() {
        fake.emit(ready())
        val sites = declareSites()
        val panel = noRolePanelOf(openAnswered(STRAY)) ?: throw AssertionError("no panel")
        panel.link(nearbyKey).doClick()
        val popup = popups.single()
        val first = popup.rows.first()

        assertTrue(first, first.endsWith("UseCaseRole.kt:1") || first.endsWith("ModelRole.kt:1"))
        popup.press(first)

        val opened = ruleEffects.opened.single()
        assertEquals(sites.getValue(first.substringAfterLast("   ").substringBefore(':')), opened.file)
        assertEquals(1, opened.line)
    }

    fun `test 宣言元を特定できない行は理由を添えて押せない`() {
        fake.emit(ready())
        // Nothing declared: every site is unresolved, as when the role file is not among the test source roots.
        val panel = noRolePanelOf(openAnswered(STRAY)) ?: throw AssertionError("no panel")
        panel.link(nearbyKey).doClick()
        val popup = popups.single()
        val row = popup.rows.first()

        assertTrue(row, row.contains(KatachiBundle.message("popup.rules.unresolved", "\u0000").substringBefore('\u0000')))
        assertFalse(popup.isEnabled(row))
        assertTrue(ruleEffects.opened.isEmpty())
    }

    fun `test 近い Role が無い通知でも popup は下書きと更新を持ち近い Role の行は押せない`() {
        fake.emit(ready())
        fake.answer(path("README.md"), rolesFor("README.md"))
        val panel = noRolePanelOf(open("README.md", "")) ?: throw AssertionError("no panel")

        panel.link(nearbyKey).doClick()

        val popup = popups.single()
        val none = "— " + KatachiBundle.message("popup.rules.nearby.none")
        assertEquals(listOf(none, "---", KatachiBundle.message("popup.rules.copySuggestion", "READMEMd"), KatachiBundle.message("popup.rules.action.refresh")), popup.rows)
        assertFalse(popup.isEnabled(none))
    }

    fun `test 下書きの行は Role の宣言をクリップボードに入れ更新の行は定義を読み直す`() {
        fake.emit(ready())
        declareSites()
        val panel = noRolePanelOf(openAnswered(STRAY)) ?: throw AssertionError("no panel")
        panel.link(nearbyKey).doClick()
        val popup = popups.single()

        popup.press(KatachiBundle.message("popup.rules.copySuggestion", "Paging"))
        popup.press(KatachiBundle.message("popup.rules.action.refresh"))

        assertEquals(listOf(roleSuggestionDraftFor(STRAY)), ruleEffects.copied)
        assertEquals(1, ruleEffects.refreshes.get())
    }

    fun `test 下書きは知られたディレクトリのファイルもファイルの記法で書く`() {
        fake.emit(ready())
        val panel = noRolePanelOf(openAnswered(MISNAMED)) ?: throw AssertionError("no panel")
        panel.link(nearbyKey).doClick()

        popups.single().press(KatachiBundle.message("popup.rules.copySuggestion", "Paging"))

        assertEquals(listOf(roleSuggestionDraftFor(MISNAMED)), ruleEffects.copied)
        assertTrue(ruleEffects.copied.single().contains("\"$USE_CASE_DIRECTORY\" / \"Paging\".ktFile()"))
    }

    fun `test 定義を開くは architecture の宣言元へ移る`() {
        fake.emit(ready())
        val sites = declareSites()
        val panel = noRolePanelOf(openAnswered(STRAY)) ?: throw AssertionError("no panel")

        panel.link("notification.rules.openDefinition").doClick()

        assertEquals(sites.getValue("Architecture.kt"), ruleEffects.opened.single().file)
        assertEquals(3, ruleEffects.opened.single().line)
    }

    fun `test 定義が解決できないとき定義を開くのリンクは出ない`() {
        fake.emit(ready())
        val panel = noRolePanelOf(openAnswered(STRAY)) ?: throw AssertionError("no panel")

        assertEquals(listOf(KatachiBundle.message(nearbyKey)), panel.links.map { it.text })
    }

    fun `test 歯車は設定を開き × はこのファイルの通知を閉じる`() {
        fake.emit(ready())
        val editor = openAnswered(STRAY)
        val panel = noRolePanelOf(editor) ?: throw AssertionError("no panel")

        panel.button("notification.editor.settings.tooltip").doClick()
        assertEquals(1, ruleEffects.settingsOpened.get())
        panel.button("notification.rules.dismiss.tooltip").doClick()

        assertNull(noRolePanelOf(editor))
    }

    fun `test 行の文字は _ や & を含むディレクトリでもニーモニックとして読まれず消えない`() {
        val oddSnapshot = definitionSnapshot(
            definitionModel(roleModel("x.Odd", layoutEntry("core/my_dir&co/*.kt"))),
        )
        fake.emit(ready(oddSnapshot))
        val relative = "core/my_dir&co/sub/A.kt"
        fake.answer(path(relative), rolesFor(relative, oddSnapshot))
        val panel = noRolePanelOf(open(relative, "")) ?: throw AssertionError("no panel")
        panel.link(nearbyKey).doClick()

        val first = popups.single().group.getChildren(null).first()

        assertTrue(first.templatePresentation.text, first.templatePresentation.text.contains("core/my_dir&co"))
        assertEquals(-1, first.templatePresentation.displayedMnemonicIndex)
        assertEquals(0, first.templatePresentation.mnemonic)
    }
}
