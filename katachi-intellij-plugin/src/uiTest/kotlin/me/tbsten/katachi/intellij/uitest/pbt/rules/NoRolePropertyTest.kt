package me.tbsten.katachi.intellij.uitest.pbt.rules

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.presentation.rules.NoRoleDecision
import me.tbsten.katachi.intellij.presentation.rules.NotificationWinner
import me.tbsten.katachi.intellij.presentation.rules.RuleSettings
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import me.tbsten.katachi.intellij.presentation.rules.arbitrateNotifications
import me.tbsten.katachi.intellij.presentation.rules.decideNoRoleNotification
import me.tbsten.katachi.intellij.testing.DEFINITION_ROOT
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.placementMatch
import me.tbsten.katachi.intellij.testing.noRoleInput
import me.tbsten.katachi.intellij.testing.noRoleSnapshot
import me.tbsten.katachi.intellij.testing.rolesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The no-role notification over random inputs and random sequences: which files show it, when it is stale, and that
 * exactly one of the two katachi notifications wins. `-Pkatachi.pbt.scale=N` runs N times as many cases.
 */
class NoRolePropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261005L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0

    private fun config(iterations: Int) = PropTest(seed = seed, iterations = (iterations * scale).toInt().coerceAtLeast(1), shrinkingMode = ShrinkingMode.Bounded(400)).toPropTestConfig()

    private val paths = listOf(
        "core/domain/src/main/kotlin/com/example/domain/useCase/helpers/Paging.kt",
        "core/domain/src/main/kotlin/com/example/domain/useCase/Paging.kt",
        "core/domain/src/main/kotlin/com/example/domain/useCase/GetUserUseCase.kt",
        "core/domain/src/main/kotlin/com/example/domain/model/User.kt",
        "docs/guide.kt",
        "core/.git/config",
        "README.md",
        "elsewhere/deep/Thing.kt",
    )

    private class Case(
        val relative: String,
        val states: List<DefinitionState>,
        val which: Int,
        val settings: RuleSettings,
        val vcs: VcsTracking,
        val closed: Boolean,
        val files: String,
    )

    private val caseArb: Arb<Case> = arbitrary { rs ->
        val r = rs.random
        val walk = DefinitionStateScripts.random(Random(r.nextLong()), 8, listOf(noRoleSnapshot()))
        Case(
            relative = paths[r.nextInt(paths.size)],
            states = walk,
            which = r.nextInt(walk.size),
            settings = RuleSettings(r.nextBoolean(), r.nextInt(4) != 0, r.nextBoolean()),
            vcs = VcsTracking.entries[r.nextInt(VcsTracking.entries.size)],
            closed = r.nextInt(4) == 0,
            files = if (r.nextBoolean()) ArchitectureModel.FILES_GIT_TRACKED else ArchitectureModel.FILES_WHOLE_TREE,
        )
    }

    // covers: 出る条件の4つ
    @Test
    fun `どんな入力でも出るのは当たらないファイルで設定が on で閉じておらず答えのある状態のときだけ`() = runBlocking<Unit> {
        checkAll(config(600), caseArb) { case ->
            val snapshot = noRoleSnapshot(case.files)
            val state = case.states[case.which]
            val roles = rolesFor(case.relative, snapshot, state.answer?.freshness ?: Freshness.Fresh)
            val file = DEFINITION_ROOT.resolve(case.relative)
            val memory = if (case.closed) EditorNotificationMemory.EMPTY.dismissNoRole(file) else EditorNotificationMemory.EMPTY

            val decision = decideNoRoleNotification(
                noRoleInput(case.relative, snapshot, file, state, roles, case.vcs, case.settings, memory),
            )

            val unexpected = roles.outcome == MatchOutcome.Unexpected || roles.outcome is MatchOutcome.UnexpectedUnder
            val expectShown = unexpected && case.settings.notifyOnNoRole && case.settings.notificationsEnabled && !case.closed && state.answer != null
            assertEquals("${case.relative} in $state", expectShown, decision is NoRoleDecision.NoRole)
            if (decision is NoRoleDecision.NoRole) {
                assertEquals(case.vcs == VcsTracking.Untracked && case.files == ArchitectureModel.FILES_GIT_TRACKED, decision.untracked)
                assertEquals((roles.outcome as? MatchOutcome.UnexpectedUnder)?.directory, decision.unexpectedUnder)
                assertEquals(roles.nearby.map { it.role.qualifiedName }, decision.nearby.map { it.roleName })
                assertEquals(case.relative, decision.relativePath)
            }
        }
    }

    private val templateArb: Arb<NotificationDecision> = arbitrary { rs ->
        val matches = listOf(placementMatch())
        when (rs.random.nextInt(5)) {
            0 -> NotificationDecision.Hidden
            1 -> NotificationDecision.CreateFromTemplate(matches)
            2 -> NotificationDecision.ViewTemplate(matches)
            3 -> NotificationDecision.Generating(if (rs.random.nextBoolean()) "cmd" else null)
            else -> NotificationDecision.FailedWithCommand("cmd")
        }
    }

    // covers: 両立の3段
    @Test
    fun `どの組でも勝者は1つで進行中の生成が最優先で何も出さない組のときだけ誰も勝たない`() = runBlocking<Unit> {
        val rule = decideNoRoleNotification(noRoleInput()) as NoRoleDecision.NoRole
        checkAll(config(300), templateArb, Arb.list(arbitrary { it.random.nextBoolean() }, 1..1)) { template, ruleShown ->
            val decision: NoRoleDecision = if (ruleShown.single()) rule else NoRoleDecision.Hidden
            val winner = arbitrateNotifications(template, decision)

            val generation = template is NotificationDecision.Generating || template is NotificationDecision.FailedWithCommand
            assertEquals("$template x $decision", NotificationWinner.Template == winner, generation || (!ruleShown.single() && template != NotificationDecision.Hidden))
            assertEquals(NotificationWinner.None == winner, !ruleShown.single() && template == NotificationDecision.Hidden)
            assertEquals(NotificationWinner.Rule == winner, ruleShown.single() && !generation)
        }
    }

    private val opArb: Arb<NoRoleOp> = arbitrary { rs ->
        val r = rs.random
        when (r.nextInt(100)) {
            in 0..14 -> NoRoleOp.Dismiss(r.nextInt(3))
            in 15..24 -> NoRoleOp.SetVcs(r.nextInt(3), VcsTracking.entries[r.nextInt(VcsTracking.entries.size)])
            in 25..32 -> NoRoleOp.Toggle(r.nextInt(2))
            in 33..92 -> NoRoleOp.Definition(r.nextInt(5))
            else -> NoRoleOp.NewSession
        }
    }

    // covers: 状態の列、操作の列
    @Test
    fun `定義の状態と利用者の操作がどんな順に並んでも各ファイルの通知はモデルの判定と一致する`() = runBlocking<Unit> {
        checkAll(config(500), Arb.list(opArb, 1..60)) { ops ->
            val machine = NoRoleMachine()
            ops.forEach { op ->
                try {
                    machine.apply(op)
                } catch (e: IllegalStateException) {
                    throw AssertionError("${e.message}\nsteps: ${machine.log.joinToString(" -> ")}", e)
                }
            }
        }
    }

    // covers: 点滅しない
    @Test
    fun `定義の状態だけが動く間は一度出た通知がどの状態を通っても消えない`() = runBlocking<Unit> {
        val stepArb: Arb<Int> = arbitrary { it.random.nextInt(5) }
        checkAll(config(500), Arb.list(stepArb, 1..60)) { steps ->
            val machine = NoRoleMachine()
            var shown = false
            steps.forEach { step ->
                machine.apply(NoRoleOp.Definition(step))
                // The first file is the one nobody holds under an unknown directory.
                if (shown) assertTrue("flicker after $step in ${machine.log}", machine.shows(0))
                shown = shown || machine.shows(0)
            }
        }
    }
}
