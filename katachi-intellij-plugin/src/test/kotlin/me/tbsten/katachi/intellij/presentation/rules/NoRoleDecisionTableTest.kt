package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DEFINITION_ROOT
import me.tbsten.katachi.intellij.testing.HELD
import me.tbsten.katachi.intellij.testing.MISNAMED
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.USE_CASE_DIRECTORY
import me.tbsten.katachi.intellij.testing.noRoleInput
import me.tbsten.katachi.intellij.testing.noRoleSnapshot
import me.tbsten.katachi.intellij.testing.rolesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conditions of the no-role notification (design 2.1), one broken at a time, over what the real matcher answers. */
class NoRoleDecisionTableTest {
    private fun noRole(input: NoRoleInput): NoRoleDecision.NoRole =
        decideNoRoleNotification(input) as? NoRoleDecision.NoRole ?: throw AssertionError("expected NoRole but was ${decideNoRoleNotification(input)}")

    @Test
    fun `どの Role にも当たらず条件が揃えば出る`() {
        val decision = noRole(noRoleInput(MISNAMED))

        assertEquals(null, decision.unexpectedUnder)
        assertEquals(MISNAMED, decision.relativePath)
        assertFalse(decision.untracked)
        assertFalse(decision.stale)
        assertEquals(listOf("domain.UseCase"), decision.nearby.map { it.roleName }.take(1))
    }

    @Test
    fun `知らないディレクトリの下なら unexpectedUnder にそのディレクトリが入る`() {
        assertEquals(USE_CASE_DIRECTORY + "/helpers", noRole(noRoleInput(STRAY)).unexpectedUnder)
    }

    @Test
    fun `当たるファイルと ignore の下と検査しない所では出ない`() {
        val cases = mapOf(
            "当たる" to HELD,
            "ignore の下" to "docs/guide.kt",
            ".git の下" to "core/.git/config",
        )
        for ((name, path) in cases) {
            assertEquals(name, NoRoleDecision.Hidden, decideNoRoleNotification(noRoleInput(path)))
        }
    }

    @Test
    fun `outcome が IgnoredByVcs や Matched のときは近い Role が付いていても出ない`() {
        val unexpected = rolesFor(MISNAMED)
        for (outcome in listOf(MatchOutcome.IgnoredByVcs, MatchOutcome.Matched)) {
            assertEquals(outcome.toString(), NoRoleDecision.Hidden, decideNoRoleNotification(noRoleInput(roles = unexpected.copy(outcome = outcome))))
        }
    }

    @Test
    fun `forFile が答えなければ出ない`() {
        assertEquals(NoRoleDecision.Hidden, decideNoRoleNotification(noRoleInput(roles = null)))
    }

    @Test
    fun `設定のどちらかが off なら出ない`() {
        assertEquals(NoRoleDecision.Hidden, decideNoRoleNotification(noRoleInput(settings = RuleSettings(notifyOnNoRole = false))))
        assertEquals(NoRoleDecision.Hidden, decideNoRoleNotification(noRoleInput(settings = RuleSettings(notificationsEnabled = false))))
        assertTrue(decideNoRoleNotification(noRoleInput(settings = RuleSettings(loadWithoutUser = false))) is NoRoleDecision.NoRole)
    }

    @Test
    fun `そのファイルの通知を閉じていたら出ず別のファイルには効かない`() {
        val file = DEFINITION_ROOT.resolve(STRAY)
        val closed = EditorNotificationMemory.EMPTY.dismissNoRole(file)

        assertEquals(NoRoleDecision.Hidden, decideNoRoleNotification(noRoleInput(STRAY, memory = closed)))
        assertTrue(decideNoRoleNotification(noRoleInput(MISNAMED, memory = closed)) is NoRoleDecision.NoRole)
    }

    @Test
    fun `テンプレートの通知の × は当たる Role が無い通知を閉じない`() {
        val templateClosed = EditorNotificationMemory.EMPTY.dismiss(DEFINITION_ROOT.resolve(STRAY))

        assertTrue(decideNoRoleNotification(noRoleInput(STRAY, memory = templateClosed)) is NoRoleDecision.NoRole)
    }

    @Test
    fun `定義の状態が答えを持つときだけ出る`() {
        val snapshot = noRoleSnapshot()
        val ready = DefinitionState.Ready(listOf(snapshot), Freshness.Fresh)
        val failure = LoadFailure.Gradle(GradleFailure.Other(listOf("boom")))
        val shown = mapOf(
            "Ready" to ready,
            "Loading(previous)" to DefinitionState.Loading(ready),
            "Unavailable(previous)" to DefinitionState.Unavailable(DefinitionUnavailableReason.LoadFailed, ready),
            "Stale" to ready.copy(freshness = Freshness.Stale(DEFINITION_INSTANT, StaleReason.ReloadFailed(failure))),
        )
        val hidden = mapOf(
            "NotLoaded" to DefinitionState.NotLoaded,
            "Loading(null)" to DefinitionState.Loading(null),
            "Unavailable(null)" to DefinitionState.Unavailable(DefinitionUnavailableReason.NotSynced, null),
        )
        for ((name, state) in shown) {
            assertTrue(name, decideNoRoleNotification(noRoleInput(state = state, roles = rolesFor(STRAY))) is NoRoleDecision.NoRole)
        }
        for ((name, state) in hidden) {
            // A caller that still holds an old FileRoles must not draw it without an answer behind it.
            assertEquals(name, NoRoleDecision.Hidden, decideNoRoleNotification(noRoleInput(state = state, roles = rolesFor(STRAY))))
        }
    }

    @Test
    fun `古さは freshness か Unavailable のときだけ付く`() {
        val snapshot = noRoleSnapshot()
        val stale = Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged)
        val readyStale = DefinitionState.Ready(listOf(snapshot), stale)
        val readyFresh = DefinitionState.Ready(listOf(snapshot), Freshness.Fresh)

        assertFalse(noRole(noRoleInput(state = readyFresh)).stale)
        assertTrue(noRole(noRoleInput(state = readyStale)).stale)
        // Loading keeps the previous answer as it was: the wording does not change while Gradle runs.
        assertFalse(noRole(noRoleInput(state = DefinitionState.Loading(readyFresh), roles = rolesFor(STRAY))).stale)
        assertTrue(noRole(noRoleInput(state = DefinitionState.Loading(readyStale), roles = rolesFor(STRAY, freshness = stale))).stale)
        assertTrue(noRole(noRoleInput(state = DefinitionState.Unavailable(DefinitionUnavailableReason.KatachiTooOld, readyFresh), roles = rolesFor(STRAY))).stale)
    }

    @Test
    fun `未追跡の文言は git に追加していない gitTracked の定義のときだけ`() {
        val gitTracked = noRoleSnapshot(ArchitectureModel.FILES_GIT_TRACKED)
        val wholeTree = noRoleSnapshot(ArchitectureModel.FILES_WHOLE_TREE)
        for (vcs in VcsTracking.entries) {
            assertEquals("gitTracked $vcs", vcs == VcsTracking.Untracked, noRole(noRoleInput(snapshot = gitTracked, vcs = vcs)).untracked)
            assertFalse("wholeTree $vcs", noRole(noRoleInput(snapshot = wholeTree, vcs = vcs)).untracked)
        }
    }

    @Test
    fun `近い Role は forFile の順に宣言元の見え方と組になり足りない分は特定できない扱い`() {
        val roles = rolesFor(MISNAMED)
        val sites = listOf(ResolvedSiteView.Found("A.kt", 1), ResolvedSiteView.Unresolved("two files"))

        val rows = noRole(noRoleInput(roles = roles, nearbySites = sites)).nearby

        assertEquals(roles.nearby.map { it.role.qualifiedName }, rows.map { it.roleName })
        assertEquals(roles.nearby.map { it.directory }, rows.map { it.directory })
        assertEquals(sites[0], rows[0].site)
        assertEquals(sites[1], rows[1].site)
        assertTrue("a missing site reads as unresolved", rows.drop(2).all { it.site is ResolvedSiteView.Unresolved })
    }

    @Test
    fun `定義を開けるのは architecture の宣言元が特定できたときだけ`() {
        assertTrue(noRole(noRoleInput()).canOpenDefinition)
        assertFalse(noRole(noRoleInput(architectureSite = ResolvedSiteView.Unresolved("none"))).canOpenDefinition)
        assertFalse(noRole(noRoleInput(architectureSite = null)).canOpenDefinition)
    }

    @Test
    fun `ディレクトリは末尾の2セグメントに縮める`() {
        assertEquals("…/domain/useCase", shortDirectory("core/domain/src/main/kotlin/com/example/domain/useCase"))
        assertEquals("a/b", shortDirectory("a/b"))
        assertEquals("a", shortDirectory("a"))
        assertEquals("…/b/c", shortDirectory("a/b/c"))
        assertEquals("", shortDirectory(""))
    }

    @Test
    fun `定義を読み始めるのは未読み込みと読み込みを止めていた状態が戻ったときだけで設定が off なら読み始めない`() {
        val ready = DefinitionState.Ready(listOf(noRoleSnapshot()), Freshness.Fresh)
        val disabled = DefinitionState.Unavailable(DefinitionUnavailableReason.LoadingWithoutUserDisabled, null)
        val states = mapOf(
            "NotLoaded" to DefinitionState.NotLoaded,
            "disabled" to disabled,
            "other unavailable" to DefinitionState.Unavailable(DefinitionUnavailableReason.NoDefinition, null),
            "Loading" to DefinitionState.Loading(null),
            "Ready" to ready,
        )
        val expectedWhenOn = setOf("NotLoaded", "disabled")
        for ((name, state) in states) {
            assertEquals("$name on", name in expectedWhenOn, shouldEnsureDefinitionLoaded(state, loadWithoutUser = true))
            assertFalse("$name off", shouldEnsureDefinitionLoaded(state, loadWithoutUser = false))
        }
    }
}
